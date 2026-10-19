package com.campus.dto;

public class AdminAccountRoleRequest {
    /** USER / ADMIN / CUSTOMER_SERVICE */
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
