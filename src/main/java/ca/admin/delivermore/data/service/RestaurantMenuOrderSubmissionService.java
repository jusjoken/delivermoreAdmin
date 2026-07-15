package ca.admin.delivermore.data.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder.ApprovalStatus;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLine;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLineOption;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderTaxRate;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineOptionRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderTaxRateRepository;

@Service
public class RestaurantMenuOrderSubmissionService {

    private static final String SUBMISSION_SOURCE = "ADMIN_PREVIEW";

    private final RestaurantMenuOrderPreviewService previewService;
    private final StagedRestaurantOrderRepository stagedRestaurantOrderRepository;
    private final StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository;
    private final StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository;
    private final StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository;
    private final StagedRestaurantOrderWorkflowService workflowService;
    private final StagedRestaurantOrderPayloadProjectionService payloadProjectionService;

    public RestaurantMenuOrderSubmissionService(
            RestaurantMenuOrderPreviewService previewService,
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository,
            StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository,
            StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository,
            StagedRestaurantOrderWorkflowService workflowService,
            StagedRestaurantOrderPayloadProjectionService payloadProjectionService) {
        this.previewService = previewService;
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.stagedRestaurantOrderLineRepository = stagedRestaurantOrderLineRepository;
        this.stagedRestaurantOrderLineOptionRepository = stagedRestaurantOrderLineOptionRepository;
        this.stagedRestaurantOrderTaxRateRepository = stagedRestaurantOrderTaxRateRepository;
        this.workflowService = workflowService;
        this.payloadProjectionService = payloadProjectionService;
    }

    public SubmissionResult submitOrder(SubmitOrderRequest request) {
        validateRequest(request);

        RestaurantMenuOrderPreviewService.PreviewData previewData = previewService.loadPreviewData(request.restaurantId());
        Restaurant restaurant = previewData.restaurant();

        double subtotal = roundCurrency(request.lines().stream()
                .mapToDouble(line -> line.unitPrice() * line.quantity())
                .sum());
        double serviceFee = roundCurrency(subtotal * previewData.serviceFeeRate());
        double gst = roundCurrency(subtotal * RestaurantMenuOrderPreviewService.GST_RATE);
        double itemTax = calculateItemTax(request.lines(), previewData.taxationRates());
        double deliveryFee = roundCurrency(previewData.deliveryFee());
        double deliveryFeeTax = roundCurrency(deliveryFee * RestaurantMenuOrderPreviewService.GST_RATE);
        double tip = isOnlinePayment(request.paymentMethod()) ? roundCurrency(request.tip()) : 0d;
        double total = roundCurrency(subtotal + serviceFee + gst + itemTax + deliveryFee + deliveryFeeTax + tip);

        LocalDateTime now = LocalDateTime.now();
        StagedRestaurantOrder stagedOrder = new StagedRestaurantOrder();
        stagedOrder.setRestaurantId(restaurant.getRestaurantId());
        stagedOrder.setRestaurantName(restaurant.getName());
        stagedOrder.setMenuVersionId(previewData.menuVersion().getId());
        stagedOrder.setSubmissionSource(SUBMISSION_SOURCE);
        stagedOrder.setContactName(request.contactName().trim());
        stagedOrder.setContactEmail(trimToEmpty(request.contactEmail()));
        stagedOrder.setContactPhone(request.contactPhone().trim());
        stagedOrder.setStreetAddress(request.street().trim());
        stagedOrder.setCity(request.city().trim());
        stagedOrder.setPostalCode(trimToEmpty(request.postalCode()));
        stagedOrder.setPaymentMethod(request.paymentMethod().trim());
        stagedOrder.setInPersonDelivery(request.inPersonDelivery());
        stagedOrder.setSubtotal(subtotal);
        stagedOrder.setServiceFee(serviceFee);
        stagedOrder.setGst(gst);
        stagedOrder.setItemTax(itemTax);
        stagedOrder.setDeliveryFee(deliveryFee);
        stagedOrder.setDeliveryFeeTax(deliveryFeeTax);
        stagedOrder.setTip(tip);
        stagedOrder.setTotal(total);
        stagedOrder.setCustomerComments(trimToEmpty(request.comments()));
        stagedOrder.setSubmittedAt(now);
        stagedOrder.setStatusUpdatedAt(now);
        stagedOrder.setAutoApproved(restaurant.getAutoApproveOrders());

        stagedOrder.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        stagedOrder.setApprovalRequestedAt(now);
        stagedOrder.setStatusReason("Waiting for approval before Tookan submission.");

        String orderSummary = buildOrderSummary(request, previewData, subtotal, serviceFee, gst, itemTax, deliveryFee,
            deliveryFeeTax, tip, total);
        stagedOrder.setOrderSummary(orderSummary);

        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        persistStructuredSnapshot(stagedOrder.getId(), request.lines(), previewData.taxationRates());
        // Build once from structured data to validate that projection remains serializable.
        payloadProjectionService.buildPayloadJson(stagedOrder.getId());

        if (restaurant.getAutoApproveOrders()) {
            StagedRestaurantOrder approvedOrder = workflowService.approve(
                stagedOrder.getId());
            return new SubmissionResult(
                approvedOrder.getId(),
                approvedOrder.getApprovalStatus(),
                "Order #" + approvedOrder.getId() + " auto-approved and saved as OrderDetail #"
                    + approvedOrder.getOrderDetailId() + ". Tookan submission is the next phase.");
        }

        return new SubmissionResult(
                stagedOrder.getId(),
                stagedOrder.getApprovalStatus(),
                "Order #" + stagedOrder.getId() + " saved pending approval.");
    }

