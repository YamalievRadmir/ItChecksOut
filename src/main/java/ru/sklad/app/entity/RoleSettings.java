package ru.sklad.app.entity;

import jakarta.persistence.*;

@Entity
public class RoleSettings {
    public static final String DEFAULT_ACTIONS = "canEditProduct,canGiveDiscount,canMakeSupply,canSeeReports";
    @Id
    @Column(nullable = false, unique = true)
    private String roleName;
    @Column(nullable = false, length = 2000)
    private String allowedViews = "";
    @Column(length = 1000)
    private String allowedActions = DEFAULT_ACTIONS;
    private Boolean inventoryAccessInitialized = false;

    public RoleSettings() {}
    public RoleSettings(String roleName, String allowedViews) {
        this.roleName = roleName;
        this.allowedViews = allowedViews;
    }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getAllowedViews() { return allowedViews; }
    public void setAllowedViews(String allowedViews) { this.allowedViews = allowedViews; }
    public String getAllowedActions() { return allowedActions == null ? DEFAULT_ACTIONS : allowedActions; }
    public void setAllowedActions(String allowedActions) { this.allowedActions = allowedActions; }
    public Boolean getInventoryAccessInitialized() { return inventoryAccessInitialized; }
    public void setInventoryAccessInitialized(Boolean value) { inventoryAccessInitialized = value; }
}
