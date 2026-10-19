package com.campus.repository;

import com.campus.model.UserSystemMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSystemMessageRepository extends JpaRepository<UserSystemMessage, Long> {

    List<UserSystemMessage> findTop100ByUsernameIgnoreCaseOrderByCreatedAtDescIdDesc(String username);

    long countByUsernameIgnoreCaseAndReadFlagFalse(String username);

    boolean existsByUsernameIgnoreCaseAndEventTypeAndRefId(String username, String eventType, Long refId);

    @Modifying
    @Query("update UserSystemMessage m set m.readFlag = true where lower(m.username) = lower(:username) and m.readFlag = false")
    int markAllRead(@Param("username") String username);
}
