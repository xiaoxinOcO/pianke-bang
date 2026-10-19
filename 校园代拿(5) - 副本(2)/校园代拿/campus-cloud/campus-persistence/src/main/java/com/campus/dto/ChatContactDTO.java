package com.campus.dto;

public class ChatContactDTO {
    private String username;
    private String avatarUrl;
    private boolean riderApproved;
    private boolean customerServiceStaff;

    public ChatContactDTO(String username, String avatarUrl, boolean riderApproved) {
        this(username, avatarUrl, riderApproved, false);
    }

    public ChatContactDTO(String username, String avatarUrl, boolean riderApproved, boolean customerServiceStaff) {
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.riderApproved = riderApproved;
        this.customerServiceStaff = customerServiceStaff;
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isRiderApproved() {
        return riderApproved;
    }

    public boolean isCustomerServiceStaff() {
        return customerServiceStaff;
    }
}
