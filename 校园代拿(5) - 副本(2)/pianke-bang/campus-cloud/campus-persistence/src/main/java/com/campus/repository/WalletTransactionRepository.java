package com.campus.repository;

import com.campus.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findTop50ByUsernameOrderByCreatedAtDesc(String username);
    List<WalletTransaction> findAllByUsername(String username);

    @Query("SELECT t FROM WalletTransaction t WHERE LOWER(t.username) = LOWER(:username)")
    List<WalletTransaction> findAllByUsernameIgnoreCase(@Param("username") String username);

    boolean existsByTaskIdAndUsername(Long taskId, String username);
    boolean existsByTaskIdAndUsernameAndType(Long taskId, String username, com.campus.model.WalletTransactionType type);
    boolean existsByUsernameAndRemark(String username, String remark);
}
