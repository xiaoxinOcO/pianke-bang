package com.campus.repository;

import com.campus.model.WalletAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
    Optional<WalletAccount> findByUsername(String username);

    @Query("SELECT w FROM WalletAccount w WHERE LOWER(w.username) = LOWER(:username)")
    List<WalletAccount> findAllByUsernameIgnoreCase(@Param("username") String username);
}
