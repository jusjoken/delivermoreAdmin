package ca.admin.delivermore.views.asset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.data.service.TabletOrderMonitoringService;
import ca.admin.delivermore.data.service.TabletOrderMonitoringService.MonitoringThresholds;
import ca.admin.delivermore.data.service.TabletOrderMonitoringService.PendingOrderAlert;
import ca.admin.delivermore.data.service.TabletOrderMonitoringService.RestaurantAlert;
import ca.admin.delivermore.data.service.TabletOrderMonitoringService.TabletAssetAlert;
import ca.admin.delivermore.data.service.TabletOrderMonitoringService.TabletMonitoringSnapshot;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Tablet Monitor")
@Route(value = "tablet-assets/order-monitor", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class TabletOrderMonitoringView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TabletOrderMonitoringService monitoringService;

    private final IntegerField warningMinutesField = buildMinuteField("Warning pending age (minutes)");
    private final IntegerField criticalMinutesField = buildMinuteField("Critical pending age (minutes)");
    private final IntegerField heartbeatStaleField = buildMinuteField("Heartbeat stale threshold (minutes)");
    private final IntegerField engagementStaleField = buildMinuteField("No engagement threshold (minutes)");

    private final Span generatedAtLabel = new Span();
    private final Span timerSummaryLabel = new Span();
    private final Checkbox configuredOnlyToggle = new Checkbox("Configured only", true);

    private final Grid<RestaurantAlert> restaurantGrid = new Grid<>();
    private final Grid<TabletAssetAlert> tabletGrid = new Grid<>();
    private final Grid<PendingOrderAlert> pendingGrid = new Grid<>();
    private List<TabletAssetAlert> currentTabletAlerts = List.of();
    private final VerticalLayout restaurantSection;
    private final VerticalLayout tabletSection;
    private final VerticalLayout pendingSection;

    public TabletOrderMonitoringView(TabletOrderMonitoringService monitoringService) {
        this.monitoringService = monitoringService;

        setSizeFull();
        setPadding(false);
        setSpacing(true);

        add(buildHeader());
        add(buildTimerSummaryRow());
        restaurantSection = buildRestaurantSection();
        tabletSection = buildTabletSection();
        pendingSection = buildPendingSection();

        add(restaurantSection);
        add(tabletSection);
        add(pendingSection);

        setFlexGrow(1, restaurantSection);
        setFlexGrow(1, tabletSection);
        setFlexGrow(2, pendingSection);

        refreshData();
    }

    private HorizontalLayout buildHeader() {
        H3 title = new H3("Tablet Monitor");
        title.getStyle().set("margin", "0");

        generatedAtLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
        generatedAtLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Button refreshButton = new Button("Refresh", event -> refreshData());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = UIUtilities.getHorizontalLayout(false, true, false);
        header.add(title, generatedAtLabel, refreshButton);
        header.expand(title);
        header.setAlignItems(Alignment.END);
        return header;
    }

    private HorizontalLayout buildTimerSummaryRow() {
        timerSummaryLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
        timerSummaryLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Button editTimersButton = new Button("Edit timers", event -> openThresholdDialog());
        editTimersButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout row = UIUtilities.getHorizontalLayout(false, true, false);
        row.setAlignItems(Alignment.CENTER);
        row.add(timerSummaryLabel, editTimersButton);
        row.expand(timerSummaryLabel);
        return row;
    }

    private void openThresholdDialog() {
        warningMinutesField.setHelperText("Pending orders move to warning at this age.");
        criticalMinutesField.setHelperText("Pending orders move to critical at this age.");
        heartbeatStaleField.setHelperText("If heartbeat is older than this, classify as offline suspected.");
        engagementStaleField.setHelperText("If no ack/pull/sent signal newer than this, classify as no-response.");

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit escalation timers");

        VerticalLayout fields = UIUtilities.getVerticalLayout(false, true, false);
        fields.setWidth("620px");
        fields.add(warningMinutesField, criticalMinutesField, heartbeatStaleField, engagementStaleField);

        Button saveButton = new Button("Save", event -> {
            if (saveThresholds()) {
                dialog.close();
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout actions = UIUtilities.getHorizontalLayout(false, true, false);
        actions.add(saveButton, cancelButton);

        dialog.add(fields);
        dialog.getFooter().add(actions);
        dialog.open();
    }

    private VerticalLayout buildRestaurantSection() {
        H4 heading = new H4("Restaurant risk summary");
        heading.getStyle().set("margin", "0");

        restaurantGrid.setSizeFull();
        restaurantGrid.setMinHeight("180px");
        restaurantGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        restaurantGrid.addColumn(RestaurantAlert::restaurantName).setHeader("Restaurant").setAutoWidth(true).setFlexGrow(1);
        restaurantGrid.addColumn(RestaurantAlert::pendingCount).setHeader("Pending").setAutoWidth(true);
        restaurantGrid.addColumn(RestaurantAlert::oldestPendingMinutes).setHeader("Oldest pending (m)").setAutoWidth(true);
        restaurantGrid.addColumn(alert -> alert.severity().displayName()).setHeader("Severity").setAutoWidth(true);
        restaurantGrid.addColumn(alert -> alert.status().displayName()).setHeader("Status").setAutoWidth(true);
        restaurantGrid.addColumn(RestaurantAlert::action).setHeader("Recommended action").setFlexGrow(1);

        VerticalLayout section = UIUtilities.getVerticalLayout(false, true, false);
        section.add(heading, restaurantGrid);
        section.setFlexGrow(1, restaurantGrid);
        return section;
    }

    private VerticalLayout buildPendingSection() {
        H4 heading = new H4("Pending orders");
        heading.getStyle().set("margin", "0");

        pendingGrid.setSizeFull();
        pendingGrid.setMinHeight("420px");
        pendingGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        pendingGrid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        pendingGrid.addColumn(PendingOrderAlert::stagedOrderId).setHeader("Staged #").setAutoWidth(true);
        pendingGrid.addColumn(PendingOrderAlert::restaurantName).setHeader("Restaurant").setAutoWidth(true);
        pendingGrid.addColumn(PendingOrderAlert::contactName).setHeader("Contact").setAutoWidth(true);
        pendingGrid.addColumn(PendingOrderAlert::pendingMinutes).setHeader("Pending (m)").setAutoWidth(true);
        pendingGrid.addColumn(alert -> formatNullableMinutes(alert.heartbeatMinutes())).setHeader("Heartbeat age (m)").setAutoWidth(true);
        pendingGrid.addColumn(alert -> formatNullableMinutes(alert.engagementMinutes())).setHeader("Engagement age (m)").setAutoWidth(true);
        pendingGrid.addColumn(alert -> alert.severity().displayName()).setHeader("Severity").setAutoWidth(true);
        pendingGrid.addColumn(alert -> alert.status().displayName()).setHeader("Status").setAutoWidth(true);
        pendingGrid.addColumn(PendingOrderAlert::assetTag).setHeader("Asset tag").setAutoWidth(true);
        pendingGrid.addColumn(alert -> alert.lastFailureReason() == null ? "" : alert.lastFailureReason()).setHeader("Last failure").setFlexGrow(1);

        pendingGrid.addComponentColumn(this::buildResendButton)
                .setHeader("Action")
                .setAutoWidth(true)
                .setFlexGrow(0);

        VerticalLayout section = UIUtilities.getVerticalLayout(false, true, false);
        section.add(heading, pendingGrid);
        section.setFlexGrow(1, pendingGrid);
        return section;
    }

    private VerticalLayout buildTabletSection() {
        H4 heading = new H4("Tablet status");
        heading.getStyle().set("margin", "0");

        configuredOnlyToggle.addValueChangeListener(event -> applyTabletFilter());

        HorizontalLayout header = UIUtilities.getHorizontalLayout(false, true, false);
        header.setAlignItems(Alignment.CENTER);
        header.add(heading, configuredOnlyToggle);
        header.expand(heading);

        tabletGrid.setSizeFull();
        tabletGrid.setMinHeight("220px");
        tabletGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        tabletGrid.addColumn(TabletAssetAlert::assetTag).setHeader("Asset tag").setAutoWidth(true);
        tabletGrid.addColumn(TabletAssetAlert::assetName).setHeader("Tablet").setAutoWidth(true);
        tabletGrid.addColumn(alert -> {
            String name = alert.restaurantName();
            return (name == null || name.isBlank()) ? "(unassigned)" : name;
        }).setHeader("Restaurant").setAutoWidth(true).setFlexGrow(1);
        tabletGrid.addColumn(alert -> alert.configured() ? "yes" : "no")
            .setHeader("Configured")
            .setAutoWidth(true);
        tabletGrid.addColumn(TabletAssetAlert::pendingCount).setHeader("Pending").setAutoWidth(true);
        tabletGrid.addColumn(alert -> formatNullableMinutes(alert.heartbeatMinutes())).setHeader("Heartbeat age (m)").setAutoWidth(true);
        tabletGrid.addColumn(alert -> {
            String version = alert.appVersion();
            return (version == null || version.isBlank()) ? "n/a" : version;
        }).setHeader("App ver").setAutoWidth(true);
        tabletGrid.addColumn(alert -> alert.health().displayName()).setHeader("Health").setAutoWidth(true);
        tabletGrid.addColumn(TabletAssetAlert::action).setHeader("Recommended action").setFlexGrow(1);

        VerticalLayout section = UIUtilities.getVerticalLayout(false, true, false);
        section.add(header, tabletGrid);
        section.setFlexGrow(1, tabletGrid);
        return section;
    }

    private Button buildResendButton(PendingOrderAlert alert) {
        boolean tabletSendEnabled = alert.tabletSendEnabled();
        String buttonText = tabletSendEnabled ? "Send to tablet" : "Tablet send disabled";
        Button resend = new Button(buttonText, event -> resendPush(alert.stagedOrderId()));
        resend.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        boolean canResend = tabletSendEnabled
                && alert.stagedOrderId() != null
                && alert.assetTag() != null
                && !alert.assetTag().isBlank();
        resend.setEnabled(canResend);
        return resend;
    }

    private void resendPush(Long stagedOrderId) {
        try {
            monitoringService.resendPush(stagedOrderId);
            UIUtilities.showNotification("Push resend triggered for order #" + stagedOrderId);
            refreshData();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            UIUtilities.showNotificationError(ex.getMessage());
        }
    }

    private boolean saveThresholds() {
        try {
            MonitoringThresholds saved = monitoringService.saveThresholds(new MonitoringThresholds(
                    sanitizeFieldValue(warningMinutesField, 1),
                    sanitizeFieldValue(criticalMinutesField, 2),
                    sanitizeFieldValue(heartbeatStaleField, 1),
                    sanitizeFieldValue(engagementStaleField, 1)));
            applyThresholds(saved);
            UIUtilities.showNotification("Monitoring timers updated");
            refreshData();
            return true;
        } catch (IllegalArgumentException ex) {
            UIUtilities.showNotificationError(ex.getMessage());
            return false;
        }
    }

    private int sanitizeFieldValue(IntegerField field, int fallback) {
        Integer value = field.getValue();
        if (value == null) {
            return fallback;
        }
        return Math.max(1, value);
    }

    private void refreshData() {
        TabletMonitoringSnapshot snapshot = monitoringService.loadSnapshot();
        applyThresholds(snapshot.thresholds());
        generatedAtLabel.setText("Updated " + formatDateTime(snapshot.generatedAt()));

        restaurantGrid.setItems(snapshot.restaurants());
        currentTabletAlerts = snapshot.tablets();
        applyTabletFilter();
        pendingGrid.setItems(snapshot.pendingOrders());
    }

    private void applyTabletFilter() {
        if (configuredOnlyToggle.getValue()) {
            tabletGrid.setItems(currentTabletAlerts.stream().filter(TabletAssetAlert::configured).toList());
        } else {
            tabletGrid.setItems(currentTabletAlerts);
        }
    }

    private void applyThresholds(MonitoringThresholds thresholds) {
        warningMinutesField.setValue(thresholds.warningMinutes());
        criticalMinutesField.setValue(thresholds.criticalMinutes());
        heartbeatStaleField.setValue(thresholds.heartbeatStaleMinutes());
        engagementStaleField.setValue(thresholds.engagementStaleMinutes());
        updateTimerSummary(thresholds);
    }

    private void updateTimerSummary(MonitoringThresholds thresholds) {
        timerSummaryLabel.setText(String.format(
                Locale.CANADA,
                "Escalation timers: warning %dm, critical %dm, heartbeat stale %dm, no engagement %dm",
                thresholds.warningMinutes(),
                thresholds.criticalMinutes(),
                thresholds.heartbeatStaleMinutes(),
                thresholds.engagementStaleMinutes()));
    }

    private IntegerField buildMinuteField(String label) {
        IntegerField field = new IntegerField(label);
        field.setMin(1);
        field.setMax(480);
        field.setStepButtonsVisible(true);
        field.setWidth("260px");
        return field;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private String formatNullableMinutes(Long value) {
        if (value == null) {
            return "n/a";
        }
        return String.format(Locale.CANADA, "%d", value);
    }
}
