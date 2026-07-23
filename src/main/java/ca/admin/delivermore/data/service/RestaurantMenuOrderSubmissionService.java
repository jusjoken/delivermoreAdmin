package ca.admin.delivermore.data.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private final TabletOrderDispatchService tabletOrderDispatchService;
    private final DeliveryZoneService deliveryZoneService;
    private final RestaurantMenuEditorService restaurantMenuEditorService;

    public RestaurantMenuOrderSubmissionService(
            RestaurantMenuOrderPreviewService previewService,
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository,
            StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository,
            StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository,
            StagedRestaurantOrderWorkflowService workflowService,
            StagedRestaurantOrderPayloadProjectionService payloadProjectionService,
            TabletOrderDispatchService tabletOrderDispatchService,
            DeliveryZoneService deliveryZoneService,
            RestaurantMenuEditorService restaurantMenuEditorService) {
        this.previewService = previewService;
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.stagedRestaurantOrderLineRepository = stagedRestaurantOrderLineRepository;
        this.stagedRestaurantOrderLineOptionRepository = stagedRestaurantOrderLineOptionRepository;
        this.stagedRestaurantOrderTaxRateRepository = stagedRestaurantOrderTaxRateRepository;
        this.workflowService = workflowService;
        this.payloadProjectionService = payloadProjectionService;
        this.tabletOrderDispatchService = tabletOrderDispatchService;
        this.deliveryZoneService = deliveryZoneService;
        this.restaurantMenuEditorService = restaurantMenuEditorService;
    }

    public SubmissionResult submitOrder(SubmitOrderRequest request) {
        validateRequest(request);

        RestaurantMenuOrderPreviewService.PreviewData previewData = previewService.loadPreviewData(request.restaurantId());
        Restaurant restaurant = previewData.restaurant();

        double subtotal = roundCurrency(request.lines().stream()
                .mapToDouble(line -> line.unitPrice() * line.quantity())
                .sum());
        double serviceFee = roundCurrency(subtotal * previewData.serviceFeeRate());

        Map<String, CategoryTaxLine> usedCategoryTaxes = calculateUsedCategoryTaxes(
            request.lines(),
            previewData,
            previewData.taxationRates());
        double itemTax = roundCurrency(usedCategoryTaxes.values().stream().mapToDouble(CategoryTaxLine::amount).sum());

        double serviceFeeTax = calculateNamedTax(serviceFee, previewData.serviceFeeTax());
        DeliveryZoneService.DeliveryQuote deliveryQuote = deliveryZoneService.quoteForRestaurant(
            restaurant,
            request.customerLatitude(),
            request.customerLongitude());
        if (!deliveryQuote.matched()) {
            throw new IllegalStateException(deliveryQuote.message());
        }
        double deliveryFee = roundCurrency(deliveryQuote.deliveryFee());
        double deliveryFeeTax = calculateNamedTax(deliveryFee, previewData.deliveryFeeTax());

        double tip = isOnlinePayment(request.paymentMethod()) ? roundCurrency(request.tip()) : 0d;
        double total = roundCurrency(subtotal + serviceFee + itemTax + serviceFeeTax + deliveryFee + deliveryFeeTax + tip);

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
        stagedOrder.setCustomerLatitude(request.customerLatitude());
        stagedOrder.setCustomerLongitude(request.customerLongitude());
        stagedOrder.setLocationConfirmedAt(now);
        stagedOrder.setDeliveryDistanceKm(deliveryQuote.distanceKm());
        stagedOrder.setDeliveryZoneName(trimToEmpty(deliveryQuote.zoneName()));
        stagedOrder.setPaymentMethod(request.paymentMethod().trim());
        stagedOrder.setInPersonDelivery(request.inPersonDelivery());
        stagedOrder.setSubtotal(subtotal);
        stagedOrder.setServiceFee(serviceFee);
        stagedOrder.setServiceFeeTax(serviceFeeTax);
        stagedOrder.setGst(0d);
        stagedOrder.setItemTax(itemTax);
        stagedOrder.setDeliveryFee(deliveryFee);
        stagedOrder.setDeliveryFeeTax(deliveryFeeTax);
        stagedOrder.setDriveMinutesToCustomer(deliveryQuote.driveMinutesToCustomer());
        stagedOrder.setTip(tip);
        stagedOrder.setTotal(total);
        stagedOrder.setCustomerComments(trimToEmpty(request.comments()));
        stagedOrder.setSubmittedAt(now);
        stagedOrder.setStatusUpdatedAt(now);
        stagedOrder.setAutoApproved(restaurant.getAutoApproveOrders());
        stagedOrder.setCheckoutTimeoutMinutes(previewData.checkoutTimeoutMinutes());

        stagedOrder.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        stagedOrder.setApprovalRequestedAt(now);
        stagedOrder.setStatusReason("Waiting for approval before Tookan submission.");

        String orderSummary = buildOrderSummary(
                request,
                previewData,
                usedCategoryTaxes,
                subtotal,
                serviceFee,
                itemTax,
                serviceFeeTax,
                deliveryFee,
                deliveryFeeTax,
                tip,
                total);
        stagedOrder.setOrderSummary(orderSummary);

        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        persistStructuredSnapshot(stagedOrder.getId(), request.lines(), previewData, usedCategoryTaxes);
        payloadProjectionService.buildPayloadJson(stagedOrder.getId());
        tabletOrderDispatchService.dispatchOrderPush(stagedOrder);

        if (restaurant.getAutoApproveOrders()) {
            int autoApproveMinutes = restaurantMenuEditorService.getCheckoutAutoApproveMinutes();
            StagedRestaurantOrder approvedOrder = workflowService.accept(stagedOrder.getId(), autoApproveMinutes);
            return new SubmissionResult(
                    approvedOrder.getId(),
                    approvedOrder.getApprovalStatus(),
                    approvedOrder.getCheckoutTimeoutMinutes(),
                    approvedOrder.getApprovedAt(),
                approvedOrder.getRestaurantMinutesToCustomer(),
                    "Order #" + approvedOrder.getId() + " auto-approved and saved as OrderDetail #"
                            + approvedOrder.getOrderDetailId() + ". Tookan submission is the next phase.");
        }

        return new SubmissionResult(
                stagedOrder.getId(),
                stagedOrder.getApprovalStatus(),
                stagedOrder.getCheckoutTimeoutMinutes(),
                stagedOrder.getApprovalRequestedAt(),
                stagedOrder.getRestaurantMinutesToCustomer(),
                "Order #" + stagedOrder.getId() + " saved pending approval.");
    }

    public OrderStatusSnapshot getOrderStatus(Long stagedOrderId) {
        StagedRestaurantOrder stagedOrder = stagedRestaurantOrderRepository.findById(stagedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Staged order not found: " + stagedOrderId));
        int timeoutMinutes = stagedOrder.getCheckoutTimeoutMinutes() == null ? 10 : Math.max(1, stagedOrder.getCheckoutTimeoutMinutes());
        LocalDateTime approvalRequestedAt = stagedOrder.getApprovalRequestedAt() == null
                ? stagedOrder.getSubmittedAt()
                : stagedOrder.getApprovalRequestedAt();
        LocalDateTime approvalDeadlineAt = approvalRequestedAt == null ? null : approvalRequestedAt.plusMinutes(timeoutMinutes);
        return new OrderStatusSnapshot(
                stagedOrder.getId(),
                stagedOrder.getRestaurantName(),
                stagedOrder.getApprovalStatus(),
                stagedOrder.getStatusReason(),
                approvalRequestedAt,
                approvalDeadlineAt,
                timeoutMinutes,
                stagedOrder.getDriveMinutesToCustomer(),
                stagedOrder.getRestaurantMinutesToCustomer(),
                stagedOrder.getApprovedAt(),
                stagedOrder.getStatusUpdatedAt());
    }

    public StagedRestaurantOrder markOrderMissed(Long stagedOrderId, String reason) {
        return workflowService.markMissed(stagedOrderId, reason);
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
        if (request.customerLatitude() == null || request.customerLongitude() == null) {
            throw new IllegalArgumentException("Confirm the customer location on the map before placing the order");
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

    private Map<String, CategoryTaxLine> calculateUsedCategoryTaxes(
            List<LineItemRequest> lines,
            RestaurantMenuOrderPreviewService.PreviewData previewData,
            Map<String, RestaurantMenuOrderPreviewService.TaxRateConfig> taxationRates) {
        Map<String, CategoryTaxLine> byCategory = new LinkedHashMap<>();
        for (LineItemRequest line : lines) {
            double lineSubtotal = roundCurrency(line.unitPrice() * line.quantity());
            double optionSubtotalTotal = 0d;

            if (line.selections() != null) {
                for (SelectedOptionRequest selection : line.selections()) {
                    double optionSubtotal = roundCurrency(selection.unitPrice() * selection.quantity() * line.quantity());
                    if (optionSubtotal > 0d) {
                        optionSubtotalTotal = roundCurrency(optionSubtotalTotal + optionSubtotal);
                    }

                    String optionCategory = normalizeTaxationCategory(
                            resolveSelectionTaxationCategory(line, selection, previewData));
                    if (optionCategory.isEmpty()) {
                        continue;
                    }

                    Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> optionRate =
                            findTaxRateEntry(taxationRates, optionCategory);
                    if (optionRate == null || optionRate.getValue().percentage() <= 0d || optionSubtotal <= 0d) {
                        continue;
                    }

                    double optionTax = roundCurrency(optionSubtotal * (optionRate.getValue().percentage() / 100d));
                    mergeCategoryTax(byCategory, optionRate.getKey(), optionRate.getValue(), optionTax);
                }
            }

            double baseSubtotal = roundCurrency(lineSubtotal - optionSubtotalTotal);
            String baseCategory = normalizeTaxationCategory(line.primaryTaxationCategory());
            if (baseSubtotal > 0d && !baseCategory.isEmpty()) {
                Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> baseRate =
                        findTaxRateEntry(taxationRates, baseCategory);
                if (baseRate != null && baseRate.getValue().percentage() > 0d) {
                    double baseTax = roundCurrency(baseSubtotal * (baseRate.getValue().percentage() / 100d));
                    mergeCategoryTax(byCategory, baseRate.getKey(), baseRate.getValue(), baseTax);
                }
            }
        }
        return aggregateTaxesByNameAndRate(byCategory);
    }

    private Map<String, CategoryTaxLine> aggregateTaxesByNameAndRate(Map<String, CategoryTaxLine> byCategory) {
        Map<String, CategoryTaxLine> merged = new LinkedHashMap<>();
        for (CategoryTaxLine taxLine : byCategory.values()) {
            String key = buildTaxIdentityKey(taxLine.taxName(), taxLine.ratePercent());
            CategoryTaxLine existing = merged.get(key);
            if (existing == null) {
                // Use tax name as category key when present so payload maps collapse to one tax item for Android.
                String categoryKey = isBlank(taxLine.taxName())
                        ? normalizeTaxationCategory(taxLine.category())
                        : taxLine.taxName().trim();
                merged.put(
                        key,
                        new CategoryTaxLine(
                                categoryKey,
                                trimToEmpty(taxLine.taxName()),
                                roundCurrency(taxLine.ratePercent()),
                                roundCurrency(taxLine.amount())));
                continue;
            }
            merged.put(
                    key,
                    new CategoryTaxLine(
                            existing.category(),
                            existing.taxName(),
                            existing.ratePercent(),
                            roundCurrency(existing.amount() + taxLine.amount())));
        }
        return merged;
    }

    private String buildTaxIdentityKey(String taxName, double ratePercent) {
        return trimToEmpty(taxName).toLowerCase(java.util.Locale.CANADA)
                + "|"
                + String.format(java.util.Locale.CANADA, "%.4f", roundCurrency(ratePercent));
    }

    private void mergeCategoryTax(
            Map<String, CategoryTaxLine> byCategory,
            String category,
            RestaurantMenuOrderPreviewService.TaxRateConfig config,
            double taxAmount) {
        CategoryTaxLine existing = byCategory.get(category);
        if (existing == null) {
            byCategory.put(category, new CategoryTaxLine(category, config.taxName(), config.percentage(), taxAmount));
            return;
        }
        byCategory.put(
                category,
                new CategoryTaxLine(
                        category,
                        existing.taxName(),
                        existing.ratePercent(),
                        roundCurrency(existing.amount() + taxAmount)));
    }

    private double calculateNamedTax(double taxableAmount, RestaurantMenuOrderPreviewService.TaxRateConfig taxConfig) {
        if (taxConfig == null || taxableAmount <= 0d || taxConfig.percentage() <= 0d) {
            return 0d;
        }
        return roundCurrency(taxableAmount * (taxConfig.percentage() / 100d));
    }

    private void persistStructuredSnapshot(
            Long stagedOrderId,
            List<LineItemRequest> lines,
            RestaurantMenuOrderPreviewService.PreviewData previewData,
            Map<String, CategoryTaxLine> usedCategoryTaxes) {
        int lineNumber = 1;
        for (LineItemRequest line : lines) {
            String category = normalizeTaxationCategory(line.primaryTaxationCategory());
            double categoryRate = resolveCategoryRate(previewData.taxationRates(), category);

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
                    String optionCategory = normalizeTaxationCategory(
                            resolveSelectionTaxationCategory(line, selection, previewData));
                    double optionRate = resolveCategoryRate(previewData.taxationRates(), optionCategory);

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

        for (CategoryTaxLine categoryTax : usedCategoryTaxes.values()) {
            StagedRestaurantOrderTaxRate stagedTaxRate = new StagedRestaurantOrderTaxRate();
            stagedTaxRate.setStagedOrderId(stagedOrderId);
            stagedTaxRate.setTaxScope("CATEGORY");
            stagedTaxRate.setTaxationCategory(normalizeTaxationCategory(categoryTax.category()));
            stagedTaxRate.setTaxName(trimToEmpty(categoryTax.taxName()));
            stagedTaxRate.setRatePercent(roundCurrency(categoryTax.ratePercent()));
            stagedRestaurantOrderTaxRateRepository.save(stagedTaxRate);
        }

        StagedRestaurantOrderTaxRate serviceFeeTaxRate = new StagedRestaurantOrderTaxRate();
        serviceFeeTaxRate.setStagedOrderId(stagedOrderId);
        serviceFeeTaxRate.setTaxScope("SERVICE_FEE");
        serviceFeeTaxRate.setTaxationCategory("SERVICE_FEE");
        serviceFeeTaxRate.setTaxName(trimToEmpty(previewData.serviceFeeTax().taxName()));
        serviceFeeTaxRate.setRatePercent(roundCurrency(previewData.serviceFeeTax().percentage()));
        stagedRestaurantOrderTaxRateRepository.save(serviceFeeTaxRate);

        StagedRestaurantOrderTaxRate deliveryFeeTaxRate = new StagedRestaurantOrderTaxRate();
        deliveryFeeTaxRate.setStagedOrderId(stagedOrderId);
        deliveryFeeTaxRate.setTaxScope("DELIVERY_FEE");
        deliveryFeeTaxRate.setTaxationCategory("DELIVERY_FEE");
        deliveryFeeTaxRate.setTaxName(trimToEmpty(previewData.deliveryFeeTax().taxName()));
        deliveryFeeTaxRate.setRatePercent(roundCurrency(previewData.deliveryFeeTax().percentage()));
        stagedRestaurantOrderTaxRateRepository.save(deliveryFeeTaxRate);
    }

    private double resolveCategoryRate(
            Map<String, RestaurantMenuOrderPreviewService.TaxRateConfig> taxationRates,
            String category) {
        if (category == null || category.isBlank()) {
            return 0d;
        }
        Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> rateEntry =
                findTaxRateEntry(taxationRates, category);
        if (rateEntry == null || rateEntry.getValue() == null) {
            return 0d;
        }
        return rateEntry.getValue().percentage();
    }

    private String normalizeTaxationCategory(String category) {
        if (isBlank(category)) {
            return "";
        }
        return category.trim();
    }

    private String resolveSelectionTaxationCategory(
            LineItemRequest line,
            SelectedOptionRequest selection,
            RestaurantMenuOrderPreviewService.PreviewData previewData) {
        String direct = normalizeTaxationCategory(selection.taxationCategory());
        if (!direct.isEmpty()) {
            return direct;
        }

        RestaurantMenuOrderPreviewService.ItemData item = findItem(previewData, line.itemId());
        if (item == null) {
            return "";
        }

        List<RestaurantMenuOrderPreviewService.OptionGroupData> candidates = new java.util.ArrayList<>();
        if (item.optionGroups() != null) {
            candidates.addAll(item.optionGroups());
        }

        String selectedSizeName = trimToEmpty(line.selectedSizeName());
        if (!selectedSizeName.isEmpty() && item.sizes() != null) {
            for (RestaurantMenuOrderPreviewService.SizeData size : item.sizes()) {
                if (size != null
                        && size.name() != null
                        && size.name().equalsIgnoreCase(selectedSizeName)
                        && size.optionGroups() != null) {
                    candidates.addAll(size.optionGroups());
                    break;
                }
            }
        }

        RestaurantMenuOrderPreviewService.OptionGroupData fallbackGroupMatch = null;
        for (RestaurantMenuOrderPreviewService.OptionGroupData group : candidates) {
            if (group == null || group.name() == null) {
                continue;
            }
            if (!group.name().equalsIgnoreCase(selection.groupName())) {
                continue;
            }
            if (fallbackGroupMatch == null) {
                fallbackGroupMatch = group;
            }
            boolean optionMatches = group.options().stream()
                    .filter(option -> option != null && option.name() != null)
                    .anyMatch(option -> option.name().equalsIgnoreCase(selection.optionName()));
            if (optionMatches) {
                return normalizeTaxationCategory(group.taxationCategory());
            }
        }

        if (fallbackGroupMatch != null) {
            return normalizeTaxationCategory(fallbackGroupMatch.taxationCategory());
        }
        return "";
    }

    private RestaurantMenuOrderPreviewService.ItemData findItem(
            RestaurantMenuOrderPreviewService.PreviewData previewData,
            Long itemId) {
        if (previewData == null || itemId == null || previewData.categories() == null) {
            return null;
        }
        for (RestaurantMenuOrderPreviewService.CategoryData category : previewData.categories()) {
            if (category == null || category.items() == null) {
                continue;
            }
            for (RestaurantMenuOrderPreviewService.ItemData item : category.items()) {
                if (item != null && item.id() != null && item.id().equals(itemId)) {
                    return item;
                }
            }
        }
        return null;
    }

    private Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> findTaxRateEntry(
            Map<String, RestaurantMenuOrderPreviewService.TaxRateConfig> taxationRates,
            String category) {
        if (taxationRates == null || category == null || category.isBlank()) {
            return null;
        }
        RestaurantMenuOrderPreviewService.TaxRateConfig exact = taxationRates.get(category);
        if (exact != null) {
            return Map.entry(category, exact);
        }
        for (Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> entry : taxationRates.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(category)) {
                return entry;
            }
        }
        return null;
    }

    private String buildOrderSummary(
            SubmitOrderRequest request,
            RestaurantMenuOrderPreviewService.PreviewData previewData,
            Map<String, CategoryTaxLine> usedCategoryTaxes,
            double subtotal,
            double serviceFee,
            double itemTax,
            double serviceFeeTax,
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
            if (line.selections() != null) {
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
            }
            if (!isBlank(line.instructions())) {
                summary.append("    Instructions: ").append(line.instructions().trim()).append('\n');
            }
        }
        summary.append('\n');
        summary.append("Subtotal: ").append(formatCurrency(subtotal)).append('\n');
        summary.append("Service Fee: ").append(formatCurrency(serviceFee)).append('\n');
        for (CategoryTaxLine categoryTax : usedCategoryTaxes.values()) {
            summary.append(formatTaxLabel(categoryTax.taxName(), categoryTax.ratePercent()))
                    .append(": ")
                    .append(formatCurrency(categoryTax.amount()))
                    .append('\n');
        }
        summary.append("Tax on fees (")
                .append(formatTaxLabel(previewData.serviceFeeTax().taxName(), previewData.serviceFeeTax().percentage()))
                .append("): ")
                .append(formatCurrency(serviceFeeTax))
                .append('\n');
        summary.append("Delivery Fee: ").append(formatCurrency(deliveryFee)).append('\n');
        summary.append("Delivery Fee Tax (")
                .append(formatTaxLabel(previewData.deliveryFeeTax().taxName(), previewData.deliveryFeeTax().percentage()))
                .append("): ")
                .append(formatCurrency(deliveryFeeTax))
                .append('\n');
        summary.append("Item Tax Total: ").append(formatCurrency(itemTax)).append('\n');
        if (isOnlinePayment(request.paymentMethod()) || tip > 0d) {
            summary.append("Tip: ").append(formatCurrency(tip)).append('\n');
        }
        summary.append("Total: ").append(formatCurrency(total)).append('\n');
        return summary.toString();
    }

    private String formatTaxLabel(String taxName, double taxRate) {
        String normalizedName = trimToEmpty(taxName);
        String rate = formatPercent(taxRate);
        if (normalizedName.isEmpty()) {
            return rate;
        }
        return normalizedName + " (" + rate + ")";
    }

    private String formatPercent(double percentage) {
        String value = String.format(java.util.Locale.CANADA, "%.2f", percentage).replaceAll("\\.?0+$", "");
        return value + "%";
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

    private record CategoryTaxLine(String category, String taxName, double ratePercent, double amount) {
    }

        public record SubmissionResult(
            Long stagedOrderId,
            ApprovalStatus status,
            Integer checkoutTimeoutMinutes,
            LocalDateTime approvalRequestedAt,
            Integer restaurantMinutesToCustomer,
            String message) {
        }

        public record OrderStatusSnapshot(
            Long stagedOrderId,
            String restaurantName,
            ApprovalStatus status,
            String statusReason,
            LocalDateTime approvalRequestedAt,
            LocalDateTime approvalDeadlineAt,
            int checkoutTimeoutMinutes,
            Integer driveMinutesToCustomer,
            Integer restaurantMinutesToCustomer,
            LocalDateTime approvedAt,
            LocalDateTime statusUpdatedAt) {
    }

    public record SubmitOrderRequest(
            Long restaurantId,
            String contactName,
            String contactEmail,
            String contactPhone,
            String street,
            String city,
            String postalCode,
            Double customerLatitude,
            Double customerLongitude,
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
