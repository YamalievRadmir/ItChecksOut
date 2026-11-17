package ru.sklad.app.entity;

import jakarta.persistence.*;

@Entity
public class RoleSettings {
    public static final String DEFAULT_ACTIONS = "canEditProduct,canGiveDiscount,canMakeSupply,canSeeReports,canSeeRetailPrice";
    @Id
    @Column(nullable = false, unique = true)
    private String roleName;
    @Column(nullable = false, length = 2000)
    private String allowedViews = "";
    @Column(length = 1000)
    private String allowedActions = DEFAULT_ACTIONS;
    private Boolean inventoryAccessInitialized = false;


    @Enumerated(EnumType.STRING) private AccessLevel productsAccess;
    public AccessLevel getProductsAccess() { return productsAccess; }
    public void setProductsAccess(AccessLevel value) { productsAccess = value; }

    @Enumerated(EnumType.STRING) private AccessLevel suppliesAccess;
    public AccessLevel getSuppliesAccess() { return suppliesAccess; }
    public void setSuppliesAccess(AccessLevel value) { suppliesAccess = value; }

    @Enumerated(EnumType.STRING) private AccessLevel deliveriesAccess;
    public AccessLevel getDeliveriesAccess() { return deliveriesAccess; }
    public void setDeliveriesAccess(AccessLevel value) { deliveriesAccess = value; }

    @Enumerated(EnumType.STRING) private AccessLevel notesAccess;
    public AccessLevel getNotesAccess() { return notesAccess; }
    public void setNotesAccess(AccessLevel value) { notesAccess = value; }

    @Enumerated(EnumType.STRING) private AccessLevel animalsAccess;
    public AccessLevel getAnimalsAccess() { return animalsAccess; }
    public void setAnimalsAccess(AccessLevel value) { animalsAccess = value; }

    @Enumerated(EnumType.STRING) private AccessLevel salesAccess;
    public AccessLevel getSalesAccess() { return salesAccess; }
    public void setSalesAccess(AccessLevel value) { salesAccess = value; }

    private Boolean inventoryAccess;
    public Boolean getInventoryAccess() { return inventoryAccess; }
    public void setInventoryAccess(Boolean value) { inventoryAccess = value; }

    private Boolean reportsAccess;
    public Boolean getReportsAccess() { return reportsAccess; }
    public void setReportsAccess(Boolean value) { reportsAccess = value; }

    private Boolean historyAccess;
    public Boolean getHistoryAccess() { return historyAccess; }
    public void setHistoryAccess(Boolean value) { historyAccess = value; }

    private Boolean employeesAccess;
    public Boolean getEmployeesAccess() { return employeesAccess; }
    public void setEmployeesAccess(Boolean value) { employeesAccess = value; }

    private Boolean canGiveDiscount;
    public Boolean getCanGiveDiscount() { return canGiveDiscount; }
    public void setCanGiveDiscount(Boolean value) { canGiveDiscount = value; }

    public void migrateAccessLevels() {
        var views = new java.util.HashSet<>(java.util.Arrays.asList(java.util.Objects.toString(getAllowedViews(), "").split(",")));
        var actions = java.util.Arrays.asList(getAllowedActions().split(","));
        if (productsAccess == null) productsAccess = !views.contains("products") ? AccessLevel.NONE : actions.contains("canEditProduct") || actions.contains("canCreateProduct") ? AccessLevel.EDIT : AccessLevel.VIEW;
        if (suppliesAccess == null) suppliesAccess = !views.contains("supplies") ? AccessLevel.NONE : actions.contains("canMakeSupply") ? AccessLevel.EDIT : AccessLevel.VIEW;
        if (deliveriesAccess == null) deliveriesAccess = views.contains("deliveries") ? AccessLevel.EDIT : AccessLevel.NONE;
        if (notesAccess == null) notesAccess = views.contains("notes") ? AccessLevel.EDIT : AccessLevel.NONE;
        if (salesAccess == null) salesAccess = views.contains("sales") ? AccessLevel.EDIT : AccessLevel.NONE;
        if (animalsAccess == null) animalsAccess = views.contains("animals") ? AccessLevel.VIEW : AccessLevel.NONE;
        if (inventoryAccess == null) inventoryAccess = views.contains("inventory");
        if (reportsAccess == null) reportsAccess = views.contains("reports") && actions.contains("canSeeReports");
        if (historyAccess == null) historyAccess = views.contains("sales-history");
        if (employeesAccess == null) employeesAccess = views.contains("employees");
        if (canGiveDiscount == null) canGiveDiscount = actions.contains("canGiveDiscount");
    }
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
