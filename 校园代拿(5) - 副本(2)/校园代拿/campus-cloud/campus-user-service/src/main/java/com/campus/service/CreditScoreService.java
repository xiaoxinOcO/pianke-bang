package com.campus.service;

import com.campus.model.UserAccount;
import com.campus.repository.UserAccountRepository;
import com.campus.rules.CreditRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CreditScoreService {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreService.class);

    private final UserAccountRepository userAccountRepository;

    public CreditScoreService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 每天自动恢复信誉分：每个用户最多 +{@link CreditRules#RECOVERY_DAILY_POINTS}，封顶 {@link CreditRules#MAX}。
     */
    @Scheduled(cron = "0 10 2 * * *")
    public void recoverCreditScoreDaily() {
        List<UserAccount> users = userAccountRepository.findAll();
        int updated = 0;
        for (UserAccount user : users) {
            int current = CreditRules.effectiveScore(user.getCreditScore());
            if (current >= CreditRules.MAX) {
                continue;
            }
            int next = Math.min(CreditRules.MAX, current + CreditRules.RECOVERY_DAILY_POINTS);
            user.setCreditScore(next);
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isBefore(LocalDateTime.now())) {
                user.setBlockedUntil(null);
            }
            userAccountRepository.save(user);
            updated++;
        }
        if (updated > 0) {
            log.info("信誉分日常恢复完成，更新用户数={}", updated);
        }
    }
}
