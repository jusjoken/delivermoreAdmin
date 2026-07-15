package ca.admin.delivermore.views.restaurants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder.ApprovalStatus;
import ca.admin.delivermore.data.service.StagedRestaurantOrderWorkflowService;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Order Approvals")
@Route(value = "restaurants/order-approvals", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class StagedRestaurantOrdersView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StagedRestaurantOrderWorkflowService workflowService;

    private final Grid<StagedRestaurantOrder> grid = new Grid<>();
    private final Button pendingOnlyButton = new Button("Pending only");
    private final Button showAllButton = new Button("Show all");
    private boolean showAll;

    public StagedRestaurantOrdersView(StagedRestaurantOrderWorkflowService workflowService) {
        this.workflowService = workflowService;
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        H3 title = new H3("Staged Order Approvals");
        title.getStyle().set("margin", "0");

        Button refreshButton = new Button("Refresh", event -> refreshGrid());
        pendingOnlyButton.addClickListener(event -> {
            showAll = false;
            refreshGrid();
        });
        showAllButton.addClickListener(event -> {
            showAll = true;
            refreshGrid();
        });

        HorizontalLayout toolbar = UIUtilities.getHorizontalLayout(false, true, false);
        toolbar.add(title, refreshButton, pendingOnlyButton, showAllButton);
        toolbar.expand(title);

        configureGrid();

        add(toolbar, grid);
        setFlexGrow(1, grid);
        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.addColumn(StagedRestaurantOrder::getId).setHeader("Staged #").setAutoWidth(true);
        grid.addColumn(order -> formatDateTime(order.getSubmittedAt())).setHeader("Submitted").setAutoWidth(true);
        grid.addColumn(StagedRestaurantOrder::getRestaurantName).setHeader("Restaurant").setAutoWidth(true);
        grid.addColumn(StagedRestaurantOrder::getContactName).setHeader("Contact").setAutoWidth(true);
        grid.addColumn(StagedRestaurantOrder::getContactPhone).setHeader("Phone").setAutoWidth(true);
        grid.addColumn(order -> formatCurrency(order.getTotal())).setHeader("Total").setAutoWidth(true);
        grid.addColumn(order -> order.getApprovalStatus().name()).setHeader("Status").setAutoWidth(true);
        grid.addColumn(order -> formatNullableLong(order.getOrderDetailId())).setHeader("OrderDetail #").setAutoWidth(true);
        grid.addColumn(order -> order.getStatusReason() == null ? "" : order.getStatusReason()).setHeader("Reason").setFlexGrow(1);

        grid.addComponentColumn(this::buildActionButtons)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private HorizontalLayout buildActionButtons(StagedRestaurantOrder order) {
        Button approve = new Button("Approve");
        approve.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        approve.setEnabled(order.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL);
        approve.addClickListener(event -> openApproveDialog(order));

        Button decline = new Button("Decline");
        decline.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        decline.setEnabled(order.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL);
        decline.addClickListener(event -> openStatusDialog(order, "Decline", "Decline order", true,
                reason -> workflowService.decline(order.getId(), reason)));

        Button cancel = new Button("Cancel");
        cancel.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        cancel.setEnabled(order.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL);
        cancel.addClickListener(event -> openStatusDialog(order, "Cancel", "Cancel order", false,
                reason -> workflowService.cancel(order.getId(), reason)));

        HorizontalLayout actions = new HorizontalLayout(approve, decline, cancel);
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private void openApproveDialog(StagedRestaurantOrder order) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Approve order #" + order.getId());
        dialog.setCancelable(true);
        dialog.setCancelText("Close");
        dialog.setConfirmText("Approve");

        dialog.setText("Approve this order and create the OrderDetail record. No reason is needed for approval.");

        dialog.addConfirmListener(event -> {
            try {
                StagedRestaurantOrder updated = workflowService.approve(order.getId());
                UIUtilities.showNotification("Updated order #" + updated.getId() + " to " + updated.getApprovalStatus().name());
                refreshGrid();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                UIUtilities.showNotificationError(ex.getMessage());
            }
        });

        dialog.open();
    }

    private void openStatusDialog(
            StagedRestaurantOrder order,
            String actionLabel,
            String heading,
            boolean reasonRequired,
            java.util.function.Function<String, StagedRestaurantOrder> action) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(heading + " #" + order.getId());
        dialog.setCancelable(true);
        dialog.setCancelText("Close");
        dialog.setConfirmText(actionLabel);

        TextArea reasonField = new TextArea("Reason");
        reasonField.setWidthFull();
        reasonField.setPlaceholder(reasonRequired ? "Reason is required" : "Optional reason");
        reasonField.setRequiredIndicatorVisible(reasonRequired);
        dialog.add(reasonField);

        dialog.addConfirmListener(event -> {
            String reason = reasonField.getValue();
            if (reasonRequired && (reason == null || reason.isBlank())) {
                UIUtilities.showNotificationError("Reason is required");
                return;
            }
            try {
                StagedRestaurantOrder updated = action.apply(reason);
                UIUtilities.showNotification("Updated order #" + updated.getId() + " to " + updated.getApprovalStatus().name());
                refreshGrid();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                UIUtilities.showNotificationError(ex.getMessage());
            }
        });

        dialog.open();
    }

    private void refreshGrid() {
        List<StagedRestaurantOrder> orders = showAll
                ? workflowService.listAll()
                : workflowService.listPendingApprovals();
        grid.setItems(orders);
        pendingOnlyButton.setEnabled(showAll);
        showAllButton.setEnabled(!showAll);
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String formatCurrency(Double value) {
        double numericValue = value == null ? 0.0 : value;
        return String.format(java.util.Locale.CANADA, "$%.2f", numericValue);
    }

    private String formatNullableLong(Long value) {
        return value == null ? "" : String.valueOf(value);
    }
}