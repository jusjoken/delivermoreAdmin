package ca.admin.delivermore.data.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.admin.delivermore.collector.data.entity.OrderDetail;
import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder.ApprovalStatus;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLine;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLineOption;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.service.OrderDetailRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineOptionRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;

@Service
public class StagedRestaurantOrderWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(StagedRestaurantOrderWorkflowService.class);
    private static final String DECISION_EMAIL_SUBJECT_PREFIX = "Order decision: ";

    private final StagedRestaurantOrderRepository stagedRestaurantOrderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository;
    private final StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository;
    private final RestaurantRepository restaurantRepository;
    private final EmailService emailService;
    private final TabletOrderDispatchService tabletOrderDispatchService;
    private final CustomerProfileService customerProfileService;

    @Value("${app.orders.support-email}")
    private String supportEmail;

    public StagedRestaurantOrderWorkflowService(
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            OrderDetailRepository orderDetailRepository,
            StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository,
            StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository,
            RestaurantRepository restaurantRepository,
            EmailService emailService,
            TabletOrderDispatchService tabletOrderDispatchService,
            CustomerProfileService customerProfileService) {
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.stagedRestaurantOrderLineRepository = stagedRestaurantOrderLineRepository;
        this.stagedRestaurantOrderLineOptionRepository = stagedRestaurantOrderLineOptionRepository;
        this.restaurantRepository = restaurantRepository;
        this.emailService = emailService;
        this.tabletOrderDispatchService = tabletOrderDispatchService;
        this.customerProfileService = customerProfileService;
    }

    public List<StagedRestaurantOrder> listPendingApprovals() {
        return stagedRestaurantOrderRepository.findByApprovalStatusOrderBySubmittedAtDesc(ApprovalStatus.PENDING_APPROVAL);
    }

    public List<StagedRestaurantOrder> listAll() {
        return stagedRestaurantOrderRepository.findAllByOrderBySubmittedAtDesc();
    }

    @Transactional(readOnly = true)
    public boolean isTookanSendEnabled(StagedRestaurantOrder stagedOrder) {
        Restaurant restaurant = findEffectiveRestaurant(stagedOrder);
        return restaurant != null && restaurant.getSendToTookan();
    }

    @Transactional
    public StagedRestaurantOrder accept(Long stagedOrderId) {
        return accept(stagedOrderId, null);
    }

    @Transactional
    public StagedRestaurantOrder accept(Long stagedOrderId, Integer restaurantMinutesToCustomer) {
        StagedRestaurantOrder stagedOrder = getById(stagedOrderId);
        ensureCanTransition(stagedOrder, "accept");

        OrderDetail orderDetail = buildOrderDetail(stagedOrder);
        orderDetailRepository.save(orderDetail);

        saveAcceptedCustomerProfile(stagedOrder);

        if (restaurantMinutesToCustomer != null) {
            stagedOrder.setRestaurantMinutesToCustomer(Math.max(1, restaurantMinutesToCustomer));
        }

        LocalDateTime now = LocalDateTime.now();
        stagedOrder.setApprovalStatus(ApprovalStatus.APPROVED);
        stagedOrder.setApprovedAt(now);
        stagedOrder.setStatusUpdatedAt(now);
        stagedOrder.setOrderDetailId(orderDetail.getOrderId());
        stagedOrder.setStatusReason(null);
        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        tabletOrderDispatchService.dispatchOrderStatusChangedPush(stagedOrder);
        maybeQueueTookanSubmission(stagedOrder);
        return stagedOrder;
    }

    public StagedRestaurantOrder approve(Long stagedOrderId) {
        return accept(stagedOrderId);
    }

    @Transactional
    public StagedRestaurantOrder reject(Long stagedOrderId, String reason) {
        StagedRestaurantOrder stagedOrder = getById(stagedOrderId);
        ensureCanTransition(stagedOrder, "reject");
        stagedOrder.setStatusReason(requireReason(reason, "Reject reason is required."));

        stagedOrder.setApprovalStatus(ApprovalStatus.DECLINED);
        stagedOrder.setStatusUpdatedAt(LocalDateTime.now());
        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        tabletOrderDispatchService.dispatchOrderStatusChangedPush(stagedOrder);
        sendDecisionEmailIfConfigured(stagedOrder);
        return stagedRestaurantOrderRepository.save(stagedOrder);
    }

    public StagedRestaurantOrder decline(Long stagedOrderId, String reason) {
        return reject(stagedOrderId, reason);
    }

    @Transactional
    public StagedRestaurantOrder cancel(Long stagedOrderId, String reason) {
        StagedRestaurantOrder stagedOrder = getById(stagedOrderId);
        ensureCanTransition(stagedOrder, "cancel");

        stagedOrder.setApprovalStatus(ApprovalStatus.CANCELED);
        stagedOrder.setStatusUpdatedAt(LocalDateTime.now());
        stagedOrder.setStatusReason(optionalReason(reason));
        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        tabletOrderDispatchService.dispatchOrderStatusChangedPush(stagedOrder);
        sendDecisionEmailIfConfigured(stagedOrder);
        return stagedRestaurantOrderRepository.save(stagedOrder);
    }

    @Transactional
    public StagedRestaurantOrder markMissed(Long stagedOrderId, String reason) {
        StagedRestaurantOrder stagedOrder = getById(stagedOrderId);
        ensureCanTransition(stagedOrder, "mark missed");

        stagedOrder.setApprovalStatus(ApprovalStatus.MISSED);
        String missedReason = optionalReason(reason);
        stagedOrder.setStatusReason(missedReason == null ? "No answer before checkout timeout." : missedReason);
        stagedOrder.setStatusUpdatedAt(LocalDateTime.now());
        stagedOrder = stagedRestaurantOrderRepository.save(stagedOrder);
        tabletOrderDispatchService.dispatchOrderStatusChangedPush(stagedOrder);
        sendDecisionEmailIfConfigured(stagedOrder);
        return stagedOrder;
    }

    public String displayApprovalStatus(ApprovalStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case APPROVED -> "Accepted";
            case DECLINED -> "Rejected";
            case CANCELED -> "Cancelled";
            case MISSED -> "Missed";
            case PENDING_APPROVAL -> "Pending";
        };
    }

    private StagedRestaurantOrder getById(Long stagedOrderId) {
        return stagedRestaurantOrderRepository.findById(stagedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Staged order not found: " + stagedOrderId));
    }

    private void ensureCanTransition(StagedRestaurantOrder stagedOrder, String action) {
        if (stagedOrder.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot " + action + " order #" + stagedOrder.getId()
                    + " because it is " + stagedOrder.getApprovalStatus());
        }
    }

    private String requireReason(String reason, String errorMessage) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return reason.trim();
    }

    private String optionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    private void sendDecisionEmailIfConfigured(StagedRestaurantOrder stagedOrder) {
        if (supportEmail == null || supportEmail.isBlank()) {
            return;
        }

        try {
            emailService.sendMailWithHtmlBody(
                    supportEmail,
                    DECISION_EMAIL_SUBJECT_PREFIX + stagedOrder.getRestaurantName() + " #" + stagedOrder.getId(),
                    buildDecisionEmailBody(stagedOrder));
            stagedOrder.setSupportEmailSentAt(LocalDateTime.now());
        } catch (RuntimeException ex) {
            log.error("Failed to send staged order decision email for order {}", stagedOrder.getId(), ex);
        }
    }

    private String buildDecisionEmailBody(StagedRestaurantOrder stagedOrder) {
        return "<p>A pending approval order has been " + stagedOrder.getApprovalStatus().name().toLowerCase()
                + ".</p>"
                + "<p><strong>Order #</strong> " + stagedOrder.getId() + "<br>"
                + "<strong>Restaurant:</strong> " + escapeHtml(stagedOrder.getRestaurantName()) + "<br>"
                + "<strong>Status:</strong> " + escapeHtml(stagedOrder.getApprovalStatus().name()) + "<br>"
                + "<strong>Reason:</strong> " + escapeHtml(optionalText(stagedOrder.getStatusReason())) + "</p>"
                + "<pre style=\"font-family:monospace;white-space:pre-wrap;\">"
                + escapeHtml(stagedOrder.getOrderSummary())
                + "</pre>";
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return "No reason provided.";
        }
        return value;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private OrderDetail buildOrderDetail(StagedRestaurantOrder stagedOrder) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(generateOrderDetailId());
        orderDetail.setRestaurantId(stagedOrder.getRestaurantId());
        orderDetail.setSubtotal(defaultDouble(stagedOrder.getSubtotal()));
        orderDetail.setDeliveryFee(defaultDouble(stagedOrder.getDeliveryFee()));
        orderDetail.setServiceFee(defaultDouble(stagedOrder.getServiceFee()));
        orderDetail.setTaxOnFees(defaultDouble(stagedOrder.getDeliveryFeeTax()));
        orderDetail.setTotalTaxes(defaultDouble(stagedOrder.getItemTax())
            + defaultDouble(stagedOrder.getServiceFeeTax())
                + defaultDouble(stagedOrder.getDeliveryFeeTax()));
        orderDetail.setTotal(defaultDouble(stagedOrder.getTotal()));
        orderDetail.setPaymentMethod(normalizePaymentMethod(stagedOrder.getPaymentMethod()));
        orderDetail.setOrderType("delivery");
        orderDetail.setSource(OrderDetail.Source.DM);
        orderDetail.setTip(defaultDouble(stagedOrder.getTip()));
        orderDetail.setJsonSourceId(0L);
        orderDetail.setClientName(stagedOrder.getContactName());
        orderDetail.setOrderText(buildDriverOrderText(stagedOrder.getId()));
        return orderDetail;
    }

    private void saveAcceptedCustomerProfile(StagedRestaurantOrder stagedOrder) {
        try {
            customerProfileService.recordCheckoutOrder(
                    stagedOrder.getRestaurantId(),
                    stagedOrder.getRestaurantName(),
                    stagedOrder.getContactName(),
                    stagedOrder.getContactEmail(),
                    stagedOrder.getContactPhone(),
                    stagedOrder.getStreetAddress(),
                    stagedOrder.getCity(),
                    stagedOrder.getPostalCode(),
                    stagedOrder.getCustomerLatitude(),
                    stagedOrder.getCustomerLongitude(),
                    defaultDouble(stagedOrder.getTotal()),
                    stagedOrder.getApprovedAt() == null ? LocalDateTime.now() : stagedOrder.getApprovedAt());
        } catch (RuntimeException ex) {
            log.warn("Unable to record accepted customer profile for staged order {}: {}", stagedOrder.getId(), ex.getMessage());
        }
    }

    private String buildDriverOrderText(Long stagedOrderId) {
        List<StagedRestaurantOrderLine> lines =
                stagedRestaurantOrderLineRepository.findByStagedOrderIdOrderByLineNumberAsc(stagedOrderId);
        List<StagedRestaurantOrderLineOption> options =
                stagedRestaurantOrderLineOptionRepository.findByStagedOrderIdOrderByLineNumberAscIdAsc(stagedOrderId);

        Map<Long, List<StagedRestaurantOrderLineOption>> optionsByLineId = new HashMap<>();
        for (StagedRestaurantOrderLineOption option : options) {
            optionsByLineId.computeIfAbsent(option.getStagedOrderLineId(), ignored -> new ArrayList<>()).add(option);
        }

        StringBuilder orderText = new StringBuilder();
        String crlf = "\r\n";
        String indent = "  - ";

        for (StagedRestaurantOrderLine line : lines) {
            if (!orderText.isEmpty()) {
                orderText.append(crlf);
            }

            orderText.append(defaultInt(line.getQuantity()))
                    .append(" x ")
                    .append(safeText(line.getItemName()))
                    .append(crlf);

            if (!isBlank(line.getSelectedSizeName())) {
                orderText.append(indent)
                        .append("Size - ")
                        .append(line.getSelectedSizeName().trim())
                        .append(crlf);
            }

            if (!isBlank(line.getInstructions())) {
                String info = line.getInstructions().trim().replace("\n", "\n    ");
                orderText.append(indent)
                        .append("Info: ")
                        .append(info)
                        .append(crlf);
            }

            for (StagedRestaurantOrderLineOption option : optionsByLineId.getOrDefault(line.getId(), List.of())) {
                if (defaultInt(option.getQuantity()) > 1) {
                    orderText.append(indent)
                            .append(option.getQuantity())
                            .append(" x ");
                } else {
                    orderText.append(indent);
                }
                orderText.append(safeText(option.getGroupName()))
                        .append(" - ")
                        .append(safeText(option.getOptionName()))
                        .append(crlf);
            }
        }

        return orderText.toString();
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "CASH";
        }
        return paymentMethod.trim().toUpperCase();
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Long generateOrderDetailId() {
        long candidate = System.currentTimeMillis();
        while (orderDetailRepository.existsById(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private void maybeQueueTookanSubmission(StagedRestaurantOrder stagedOrder) {
        Restaurant restaurant = findEffectiveRestaurant(stagedOrder);

        if (restaurant == null || !restaurant.getSendToTookan()) {
            return;
        }

        // TODO: implement Tookan task submission when integration is completed.
        log.info("approve: sendToTookan is enabled for stagedOrderId={} restaurantId={}; Tookan submission is pending implementation",
                stagedOrder.getId(), stagedOrder.getRestaurantId());
    }

    private Restaurant findEffectiveRestaurant(StagedRestaurantOrder stagedOrder) {
        if (stagedOrder == null || stagedOrder.getRestaurantId() == null) {
            return null;
        }

        LocalDate effectiveDate = stagedOrder.getSubmittedAt() == null
                ? LocalDate.now()
                : stagedOrder.getSubmittedAt().toLocalDate();

        return restaurantRepository.findEffectiveByRestaurantId(
                stagedOrder.getRestaurantId(),
                effectiveDate).stream().findFirst().orElse(null);
    }
}