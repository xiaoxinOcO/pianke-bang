package com.campus.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 使用账号主键 ID（数字）或注册手机号（11 位）登录；兼容旧客户端字段名 username。
 */
public class LoginRequest {
    @JsonAlias("username")
    private String account;
    private String password;
    /** 客服工作台登录时为 true；主站拒绝客服账号登录 */
    private boolean csConsole;

    /** web | mp | rider，默认 web；接单员台须传 rider，小程序须传 mp */
    private String clientChannel;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isCsConsole() {
        return csConsole;
    }

    public void setCsConsole(boolean csConsole) {
        this.csConsole = csConsole;
    }

    public String getClientChannel() {
        return clientChannel;
    }

    public void setClientChannel(String clientChannel) {
        this.clientChannel = clientChannel;
    }
}
