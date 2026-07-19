package ca.admin.delivermore.data.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.admin.delivermore.collector.data.entity.SettingEntity;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder.ApprovalStatus;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.SettingRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.entity.TabletOrderDispatch;

@Service
public class TabletOrderMonitoringService {

    private static final String SETTINGS_SECTION = "tablet_monitoring";
    private static final String WARNING_MINUTES_SETTING = "pending_warning_minutes";
    private static final String CRITICAL_MINUTES_SETTING = "pending_critical_minutes";
    private static final String HEARTBEAT_STALE_MINUTES_SETTING = "heartbeat_stale_minutes";
    private static final String ENGAGEMENT_STALE_MINUTES_SETTING = "engagement_stale_minutes";

    private static final int DEFAULT_WARNING_MINUTES = 3;
    private static final int DEFAULT_CRITICAL_MINUTES = 6;
    private static final int DEFAULT_HEARTBEAT_STALE_MINUTES = 5;
    private static final int DEFAULT_ENGAGEMENT_STALE_MINUTES = 3;

    private final SettingRepository settingRepository;
    private final StagedRestaurantOrderRepository stagedRestaurantOrderRepository;
    private final RestaurantRepository restaurantRepository;
    private final TabletAssetRepository tabletAssetRepository;
    private final TabletOrderDispatchRepository tabletOrderDispatchRepository;
    private final TabletOrderDispatchService tabletOrderDispatchService;

    public TabletOrderMonitoringService(
            SettingRepository settingRepository,
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            RestaurantRepository restaurantRepository,
            TabletAssetRepository tabletAssetRepository,
            TabletOrderDispatchRepository tabletOrderDispatchRepository,
            TabletOrderDispatchService tabletOrderDispatchService) {
        this.settingRepository = settingRepository;
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.restaurantRepository = restaurantRepository;
        this.tabletAssetRepository = tabletAssetRepository;
        this.tabletOrderDispatchRepository = tabletOrderDispatchRepository;
        this.tabletOrderDispatchService = tabletOrderDispatchService;
    }

