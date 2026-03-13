package ca.admin.delivermore.data.entity;

import ca.admin.delivermore.collector.data.tookan.Driver;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "tablet_asset")
public class TabletAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String assetName = "";

    @NotBlank
    @Column(nullable = false, unique = true)
    private String assetTag = "";

    // assignment: FK + read-only relationship (same pattern as DriverAdjustment)
    private Long assignedFleetId;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignedFleetId", insertable = false, updatable = false)
    private Driver assignedDriver;

    // optional restaurant/location: empty string means "none"
    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName = "";

    // optional, set only when chosen from effective restaurant list
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(nullable = false)
    private boolean archived = false;

    // audit (service sets)
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String createdBy = "";

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String updatedBy = "";

    @Version
    private Long version;
    
    @Column(length = 1000, nullable = false)
    private String notes = "";

    public TabletAsset() {}

    public Long getId() { return id; }

    // optional: only needed if you clone in UI. In the updated view below we DO NOT need setId().
    public void setId(Long id) { this.id = id; }

    public String getAssetName() { return assetName == null ? "" : assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName == null ? "" : assetName; }

    public String getAssetTag() { return assetTag == null ? "" : assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag == null ? "" : assetTag; }

    public Long getAssignedFleetId() { return assignedFleetId; }
    public void setAssignedFleetId(Long assignedFleetId) { this.assignedFleetId = assignedFleetId; }

    public Driver getAssignedDriver() { return assignedDriver; }
    public void setAssignedDriver(Driver assignedDriver) { this.assignedDriver = assignedDriver; }

    public String getRestaurantName() { return restaurantName == null ? "" : restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName == null ? "" : restaurantName; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy == null ? "" : createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy == null ? "" : createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy == null ? "" : updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy == null ? "" : updatedBy; }

    public String getNotes() {
        return notes == null ? "" : notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }    
    
    @Override
    public String toString() {
        return "TabletAsset{" +
                "id=" + id +
                ", assetName='" + assetName + '\'' +
                ", assetTag='" + assetTag + '\'' +
                ", assignedFleetId=" + assignedFleetId +
                ", restaurantName='" + restaurantName + '\'' +
                ", restaurantId=" + restaurantId +
                ", archived=" + archived +
                ", createdAt=" + createdAt +
                ", createdBy='" + createdBy + '\'' +
                ", updatedAt=" + updatedAt +
                ", updatedBy='" + updatedBy + '\'' +
                '}';
    }
}