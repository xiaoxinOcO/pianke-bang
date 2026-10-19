package com.campus.security;

import com.campus.config.AdminProperties;
import com.campus.model.AccountRole;
import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Locale;

/**
 * 启动时把配置中的管理员用户名同步为库内 {@link AccountRole#ADMIN}（幂等提升，不降级）。
 * 配置仅作种子 / 兜底，日常以数据库角色为准。
 */
public class AdminRoleSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleSeedRunner.class);

    private final UserAccountRepository userAccountRepository;
    private final AdminProperties adminProperties;

    public AdminRoleSeedRunner(UserAccountRepository userAccountRepository, AdminProperties adminProperties) {
        this.userAccountRepository = userAccountRepository;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String csv = adminProperties.getUsernames();
        if (csv == null || csv.isBlank()) {
            return;
        }
        int promoted = 0;
        int missing = 0;
        for (String raw : csv.split(",")) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            UserAccount user = userAccountRepository.findByUsernameIgnoreCase(name).orElse(null);
            if (user == null) {
                missing++;
                log.warn("管理员种子用户不存在，请先注册后再写入角色: {}", name.toLowerCase(Locale.ROOT));
                continue;
            }
            if (user.isPlatformAdmin() && !user.isCustomerServiceStaff()) {
                continue;
            }
            user.applyAccountRole(AccountRole.ADMIN);
            userAccountRepository.save(user);
            promoted++;
            log.info("已将用户提升为平台管理员: {}", user.getUsername());
        }
        if (promoted > 0 || missing > 0) {
            log.info("管理员角色种子完成：提升 {} 人，缺失账号 {} 个", promoted, missing);
        }
    }
}