    @Transactional(readOnly = true)
    public TabletMonitoringSnapshot loadSnapshot() {
        MonitoringThresholds thresholds = getThresholds();
        LocalDateTime now = LocalDateTime.now();

        List<StagedRestaurantOrder> pendingOrders = stagedRestaurantOrderRepository
                .findByApprovalStatusOrderBySubmittedAtDesc(ApprovalStatus.PENDING_APPROVAL);
        List<TabletAsset> activeAssets = tabletAssetRepository.findByArchivedFalseOrderByAssetNameAsc();

        Map<Long, TabletAsset> assetsByRestaurantId = activeAssets.stream()
                .filter(asset -> asset.getRestaurantId() != null)
                .collect(Collectors.toMap(
                        TabletAsset::getRestaurantId,
                        asset -> asset,
                        (first, second) -> first,
                        HashMap::new));

        List<Long> stagedOrderIds = pendingOrders.stream()
                .map(StagedRestaurantOrder::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, TabletOrderDispatch> dispatchByOrderId = stagedOrderIds.isEmpty()
                ? Map.of()
                : tabletOrderDispatchRepository.findByStagedOrderIdIn(stagedOrderIds).stream()
                        .collect(Collectors.toMap(TabletOrderDispatch::getStagedOrderId, dispatch -> dispatch));

        List<PendingOrderAlert> orderAlerts = new ArrayList<>();
        Map<Long, RestaurantAggregate> aggregatesByRestaurant = new HashMap<>();

        for (StagedRestaurantOrder pendingOrder : pendingOrders) {
            PendingOrderAlert alert = buildAlert(now, thresholds, pendingOrder,
                    assetsByRestaurantId.get(pendingOrder.getRestaurantId()),
                    dispatchByOrderId.get(pendingOrder.getId()));
            orderAlerts.add(alert);

            RestaurantAggregate aggregate = aggregatesByRestaurant
                    .computeIfAbsent(pendingOrder.getRestaurantId(),
                            key -> new RestaurantAggregate(pendingOrder.getRestaurantId(), pendingOrder.getRestaurantName()));
            aggregate.add(alert);
        }

        List<RestaurantAlert> restaurantAlerts = aggregatesByRestaurant.values().stream()
                .map(RestaurantAggregate::toRestaurantAlert)
                .sorted(Comparator
                        .comparing(RestaurantAlert::severity).reversed()
                        .thenComparing(RestaurantAlert::oldestPendingMinutes, Comparator.reverseOrder())
                        .thenComparing(RestaurantAlert::restaurantName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PendingOrderAlert> sortedOrders = orderAlerts.stream()
                .sorted(Comparator
                        .comparing(PendingOrderAlert::severity).reversed()
                        .thenComparing(PendingOrderAlert::pendingMinutes, Comparator.reverseOrder())
                        .thenComparing(PendingOrderAlert::restaurantName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PendingOrderAlert::stagedOrderId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Map<Long, Integer> pendingCountByRestaurant = new HashMap<>();
        for (PendingOrderAlert alert : sortedOrders) {
            Long restaurantId = alert.restaurantId();
            if (restaurantId != null) {
                pendingCountByRestaurant.merge(restaurantId, 1, Integer::sum);
            }
        }

        List<TabletAssetAlert> tabletAlerts = activeAssets.stream()
                .map(asset -> buildTabletAssetAlert(now, thresholds, asset, pendingCountByRestaurant))
                .sorted(Comparator
                        .comparing(TabletAssetAlert::health).reversed()
                        .thenComparing(TabletAssetAlert::pendingCount, Comparator.reverseOrder())
                        .thenComparing(TabletAssetAlert::restaurantName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TabletAssetAlert::assetTag, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new TabletMonitoringSnapshot(thresholds, restaurantAlerts, sortedOrders, tabletAlerts, now);
    }

    private TabletAssetAlert buildTabletAssetAlert(
            LocalDateTime now,
            MonitoringThresholds thresholds,
            TabletAsset asset,
            Map<Long, Integer> pendingCountByRestaurant) {
        Long heartbeatMinutes = nullableMinutesBetween(asset.getLastHeartbeatAt(), now);
        int pendingCount = asset.getRestaurantId() == null
                ? 0
                : pendingCountByRestaurant.getOrDefault(asset.getRestaurantId(), 0);

        boolean configured = asset.getRestaurantId() != null
                && asset.getProvisionedAt() != null
                && asset.getLastHeartbeatAt() != null
                && asset.getFcmRegistrationToken() != null
                && !asset.getFcmRegistrationToken().isBlank();

        TabletHealth health;
        String action;

        if (!configured) {
            health = TabletHealth.NOT_CONFIGURED;
            action = "Complete provisioning (QR claim + token registration + heartbeat).";
        } else if (asset.getRestaurantId() == null || asset.getRestaurantName().isBlank()) {
            health = TabletHealth.UNASSIGNED;
            action = "Assign this tablet to a restaurant for monitoring and push routing.";
        } else if (heartbeatMinutes == null) {
            health = TabletHealth.NO_HEARTBEAT;
            action = "Open the app on the tablet to start heartbeat updates.";
        } else if (heartbeatMinutes >= thresholds.heartbeatStaleMinutes()) {
            health = TabletHealth.OFFLINE_SUSPECTED;
            action = "Check tablet power/network at the restaurant.";
        } else if (pendingCount > 0) {
            health = TabletHealth.ACTIVE_WITH_PENDING;
            action = "Tablet is online; monitor pending approvals and follow up if aging.";
        } else {
            health = TabletHealth.HEALTHY;
            action = "No immediate action needed.";
        }

        return new TabletAssetAlert(
                asset.getId(),
                asset.getAssetTag(),
                asset.getAssetName(),
                asset.getRestaurantName(),
                asset.getRestaurantId(),
                heartbeatMinutes,
                asset.getLastHeartbeatAppVersion(),
                pendingCount,
                configured,
                health,
                action);
    }

    @Transactional
    public MonitoringThresholds saveThresholds(MonitoringThresholds incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Threshold values are required");
        }

        int warningMinutes = clampMinutes(incoming.warningMinutes(), 1, 240);
        int criticalMinutes = clampMinutes(incoming.criticalMinutes(), warningMinutes + 1, 480);
        int heartbeatStaleMinutes = clampMinutes(incoming.heartbeatStaleMinutes(), 1, 240);
        int engagementStaleMinutes = clampMinutes(incoming.engagementStaleMinutes(), 1, 240);

        saveIntegerSetting(WARNING_MINUTES_SETTING, "Warning threshold for pending orders", warningMinutes);
        saveIntegerSetting(CRITICAL_MINUTES_SETTING, "Critical threshold for pending orders", criticalMinutes);
        saveIntegerSetting(HEARTBEAT_STALE_MINUTES_SETTING, "Heartbeat stale threshold", heartbeatStaleMinutes);
        saveIntegerSetting(ENGAGEMENT_STALE_MINUTES_SETTING, "No engagement threshold", engagementStaleMinutes);

        return new MonitoringThresholds(
                warningMinutes,
                criticalMinutes,
                heartbeatStaleMinutes,
                engagementStaleMinutes);
    }

    @Transactional
    public void resendPush(Long stagedOrderId) {
        if (stagedOrderId == null) {
            throw new IllegalArgumentException("Staged order id is required");
        }

        StagedRestaurantOrder stagedOrder = stagedRestaurantOrderRepository.findById(stagedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (stagedOrder.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only pending approvals can be resent");
        }

        tabletOrderDispatchService.dispatchOrderPush(stagedOrder);
    }

    @Transactional(readOnly = true)
    public MonitoringThresholds getThresholds() {
        int warningMinutes = getIntegerSetting(WARNING_MINUTES_SETTING, DEFAULT_WARNING_MINUTES);
        int criticalMinutes = getIntegerSetting(CRITICAL_MINUTES_SETTING, DEFAULT_CRITICAL_MINUTES);
        if (criticalMinutes <= warningMinutes) {
            criticalMinutes = warningMinutes + 1;
        }

        int heartbeatStaleMinutes = getIntegerSetting(HEARTBEAT_STALE_MINUTES_SETTING, DEFAULT_HEARTBEAT_STALE_MINUTES);
        int engagementStaleMinutes = getIntegerSetting(ENGAGEMENT_STALE_MINUTES_SETTING, DEFAULT_ENGAGEMENT_STALE_MINUTES);

        return new MonitoringThresholds(
                warningMinutes,
                criticalMinutes,
                heartbeatStaleMinutes,
                engagementStaleMinutes);
    }

    private PendingOrderAlert buildAlert(
            LocalDateTime now,
            MonitoringThresholds thresholds,
            StagedRestaurantOrder order,
            TabletAsset asset,
            TabletOrderDispatch dispatch) {
        long pendingMinutes = minutesBetween(order.getSubmittedAt(), now);
        Long heartbeatMinutes = asset == null ? null : nullableMinutesBetween(asset.getLastHeartbeatAt(), now);
        boolean heartbeatStale = heartbeatMinutes == null || heartbeatMinutes >= thresholds.heartbeatStaleMinutes();

        LocalDateTime engagementAt = latestEngagementAt(dispatch);
        Long engagementMinutes = nullableMinutesBetween(engagementAt, now);
        boolean engagementStale = dispatch == null
                || engagementMinutes == null
                || engagementMinutes >= thresholds.engagementStaleMinutes();

        AlertSeverity baseSeverity;
        if (pendingMinutes >= thresholds.criticalMinutes()) {
            baseSeverity = AlertSeverity.CRITICAL;
        } else if (pendingMinutes >= thresholds.warningMinutes()) {
            baseSeverity = AlertSeverity.WARNING;
        } else {
            baseSeverity = AlertSeverity.NORMAL;
        }

        AlertStatus status;
        String recommendation;

        if (asset == null) {
            status = AlertStatus.NO_TABLET_ASSIGNED;
            recommendation = "Assign an active tablet to this restaurant before escalating.";
            baseSeverity = AlertSeverity.CRITICAL;
        } else if (baseSeverity == AlertSeverity.NORMAL) {
            status = AlertStatus.WITHIN_TARGET;
            recommendation = "No action needed.";
        } else if (heartbeatStale) {
            status = AlertStatus.TABLET_OFFLINE_SUSPECTED;
            recommendation = "Call restaurant to check tablet power/network, then resend push.";
        } else if (engagementStale) {
            status = AlertStatus.NO_RESPONSE_SUSPECTED;
            recommendation = "Tablet heartbeat is fresh but order is stale. Call restaurant and resend push.";
        } else {
            status = AlertStatus.WAITING_ON_RESTAURANT;
            recommendation = "Recent engagement detected. Monitor before calling.";
        }

        return new PendingOrderAlert(
                order.getId(),
                order.getRestaurantId(),
                order.getRestaurantName(),
                order.getContactName(),
                pendingMinutes,
                baseSeverity,
                status,
                asset == null ? null : asset.getAssetTag(),
                heartbeatMinutes,
                engagementMinutes,
                dispatch == null ? null : dispatch.getFailureCount(),
                dispatch == null ? null : dispatch.getLastFailureReason(),
                isTabletSendEnabled(order),
                recommendation,
                order.getSubmittedAt());
    }

    private boolean isTabletSendEnabled(StagedRestaurantOrder order) {
        if (order == null || order.getRestaurantId() == null) {
            return false;
        }

        LocalDate effectiveDate = order.getSubmittedAt() == null
                ? LocalDate.now()
                : order.getSubmittedAt().toLocalDate();

        return restaurantRepository.findEffectiveByRestaurantId(order.getRestaurantId(), effectiveDate)
                .stream()
                .findFirst()
                .map(restaurant -> restaurant.getSendToTablet())
                .orElse(false);
    }

    private LocalDateTime latestEngagementAt(TabletOrderDispatch dispatch) {
        if (dispatch == null) {
            return null;
        }

        LocalDateTime latest = dispatch.getPayloadPulledAt();
        if (dispatch.getAcknowledgedAt() != null && (latest == null || dispatch.getAcknowledgedAt().isAfter(latest))) {
            latest = dispatch.getAcknowledgedAt();
        }
        if (dispatch.getPushSentAt() != null && (latest == null || dispatch.getPushSentAt().isAfter(latest))) {
            latest = dispatch.getPushSentAt();
        }
        return latest;
    }

    private int getIntegerSetting(String name, int defaultValue) {
        SettingEntity setting = settingRepository.findBySectionAndName(SETTINGS_SECTION, name);
        if (setting == null || setting.getValue() == null || setting.getValue().isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(setting.getValue().trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void saveIntegerSetting(String name, String description, int value) {
        SettingEntity setting = settingRepository.findBySectionAndName(SETTINGS_SECTION, name);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(SETTINGS_SECTION);
            setting.setName(name);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.INTEGER);
        }

        setting.setValue(String.valueOf(value));
        settingRepository.save(setting);
    }

    private int clampMinutes(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return Math.max(0L, Duration.between(from, to).toMinutes());
    }

    private Long nullableMinutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        return Math.max(0L, Duration.between(from, to).toMinutes());
    }

    private static final class RestaurantAggregate {
        private final Long restaurantId;
        private final String restaurantName;
        private int pendingCount;
        private long oldestPendingMinutes;
        private AlertSeverity severity = AlertSeverity.NORMAL;
        private AlertStatus dominantStatus = AlertStatus.WITHIN_TARGET;

        private RestaurantAggregate(Long restaurantId, String restaurantName) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
        }

        private void add(PendingOrderAlert alert) {
            pendingCount += 1;
            oldestPendingMinutes = Math.max(oldestPendingMinutes, alert.pendingMinutes());

            if (alert.severity().level() > severity.level()) {
                severity = alert.severity();
            }
            if (alert.status().priority() > dominantStatus.priority()) {
                dominantStatus = alert.status();
            }
        }

        private RestaurantAlert toRestaurantAlert() {
            String action = switch (dominantStatus) {
                case TABLET_OFFLINE_SUSPECTED -> "Call restaurant now, check tablet/network, resend push.";
                case NO_RESPONSE_SUSPECTED -> "Call restaurant now and confirm order handling.";
                case NO_TABLET_ASSIGNED -> "Assign an active tablet to this restaurant.";
                case WAITING_ON_RESTAURANT -> "Monitor closely and call if aging continues.";
                case WITHIN_TARGET -> "No action needed.";
            };

            return new RestaurantAlert(
                    restaurantId,
                    restaurantName,
                    pendingCount,
                    oldestPendingMinutes,
                    severity,
                    dominantStatus,
                    action);
        }
    }

    public enum AlertSeverity {
        NORMAL(0),
        WARNING(1),
        CRITICAL(2);

        private final int level;

        AlertSeverity(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }

        public String displayName() {
            return name().toLowerCase(Locale.CANADA).replace('_', ' ');
        }
    }

    public enum AlertStatus {
        WITHIN_TARGET(0),
        WAITING_ON_RESTAURANT(1),
        NO_RESPONSE_SUSPECTED(2),
        TABLET_OFFLINE_SUSPECTED(3),
        NO_TABLET_ASSIGNED(4);

        private final int priority;

        AlertStatus(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        public String displayName() {
            return name().toLowerCase(Locale.CANADA).replace('_', ' ');
        }
    }

    public record MonitoringThresholds(
            int warningMinutes,
            int criticalMinutes,
            int heartbeatStaleMinutes,
            int engagementStaleMinutes) {
    }

    public record RestaurantAlert(
            Long restaurantId,
            String restaurantName,
            int pendingCount,
            long oldestPendingMinutes,
            AlertSeverity severity,
            AlertStatus status,
            String action) {
    }

    public record PendingOrderAlert(
            Long stagedOrderId,
            Long restaurantId,
            String restaurantName,
            String contactName,
            long pendingMinutes,
            AlertSeverity severity,
            AlertStatus status,
            String assetTag,
            Long heartbeatMinutes,
            Long engagementMinutes,
            Integer failureCount,
            String lastFailureReason,
                boolean tabletSendEnabled,
            String recommendation,
            LocalDateTime submittedAt) {
    }

    public enum TabletHealth {
        HEALTHY(0),
        ACTIVE_WITH_PENDING(1),
        OFFLINE_SUSPECTED(2),
        NO_HEARTBEAT(3),
        UNASSIGNED(4),
        NOT_CONFIGURED(5);

        private final int level;

        TabletHealth(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }

        public String displayName() {
            return name().toLowerCase(Locale.CANADA).replace('_', ' ');
        }
    }

    public record TabletAssetAlert(
            Long tabletAssetId,
            String assetTag,
            String assetName,
            String restaurantName,
            Long restaurantId,
            Long heartbeatMinutes,
            String appVersion,
            int pendingCount,
                boolean configured,
            TabletHealth health,
            String action) {
    }

    public record TabletMonitoringSnapshot(
            MonitoringThresholds thresholds,
            List<RestaurantAlert> restaurants,
            List<PendingOrderAlert> pendingOrders,
            List<TabletAssetAlert> tablets,
            LocalDateTime generatedAt) {
    }
}
