package com.campus.support;

import com.campus.model.UserAccount;
import com.campus.model.WalletAccount;
import com.campus.model.WalletTransaction;
import com.campus.model.WalletWithdrawRequest;
import com.campus.repository.UserAccountRepository;
import com.campus.repository.WalletAccountRepository;
import com.campus.repository.WalletTransactionRepository;
import com.campus.repository.WalletWithdrawRequestRepository;
import com.campus.security.AuthHeaderCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 统一钱包账户解析与合并：管理员充值、支付宝充值、任务扣款共用同一 username 键。
 * <p>
 * 历史脏数据可能同时存在：
 * <ul>
 *   <li>规范用户名（如「测试接单员」）</li>
 *   <li>用户数字 ID 作键</li>
 *   <li>网关曾把中文用户名 Base64 后写入钱包键（看起来像两张表/两个钱包）</li>
 * </ul>
 */
@Component
public class WalletAccountSupport {

    private static final Logger log = LoggerFactory.getLogger(WalletAccountSupport.class);

    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectProvider<WalletWithdrawRequestRepository> withdrawRequestRepository;

    public WalletAccountSupport(
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            UserAccountRepository userAccountRepository,
            ObjectProvider<WalletWithdrawRequestRepository> withdrawRequestRepository
    ) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userAccountRepository = userAccountRepository;
        this.withdrawRequestRepository = withdrawRequestRepository;
    }

    public String resolveCanonicalUsername(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String trimmed = rawUsername.trim();

        // 若请求头解码失败、脏数据把 Base64 当作用户名，先尝试还原
        String maybeDecoded = AuthHeaderCodec.decodeUsername(trimmed, null);
        if (maybeDecoded != null && !maybeDecoded.isBlank() && !maybeDecoded.equals(trimmed)) {
            Optional<UserAccount> decodedUser = userAccountRepository.findByUsernameIgnoreCase(maybeDecoded.trim());
            if (decodedUser.isPresent()) {
                trimmed = decodedUser.get().getUsername().trim();
            }
        }

        Optional<UserAccount> byName = userAccountRepository.findByUsernameIgnoreCase(trimmed);
        if (byName.isPresent()) {
            return byName.get().getUsername().trim().toLowerCase(Locale.ROOT);
        }
        // 兼容历史脏数据：曾用用户数字 ID 当 wallet.username
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                Long id = Long.parseLong(trimmed);
                Optional<UserAccount> byId = userAccountRepository.findById(id);
                if (byId.isPresent()) {
                    return byId.get().getUsername().trim().toLowerCase(Locale.ROOT);
                }
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 同一用户在历史脏数据下可能用到的全部钱包键（规范名 / 原名 / 用户ID / Base64）。
     */
    public List<String> listUsernameAliases(String rawUsername) {
        String canonical = resolveCanonicalUsername(rawUsername);
        Set<String> keys = new LinkedHashSet<>();
        keys.add(canonical);
        if (rawUsername != null && !rawUsername.isBlank()) {
            keys.add(rawUsername.trim());
        }
        if (AuthHeaderCodec.needsBase64Encoding(canonical)) {
            keys.add(AuthHeaderCodec.encodeUsername(canonical));
            // 也收录「未转小写」的原始大小写 Base64（中文无影响，英文有）
            userAccountRepository.findByUsernameIgnoreCase(canonical).ifPresent(u -> {
                String original = u.getUsername() == null ? "" : u.getUsername().trim();
                if (!original.isBlank() && AuthHeaderCodec.needsBase64Encoding(original)) {
                    keys.add(AuthHeaderCodec.encodeUsername(original));
                }
            });
        }
        userAccountRepository.findByUsernameIgnoreCase(canonical).ifPresent(user ->
                keys.add(String.valueOf(user.getId()))
        );
        return new ArrayList<>(keys);
    }

    /**
     * 取得（或创建）规范钱包；若存在大小写/ID/Base64 键重复账户则合并余额与流水。
     */
    @Transactional
    public WalletAccount getOrCreateMerged(String rawUsername) {
        String canonical = resolveCanonicalUsername(rawUsername);
        List<WalletAccount> duplicates = collectDuplicateAccounts(canonical, rawUsername);
        if (duplicates.isEmpty()) {
            return createEmpty(canonical);
        }
        if (duplicates.size() == 1) {
            WalletAccount only = duplicates.get(0);
            if (!canonical.equals(only.getUsername())) {
                rekeyAccount(only, canonical);
            }
            rekeyRelatedRows(canonical, listUsernameAliases(rawUsername), rawUsername);
            return only;
        }
        return mergeAccounts(canonical, duplicates, rawUsername);
    }

    private List<WalletAccount> collectDuplicateAccounts(String canonical, String rawUsername) {
        Map<Long, WalletAccount> byId = new LinkedHashMap<>();
        for (String key : listUsernameAliases(rawUsername == null || rawUsername.isBlank() ? canonical : rawUsername)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            walletAccountRepository.findByUsername(key).ifPresent(w -> byId.put(w.getId(), w));
            for (WalletAccount w : walletAccountRepository.findAllByUsernameIgnoreCase(key)) {
                byId.put(w.getId(), w);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private WalletAccount mergeAccounts(String canonical, List<WalletAccount> duplicates, String rawUsername) {
        duplicates.sort((a, b) -> {
            boolean aCanon = canonical.equals(a.getUsername());
            boolean bCanon = canonical.equals(b.getUsername());
            if (aCanon != bCanon) {
                return aCanon ? -1 : 1;
            }
            return Long.compare(a.getId(), b.getId());
        });
        WalletAccount primary = duplicates.get(0);
        BigDecimal balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal income = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawn = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawing = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<String> oldNames = new ArrayList<>();
        for (WalletAccount w : duplicates) {
            oldNames.add(w.getUsername());
            balance = balance.add(nz(w.getBalance()));
            income = income.add(nz(w.getTotalIncome()));
            withdrawn = withdrawn.add(nz(w.getTotalWithdrawn()));
            withdrawing = withdrawing.add(nz(w.getWithdrawingAmount()));
        }
        primary.setUsername(canonical);
        primary.setBalance(balance);
        primary.setTotalIncome(income);
        primary.setTotalWithdrawn(withdrawn);
        primary.setWithdrawingAmount(withdrawing);
        walletAccountRepository.save(primary);

        for (int i = 1; i < duplicates.size(); i++) {
            walletAccountRepository.delete(duplicates.get(i));
        }
        List<String> aliasNames = new ArrayList<>(oldNames);
        aliasNames.addAll(listUsernameAliases(rawUsername));
        rekeyRelatedRows(canonical, aliasNames, rawUsername);
        log.warn("Merged {} wallet accounts into username={}", duplicates.size(), canonical);
        return primary;
    }

    private void rekeyAccount(WalletAccount account, String canonical) {
        String old = account.getUsername();
        account.setUsername(canonical);
        walletAccountRepository.save(account);
        rekeyRelatedRows(canonical, List.of(old), canonical);
    }

    private void rekeyRelatedRows(String canonical, List<String> oldNames, String rawUsername) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String n : oldNames) {
            if (n != null && !n.isBlank()) {
                names.add(n);
            }
        }
        if (rawUsername != null && !rawUsername.isBlank()) {
            names.add(rawUsername.trim());
        }
        names.addAll(listUsernameAliases(canonical));

        WalletWithdrawRequestRepository withdrawRepo = withdrawRequestRepository.getIfAvailable();
        for (String name : names) {
            if (name == null || name.isBlank() || canonical.equals(name)) {
                continue;
            }
            List<WalletTransaction> txs = walletTransactionRepository.findAllByUsername(name);
            for (WalletTransaction t : txs) {
                if (!canonical.equals(t.getUsername())) {
                    t.setUsername(canonical);
                    walletTransactionRepository.save(t);
                }
            }
            for (WalletTransaction t : walletTransactionRepository.findAllByUsernameIgnoreCase(name)) {
                if (!canonical.equals(t.getUsername())) {
                    t.setUsername(canonical);
                    walletTransactionRepository.save(t);
                }
            }
            if (withdrawRepo != null) {
                for (WalletWithdrawRequest wr : withdrawRepo.findAllByUsername(name)) {
                    if (!canonical.equals(wr.getUsername())) {
                        wr.setUsername(canonical);
                        withdrawRepo.save(wr);
                    }
                }
                for (WalletWithdrawRequest wr : withdrawRepo.findAllByUsernameIgnoreCase(name)) {
                    if (!canonical.equals(wr.getUsername())) {
                        wr.setUsername(canonical);
                        withdrawRepo.save(wr);
                    }
                }
            }
        }
    }

    private WalletAccount createEmpty(String canonical) {
        WalletAccount w = new WalletAccount();
        w.setUsername(canonical);
        w.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        w.setTotalIncome(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        w.setTotalWithdrawn(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        w.setWithdrawingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return walletAccountRepository.save(w);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v;
    }
}
