package ca.admin.delivermore.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "tablet_asset_history",
        indexes = {
                @Index(name = "idx_tablet_asset_history_asset", columnList = "tabletAssetId"),
                @Index(name = "idx_tablet_asset_history_changedAt", columnList = "changedAt")
        })
public class TabletAssetHistory {

    public enum ChangeType {
        CREATE,
        UPDATE,
        ARCHIVE,
        UNARCHIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    // explicit FK column for easy querying
    @NotNull
    @Column(nullable = false)
    private Long tabletAssetId;

    // optional relation (read-only convenience)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tabletAssetId", insertable = false, updatable = false)
    private TabletAsset tabletAsset;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeType changeType;

    @NotNull
    @Column(nullable = false)
    private Instant changedAt;

    @NotNull
    @Column(nullable = false)
    private String changedBy = "";

    // snapshot of the asset at time of change
    @NotNull
    @Column(nullable = false)
    private String assetName = "";

    @NotNull
    @Column(nullable = false)
    private String assetTag = "";

    private Long assignedFleetId;

    @NotNull
    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName = "";

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(nullable = false)
    private boolean archived;

    @Column(length = 1000)
    private String notes;

    public TabletAssetHistory() {}

    public Long getId() {
        return id;
    }

    public Long getTabletAssetId() {
        return tabletAssetId;
    }

    public void setTabletAssetId(Long tabletAssetId) {
        this.tabletAssetId = tabletAssetId;
    }

    public TabletAsset getTabletAsset() {
        return tabletAsset;
    }

    // compatibility with service style: set the relation and also set tabletAssetId
    public void setTabletAsset(TabletAsset tabletAsset) {
        this.tabletAsset = tabletAsset;
        if (tabletAsset != null) {
            this.tabletAssetId = tabletAsset.getId();
        }
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy == null ? "" : changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy == null ? "" : changedBy;
    }

    public String getAssetName() {
        return assetName == null ? "" : assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName == null ? "" : assetName;
    }

    public String getAssetTag() {
        return assetTag == null ? "" : assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag == null ? "" : assetTag;
    }

    public Long getAssignedFleetId() {
        return assignedFleetId;
    }

    public void setAssignedFleetId(Long assignedFleetId) {
        this.assignedFleetId = assignedFleetId;
    }

    public String getRestaurantName() {
        return restaurantName == null ? "" : restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName == null ? "" : restaurantName;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}