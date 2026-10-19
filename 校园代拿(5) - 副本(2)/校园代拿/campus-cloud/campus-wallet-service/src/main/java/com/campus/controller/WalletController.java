package com.campus.controller;

import com.campus.dto.WalletAuditRequest;
import com.campus.dto.WalletWithdrawApplyRequest;
import com.campus.model.WalletWithdrawRequest;
import com.campus.security.AuthContext;
import com.campus.service.WalletService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    public Map<String, Object> myWallet() {
        return walletService.snapshot(AuthContext.getCurrentUser());
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuildMyWallet() {
        return walletService.forceRebuild(AuthContext.getCurrentUser());
    }

    @PostMapping("/withdraw")
    public WalletWithdrawRequest applyWithdraw(@RequestBody WalletWithdrawApplyRequest request) {
        return walletService.applyWithdraw(AuthContext.getCurrentUser(), request);
    }

    @GetMapping("/admin/withdraw/pending")
    public List<WalletWithdrawRequest> listPending() {
        return walletService.listPendingForAdmin(AuthContext.getCurrentUser());
    }

    @PostMapping("/admin/withdraw/{id}/audit")
    public WalletWithdrawRequest audit(@PathVariable("id") Long id, @RequestBody WalletAuditRequest request) {
        return walletService.auditWithdraw(id, request, AuthContext.getCurrentUser());
    }
}
