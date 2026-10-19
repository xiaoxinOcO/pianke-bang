package com.campus.dto;

public class WeChatMpConfirmPickRequest {
    private String pickToken;
    private Long userId;
    private String wxMpOpenId;
    private String wxUnionId;

    public String getPickToken() {
        return pickToken;
    }

    public void setPickToken(String pickToken) {
        this.pickToken = pickToken;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getWxMpOpenId() {
        return wxMpOpenId;
    }

    public void setWxMpOpenId(String wxMpOpenId) {
        this.wxMpOpenId = wxMpOpenId;
    }

    public String getWxUnionId() {
        return wxUnionId;
    }

    public void setWxUnionId(String wxUnionId) {
        this.wxUnionId = wxUnionId;
    }
}
