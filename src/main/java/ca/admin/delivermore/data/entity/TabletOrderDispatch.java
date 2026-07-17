package ca.admin.delivermore.data.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "tablet_order_dispatch")
public class TabletOrderDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staged_order_id", nullable = false, unique = true)
    private Long stagedOrderId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName = "";

    @Column(name = "tablet_asset_id")
    private Long tabletAssetId;

    @Column(name = "tablet_asset_tag")
    private String tabletAssetTag = "";

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "push_sent_at")
    private LocalDateTime pushSentAt;

    @Column(name = "push_failed_at")
    private LocalDateTime pushFailedAt;

    @Column(name = "payload_pulled_at")
    private LocalDateTime payloadPulledAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Lob
    @Column(name = "last_failure_reason", columnDefinition = "LONGTEXT")
    private String lastFailureReason;

    @Column(name = "support_email_notified_at")
    private LocalDateTime supportEmailNotifiedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStagedOrderId() {
        return stagedOrderId;
    }

    public void setStagedOrderId(Long stagedOrderId) {
        this.stagedOrderId = stagedOrderId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName == null ? "" : restaurantName;
    }

    public Long getTabletAssetId() {
        return tabletAssetId;
    }

    public void setTabletAssetId(Long tabletAssetId) {
        this.tabletAssetId = tabletAssetId;
    }

    public String getTabletAssetTag() {
        return tabletAssetTag;
    }

    public void setTabletAssetTag(String tabletAssetTag) {
        this.tabletAssetTag = tabletAssetTag == null ? "" : tabletAssetTag;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public void setLastAttemptedAt(LocalDateTime lastAttemptedAt) {
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public LocalDateTime getPushSentAt() {
        return pushSentAt;
    }

    public void setPushSentAt(LocalDateTime pushSentAt) {
        this.pushSentAt = pushSentAt;
    }

    public LocalDateTime getPushFailedAt() {
        return pushFailedAt;
    }

    public void setPushFailedAt(LocalDateTime pushFailedAt) {
        this.pushFailedAt = pushFailedAt;
    }

    public LocalDateTime getPayloadPulledAt() {
        return payloadPulledAt;
    }

    public void setPayloadPulledAt(LocalDateTime payloadPulledAt) {
        this.payloadPulledAt = payloadPulledAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = Math.max(0, failureCount);
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public void setLastFailureReason(String lastFailureReason) {
        this.lastFailureReason = lastFailureReason;
    }

    public LocalDateTime getSupportEmailNotifiedAt() {
        return supportEmailNotifiedAt;
    }

    public void setSupportEmailNotifiedAt(LocalDateTime supportEmailNotifiedAt) {
        this.supportEmailNotifiedAt = supportEmailNotifiedAt;
    }
}