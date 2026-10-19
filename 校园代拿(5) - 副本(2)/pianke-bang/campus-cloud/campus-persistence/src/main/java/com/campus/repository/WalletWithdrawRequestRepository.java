package com.campus.repository;

import com.campus.model.WalletWithdrawRequest;
import com.campus.model.WithdrawStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletWithdrawRequestRepository extends JpaRepository<WalletWithdrawRequest, Long> {
    List<WalletWithdrawRequest> findTop30ByUsernameOrderByCreatedAtDesc(String username);
    List<WalletWithdrawRequest> findAllByUsername(String username);
    List<WalletWithdrawRequest> findAllByUsernameIgnoreCase(String username);
    List<WalletWithdrawRequest> findTop100ByStatusOrderByCreatedAtAsc(WithdrawStatus status);
}
