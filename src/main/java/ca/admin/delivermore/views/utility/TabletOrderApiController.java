package ca.admin.delivermore.views.utility;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder.ApprovalStatus;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.service.StagedRestaurantOrderPayloadProjectionService;
import ca.admin.delivermore.data.service.StagedRestaurantOrderWorkflowService;
import ca.admin.delivermore.data.service.TabletApiAccessService;
import ca.admin.delivermore.data.service.TabletApiAccessService.TabletApiAccessException;
import ca.admin.delivermore.data.service.TabletOrderDispatchService;

@RestController
@RequestMapping("/api/tablet/orders")
public class TabletOrderApiController {

    private static final String ASSET_TAG_HEADER = "X-Tablet-Asset-Tag";
    private static final String API_KEY_HEADER = "X-Tablet-Api-Key";

    private final TabletApiAccessService tabletApiAccessService;
    private final StagedRestaurantOrderRepository stagedRestaurantOrderRepository;
    private final StagedRestaurantOrderWorkflowService stagedRestaurantOrderWorkflowService;
    private final StagedRestaurantOrderPayloadProjectionService payloadProjectionService;
    private final TabletOrderDispatchService tabletOrderDispatchService;

    public TabletOrderApiController(
            TabletApiAccessService tabletApiAccessService,
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            StagedRestaurantOrderWorkflowService stagedRestaurantOrderWorkflowService,
            StagedRestaurantOrderPayloadProjectionService payloadProjectionService,
            TabletOrderDispatchService tabletOrderDispatchService) {
        this.tabletApiAccessService = tabletApiAccessService;
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.stagedRestaurantOrderWorkflowService = stagedRestaurantOrderWorkflowService;
        this.payloadProjectionService = payloadProjectionService;
        this.tabletOrderDispatchService = tabletOrderDispatchService;
    }

    @PostMapping("/register-token")
    public GenericResult registerToken(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody TokenRegistrationRequest request) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        if (request == null || request.registrationToken() == null || request.registrationToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "registrationToken is required");
        }

        tabletOrderDispatchService.registerFcmToken(tabletAsset.getAssetTag(), request.registrationToken());
        return new GenericResult("token_registered", LocalDateTime.now());
    }

    @PostMapping("/heartbeat")
    public GenericResult heartbeat(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody(required = false) HeartbeatRequest request) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        tabletOrderDispatchService.recordHeartbeat(
                tabletAsset.getAssetTag(),
                request == null ? null : request.appVersion());
        return new GenericResult("heartbeat_recorded", LocalDateTime.now());
    }

    @GetMapping("/pending")
    public List<PendingOrderSummary> listPendingOrders(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        int boundedLimit = Math.max(1, Math.min(limit, 100));

        return stagedRestaurantOrderRepository
                .findByApprovalStatusOrderBySubmittedAtDesc(ApprovalStatus.PENDING_APPROVAL)
                .stream()
                .filter(order -> order.getRestaurantId() != null
                        && order.getRestaurantId().equals(tabletAsset.getRestaurantId()))
                .limit(boundedLimit)
                .map(order -> new PendingOrderSummary(
                        order.getId(),
                        order.getRestaurantId(),
                        order.getRestaurantName(),
                        order.getContactName(),
                        order.getSubmittedAt(),
                        order.getTotal(),
                        order.getApprovalStatus(),
                        order.getCustomerComments() != null && !order.getCustomerComments().isBlank()))
                .toList();
    }

    @GetMapping(value = "/{stagedOrderId}/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getOrderPayload(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long stagedOrderId) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        StagedRestaurantOrder stagedOrder = getAccessibleOrderOrThrow(tabletAsset, stagedOrderId);

        try {
            String payload = payloadProjectionService.buildPayloadJson(stagedOrder.getId());
            tabletOrderDispatchService.markPayloadPulled(stagedOrder.getId(), tabletAsset.getAssetTag());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(payload);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{stagedOrderId}/ack")
    public GenericResult acknowledgeOrderNotification(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long stagedOrderId) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        getAccessibleOrderOrThrow(tabletAsset, stagedOrderId);
        tabletOrderDispatchService.acknowledgePush(stagedOrderId, tabletAsset.getAssetTag());
        return new GenericResult("acknowledged", LocalDateTime.now());
    }

    @PostMapping("/{stagedOrderId}/approve")
    public DecisionResult approveOrder(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long stagedOrderId) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        getAccessibleOrderOrThrow(tabletAsset, stagedOrderId);
        try {
            StagedRestaurantOrder saved = stagedRestaurantOrderWorkflowService.approve(stagedOrderId);
            return new DecisionResult(saved.getId(), saved.getApprovalStatus(), saved.getStatusReason(), saved.getStatusUpdatedAt());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{stagedOrderId}/decline")
    public DecisionResult declineOrder(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long stagedOrderId,
            @RequestBody(required = false) DecisionRequest request) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        getAccessibleOrderOrThrow(tabletAsset, stagedOrderId);
        try {
            StagedRestaurantOrder saved = stagedRestaurantOrderWorkflowService.decline(
                    stagedOrderId,
                    request == null ? null : request.reason());
            return new DecisionResult(saved.getId(), saved.getApprovalStatus(), saved.getStatusReason(), saved.getStatusUpdatedAt());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{stagedOrderId}/cancel")
    public DecisionResult cancelOrder(
            @RequestHeader(name = ASSET_TAG_HEADER, required = false) String assetTag,
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @PathVariable Long stagedOrderId,
            @RequestBody(required = false) DecisionRequest request) {
        TabletAsset tabletAsset = authorizeTablet(assetTag, apiKey);
        getAccessibleOrderOrThrow(tabletAsset, stagedOrderId);
        try {
            StagedRestaurantOrder saved = stagedRestaurantOrderWorkflowService.cancel(
                    stagedOrderId,
                    request == null ? null : request.reason());
            return new DecisionResult(saved.getId(), saved.getApprovalStatus(), saved.getStatusReason(), saved.getStatusUpdatedAt());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    private TabletAsset authorizeTablet(String assetTag, String apiKey) {
        try {
            return tabletApiAccessService.authorize(assetTag, apiKey);
        } catch (TabletApiAccessException ex) {
            if (ex.getReason() == TabletApiAccessException.Reason.SERVER_NOT_CONFIGURED) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
            }
            if (ex.getReason() == TabletApiAccessException.Reason.NOT_ASSIGNED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    private StagedRestaurantOrder getAccessibleOrderOrThrow(TabletAsset tabletAsset, Long stagedOrderId) {
        StagedRestaurantOrder stagedOrder = stagedRestaurantOrderRepository.findById(stagedOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staged order not found."));

        if (stagedOrder.getRestaurantId() == null || !stagedOrder.getRestaurantId().equals(tabletAsset.getRestaurantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Staged order not found.");
        }

        return stagedOrder;
    }

    public record PendingOrderSummary(
            Long stagedOrderId,
            Long restaurantId,
            String restaurantName,
            String contactName,
            LocalDateTime submittedAt,
            Double total,
            ApprovalStatus approvalStatus,
            boolean hasCustomerComments) {
    }

    public record DecisionRequest(String reason) {
    }

    public record TokenRegistrationRequest(String registrationToken) {
    }

    public record HeartbeatRequest(String appVersion) {
    }

    public record GenericResult(String status, LocalDateTime serverTime) {
    }

    public record DecisionResult(
            Long stagedOrderId,
            ApprovalStatus approvalStatus,
            String statusReason,
            LocalDateTime statusUpdatedAt) {
    }
}