    private void validateRequest(SubmitOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request is required");
        }
        if (request.restaurantId() == null) {
            throw new IllegalArgumentException("Restaurant id is required");
        }
        if (isBlank(request.contactName())) {
            throw new IllegalArgumentException("Contact name is required");
        }
        if (isBlank(request.contactPhone())) {
            throw new IllegalArgumentException("Phone is required");
        }
        if (isBlank(request.street())) {
            throw new IllegalArgumentException("Street address is required");
        }
        if (isBlank(request.city())) {
            throw new IllegalArgumentException("Town / city is required");
        }
        if (isBlank(request.paymentMethod())) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (request.tip() < 0d) {
            throw new IllegalArgumentException("Tip cannot be negative");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("Add at least one item before placing the order");
        }
        for (LineItemRequest line : request.lines()) {
            if (line == null || isBlank(line.itemName())) {
                throw new IllegalArgumentException("Each line item must include a name");
            }
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Item quantities must be greater than zero");
            }
        }
    }

    private double calculateItemTax(List<LineItemRequest> lines, Map<String, Double> taxationRates) {
        double total = 0d;
        for (LineItemRequest line : lines) {
            String category = isBlank(line.primaryTaxationCategory()) ? "Food" : line.primaryTaxationCategory();
            double rate = taxationRates.getOrDefault(category, 0d);
            total += (line.unitPrice() * line.quantity()) * (rate / 100d);
        }
        return roundCurrency(total);
    }

    private void persistStructuredSnapshot(
            Long stagedOrderId,
            List<LineItemRequest> lines,
            Map<String, Double> taxationRates) {
        int lineNumber = 1;
        for (LineItemRequest line : lines) {
            String category = normalizeTaxationCategory(line.primaryTaxationCategory());
            double categoryRate = taxationRates.getOrDefault(category, 0d);

            StagedRestaurantOrderLine stagedLine = new StagedRestaurantOrderLine();
            stagedLine.setStagedOrderId(stagedOrderId);
            stagedLine.setLineNumber(lineNumber);
            stagedLine.setItemId(line.itemId());
            stagedLine.setItemName(line.itemName());
            stagedLine.setSelectedSizeName(trimToEmpty(line.selectedSizeName()));
            stagedLine.setQuantity(line.quantity());
            stagedLine.setUnitPrice(roundCurrency(line.unitPrice()));
            stagedLine.setLineSubtotal(roundCurrency(line.unitPrice() * line.quantity()));
            stagedLine.setInstructions(trimToEmpty(line.instructions()));
            stagedLine.setPrimaryTaxationCategory(category);
            stagedLine.setPrimaryTaxRate(roundCurrency(categoryRate));
            stagedLine = stagedRestaurantOrderLineRepository.save(stagedLine);

            if (line.selections() != null) {
                for (SelectedOptionRequest selection : line.selections()) {
                    String optionCategory = normalizeTaxationCategory(selection.taxationCategory());
                    double optionRate = taxationRates.getOrDefault(optionCategory, 0d);

                    StagedRestaurantOrderLineOption stagedOption = new StagedRestaurantOrderLineOption();
                    stagedOption.setStagedOrderId(stagedOrderId);
                    stagedOption.setStagedOrderLineId(stagedLine.getId());
                    stagedOption.setLineNumber(lineNumber);
                    stagedOption.setGroupName(selection.groupName());
                    stagedOption.setOptionName(selection.optionName());
                    stagedOption.setUnitPrice(roundCurrency(selection.unitPrice()));
                    stagedOption.setQuantity(selection.quantity());
                    stagedOption.setOptionSubtotal(roundCurrency(selection.unitPrice() * selection.quantity()));
                    stagedOption.setTaxationCategory(optionCategory);
                    stagedOption.setTaxationRate(roundCurrency(optionRate));
                    stagedRestaurantOrderLineOptionRepository.save(stagedOption);
                }
            }

            lineNumber++;
        }

        for (Map.Entry<String, Double> entry : taxationRates.entrySet()) {
            StagedRestaurantOrderTaxRate stagedTaxRate = new StagedRestaurantOrderTaxRate();
            stagedTaxRate.setStagedOrderId(stagedOrderId);
            stagedTaxRate.setTaxationCategory(normalizeTaxationCategory(entry.getKey()));
            stagedTaxRate.setRatePercent(roundCurrency(entry.getValue()));
            stagedRestaurantOrderTaxRateRepository.save(stagedTaxRate);
        }
    }

    private String normalizeTaxationCategory(String category) {
        if (isBlank(category)) {
            return "Food";
        }
        return category.trim();
    }

    private String buildOrderSummary(
            SubmitOrderRequest request,
            RestaurantMenuOrderPreviewService.PreviewData previewData,
            double subtotal,
            double serviceFee,
            double gst,
            double itemTax,
            double deliveryFee,
            double deliveryFeeTax,
            double tip,
            double total) {
        StringBuilder summary = new StringBuilder();
        summary.append("Restaurant: ").append(previewData.restaurant().getName()).append('\n');
        summary.append("Menu Version: ").append(previewData.menuVersion().getId()).append('\n');
        summary.append("Contact: ").append(request.contactName().trim()).append('\n');
        summary.append("Phone: ").append(request.contactPhone().trim()).append('\n');
        if (!isBlank(request.contactEmail())) {
            summary.append("Email: ").append(request.contactEmail().trim()).append('\n');
        }
        summary.append("Address: ").append(request.street().trim()).append(", ")
                .append(request.city().trim());
        if (!isBlank(request.postalCode())) {
            summary.append(' ').append(request.postalCode().trim());
        }
        summary.append('\n');
        summary.append("Payment: ").append(request.paymentMethod().trim()).append('\n');
        summary.append("In-person delivery: ").append(request.inPersonDelivery() ? "Yes" : "No").append('\n');
        if (!isBlank(request.comments())) {
            summary.append("Comments: ").append(request.comments().trim()).append('\n');
        }
        summary.append('\n').append("Items:").append('\n');
        for (LineItemRequest line : request.lines()) {
            summary.append("- ").append(line.itemName());
            if (!isBlank(line.selectedSizeName())) {
                summary.append(" [").append(line.selectedSizeName()).append(']');
            }
            summary.append(" x").append(line.quantity())
                    .append(" @ ").append(formatCurrency(line.unitPrice()))
                    .append(" = ").append(formatCurrency(roundCurrency(line.unitPrice() * line.quantity())))
                    .append('\n');
            for (SelectedOptionRequest selection : line.selections()) {
                summary.append("    • ").append(selection.groupName()).append(": ")
                        .append(selection.optionName());
                if (selection.quantity() > 1) {
                    summary.append(" x").append(selection.quantity());
                }
                if (selection.unitPrice() > 0d) {
                    summary.append(" ").append(formatCurrency(selection.unitPrice()));
                }
                summary.append('\n');
            }
            if (!isBlank(line.instructions())) {
                summary.append("    Instructions: ").append(line.instructions().trim()).append('\n');
            }
        }
        summary.append('\n');
        summary.append("Subtotal: ").append(formatCurrency(subtotal)).append('\n');
        summary.append("Service Fee: ").append(formatCurrency(serviceFee)).append('\n');
        summary.append("GST: ").append(formatCurrency(gst)).append('\n');
        summary.append("Item Tax: ").append(formatCurrency(itemTax)).append('\n');
        summary.append("Delivery Fee: ").append(formatCurrency(deliveryFee)).append('\n');
        summary.append("Delivery Fee Tax: ").append(formatCurrency(deliveryFeeTax)).append('\n');
        if (isOnlinePayment(request.paymentMethod()) || tip > 0d) {
            summary.append("Tip: ").append(formatCurrency(tip)).append('\n');
        }
        summary.append("Total: ").append(formatCurrency(total)).append('\n');
        return summary.toString();
    }

    private boolean isOnlinePayment(String paymentMethod) {
        return paymentMethod != null && "Online".equalsIgnoreCase(paymentMethod.trim());
    }

    private String formatCurrency(double value) {
        return "$" + String.format(java.util.Locale.CANADA, "%.2f", roundCurrency(value));
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record SubmissionResult(Long stagedOrderId, ApprovalStatus status, String message) {
    }

    public record SubmitOrderRequest(
            Long restaurantId,
            String contactName,
            String contactEmail,
            String contactPhone,
            String street,
            String city,
            String postalCode,
            String paymentMethod,
            boolean inPersonDelivery,
            double tip,
            String comments,
            List<LineItemRequest> lines) {
    }

    public record LineItemRequest(
            Long itemId,
            String itemName,
            String selectedSizeName,
            int quantity,
            double unitPrice,
            String instructions,
            String primaryTaxationCategory,
            List<SelectedOptionRequest> selections) {
    }

    public record SelectedOptionRequest(
            String groupName,
            String optionName,
            double unitPrice,
            int quantity,
            String taxationCategory) {
    }
}