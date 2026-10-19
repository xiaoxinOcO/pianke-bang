package com.campus.mq;

public class WalletLockRpcResponse {

    private boolean success;
    private String errorMessage;

    public WalletLockRpcResponse() {
    }

    public WalletLockRpcResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static WalletLockRpcResponse ok() {
        return new WalletLockRpcResponse(true, null);
    }

    public static WalletLockRpcResponse fail(String errorMessage) {
        return new WalletLockRpcResponse(false, errorMessage == null ? "未知错误" : errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
