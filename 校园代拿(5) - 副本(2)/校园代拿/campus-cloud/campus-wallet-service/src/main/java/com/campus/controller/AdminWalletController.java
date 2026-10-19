package com.campus.controller;

import com.campus.dto.AdminWalletCreditRequest;
import com.campus.security.AuthContext;
import com.campus.service.WalletService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminWalletController {

    private final WalletService walletService;

    public AdminWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/users/{id}/wallet/credit")
    public Map<String, Object> creditUserWallet(@PathVariable("id") Long id, @RequestBody AdminWalletCreditRequest request) {
        return walletService.adminCreditUserWallet(AuthContext.getCurrentUser(), id, request);
    }
}
