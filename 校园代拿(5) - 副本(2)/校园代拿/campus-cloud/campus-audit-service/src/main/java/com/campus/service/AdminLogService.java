package com.campus.service;

import com.campus.dto.AdminLogPageResponse;
import com.campus.model.AdminOperationLog;
import com.campus.repository.AdminOperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminLogService {

    private final AdminOperationLogRepository adminOperationLogRepository;

    public AdminLogService(AdminOperationLogRepository adminOperationLogRepository) {
        this.adminOperationLogRepository = adminOperationLogRepository;
    }

    public void log(String operatorName, String actionType, Long targetTaskId, String detail) {
        AdminOperationLog log = new AdminOperationLog();
        log.setOperatorName(operatorName);
        log.setActionType(actionType);
        log.setTargetTaskId(targetTaskId);
        log.setDetail(detail);
        adminOperationLogRepository.save(log);
    }

    public AdminLogPageResponse listLogs(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminOperationLog> result = adminOperationLogRepository.findAll(pageable);
        return new AdminLogPageResponse(
                result.getContent(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
