package ca.admin.delivermore.data.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.entity.TabletOrderDispatch;
import jakarta.annotation.PostConstruct;

@Service
public class TabletOrderDispatchService {

    private static final String FIREBASE_APP_NAME = "dm-tablet-push";
    private static final Logger log = LoggerFactory.getLogger(TabletOrderDispatchService.class);

    private final TabletAssetRepository tabletAssetRepository;
    private final TabletOrderDispatchRepository tabletOrderDispatchRepository;
    private final RestaurantRepository restaurantRepository;
    private final EmailService emailService;
    private FirebaseMessaging firebaseMessaging;

    @Value("${app.orders.support-email:}")
    private String supportEmail;

    @Value("${app.tablet.push.max-failures-before-email:3}")
    private int maxFailuresBeforeEmail;

    @Value("${app.tablet.push.max-attempts-per-order:3}")
    private int maxAttemptsPerOrder;

    @Value("${app.tablet.push.fcm.enabled:false}")
    private boolean tabletPushEnabled;

    @Value("${app.tablet.push.fcm.project-id:}")
    private String firebaseProjectId;

    @Value("${app.tablet.push.fcm.credentials-path:}")
    private String firebaseCredentialsPath;

    public TabletOrderDispatchService(
            TabletAssetRepository tabletAssetRepository,
            TabletOrderDispatchRepository tabletOrderDispatchRepository,
            RestaurantRepository restaurantRepository,
            EmailService emailService) {
        this.tabletAssetRepository = tabletAssetRepository;
        this.tabletOrderDispatchRepository = tabletOrderDispatchRepository;
        this.restaurantRepository = restaurantRepository;
        this.emailService = emailService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logFirebasePushStartupState() {
        log.warn(
                "logFirebasePushStartupState: enabled={} projectIdConfigured={} credentialsPathConfigured={}",
                tabletPushEnabled,
                StringUtils.hasText(firebaseProjectId),
                StringUtils.hasText(firebaseCredentialsPath));

        if (!tabletPushEnabled) {
            log.info("logFirebasePushStartupState: tablet push is disabled");
            return;
        }

        if (!StringUtils.hasText(firebaseProjectId)) {
            log.warn("logFirebasePushStartupState: Firebase project id is missing");
            return;
        }

        if (!StringUtils.hasText(firebaseCredentialsPath)) {
            log.warn("logFirebasePushStartupState: Firebase credentials path is missing");
            return;
        }

        log.info("logFirebasePushStartupState: Firebase push configuration is present; initializing now");
        initializeFirebaseMessaging();
    }

    @PostConstruct
    public void initializeFirebaseMessaging() {
        if (!tabletPushEnabled) {
            log.info("initializeFirebaseMessaging: tablet push is disabled");
            return;
        }

        if (!StringUtils.hasText(firebaseProjectId)) {
            log.warn("initializeFirebaseMessaging: missing Firebase project id configuration");
            return;
        }

        if (!StringUtils.hasText(firebaseCredentialsPath)) {
            log.warn("initializeFirebaseMessaging: missing Firebase credentials path configuration");
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(firebaseCredentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);

            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> FIREBASE_APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(
                            FirebaseOptions.builder()
                                    .setCredentials(credentials)
                                    .setProjectId(firebaseProjectId)
                                    .build(),
                            FIREBASE_APP_NAME));

            firebaseMessaging = FirebaseMessaging.getInstance(app);
            log.info("initializeFirebaseMessaging: Firebase messaging ready for project {}", firebaseProjectId);
        } catch (IOException ex) {
            log.error("initializeFirebaseMessaging: failed to initialize Firebase messaging: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public void dispatchOrderPush(StagedRestaurantOrder stagedOrder) {
        if (stagedOrder == null || stagedOrder.getId() == null) {
            return;
        }

        if (!isRestaurantTabletSendingEnabled(stagedOrder)) {
            log.info("dispatchOrderPush: skipped stagedOrderId={} because sendToTablet is disabled for restaurantId={}",
                    stagedOrder.getId(), stagedOrder.getRestaurantId());
            return;
        }

        TabletOrderDispatch dispatch = tabletOrderDispatchRepository.findByStagedOrderId(stagedOrder.getId())
                .orElseGet(() -> createDispatch(stagedOrder));

        Optional<TabletAsset> tabletAssetOptional = tabletAssetRepository
                .findFirstByRestaurantIdAndArchivedFalse(stagedOrder.getRestaurantId());
        if (tabletAssetOptional.isEmpty()) {
            recordFailure(dispatch, "No active tablet asset assigned for restaurant " + stagedOrder.getRestaurantId());
            return;
        }

        TabletAsset tabletAsset = tabletAssetOptional.get();
        dispatch.setTabletAssetId(tabletAsset.getId());
        dispatch.setTabletAssetTag(tabletAsset.getAssetTag());

        if (!tabletPushEnabled) {
            recordFailure(dispatch, "Tablet push is disabled by configuration");
            return;
        }

        if (firebaseMessaging == null) {
            recordFailure(dispatch, "Firebase messaging is not initialized");
            return;
        }

        if (!StringUtils.hasText(tabletAsset.getFcmRegistrationToken())) {
            recordFailure(dispatch, "Tablet FCM registration token missing for asset " + tabletAsset.getAssetTag());
            return;
        }

        int attempts = Math.max(1, maxAttemptsPerOrder);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            LocalDateTime now = LocalDateTime.now();
            dispatch.setLastAttemptedAt(now);

            try {
                Message message = Message.builder()
                        .setToken(tabletAsset.getFcmRegistrationToken())
                        .putData("type", "dm_new_order")
                        .putData("stagedOrderId", String.valueOf(stagedOrder.getId()))
                        .putData("restaurantId", String.valueOf(stagedOrder.getRestaurantId()))
                        .putData("restaurantName", String.valueOf(stagedOrder.getRestaurantName()))
                        .putData("assetTag", String.valueOf(tabletAsset.getAssetTag()))
                        .build();

                String messageId = firebaseMessaging.send(message);
                dispatch.setPushSentAt(now);
                dispatch.setPushFailedAt(null);
                dispatch.setLastFailureReason(null);
                dispatch.setFailureCount(0);
                tabletOrderDispatchRepository.save(dispatch);
                log.info("dispatchOrderPush: pushed stagedOrderId={} to tablet asset={} attempt={} messageId={}",
                        stagedOrder.getId(), tabletAsset.getAssetTag(), attempt, messageId);
                return;
            } catch (FirebaseMessagingException ex) {
                recordFailure(dispatch, "FCM send failed attempt=" + attempt + ": " + summarizeFirebaseError(ex));
            }
        }
    }

    @Transactional
    public void dispatchOrderStatusChangedPush(StagedRestaurantOrder stagedOrder) {
        if (stagedOrder == null || stagedOrder.getId() == null) {
            return;
        }

        if (!isRestaurantTabletSendingEnabled(stagedOrder)) {
            log.info("dispatchOrderStatusChangedPush: skipped stagedOrderId={} because sendToTablet is disabled for restaurantId={}",
                    stagedOrder.getId(), stagedOrder.getRestaurantId());
            return;
        }

        Optional<TabletAsset> tabletAssetOptional = tabletAssetRepository
                .findFirstByRestaurantIdAndArchivedFalse(stagedOrder.getRestaurantId());
        if (tabletAssetOptional.isEmpty()) {
            log.warn("dispatchOrderStatusChangedPush: no active tablet asset assigned for restaurantId={} stagedOrderId={}",
                    stagedOrder.getRestaurantId(), stagedOrder.getId());
            return;
        }

        TabletAsset tabletAsset = tabletAssetOptional.get();

        if (!tabletPushEnabled) {
            log.info("dispatchOrderStatusChangedPush: skipped because tablet push is disabled by configuration");
            return;
        }

        if (firebaseMessaging == null) {
            log.warn("dispatchOrderStatusChangedPush: Firebase messaging is not initialized");
            return;
        }

        if (!StringUtils.hasText(tabletAsset.getFcmRegistrationToken())) {
            log.warn("dispatchOrderStatusChangedPush: missing FCM token for asset={} stagedOrderId={}",
                    tabletAsset.getAssetTag(), stagedOrder.getId());
            return;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(tabletAsset.getFcmRegistrationToken())
                    .putData("type", "dm_order_status_changed")
                    .putData("stagedOrderId", String.valueOf(stagedOrder.getId()))
                    .putData("restaurantId", String.valueOf(stagedOrder.getRestaurantId()))
                    .putData("restaurantName", String.valueOf(stagedOrder.getRestaurantName()))
                    .putData("assetTag", String.valueOf(tabletAsset.getAssetTag()))
                    .putData("approvalStatus", String.valueOf(stagedOrder.getApprovalStatus()));

            if (stagedOrder.getStatusReason() != null) {
                messageBuilder.putData("statusReason", stagedOrder.getStatusReason());
            }
            if (stagedOrder.getStatusUpdatedAt() != null) {
                messageBuilder.putData("statusUpdatedAt", stagedOrder.getStatusUpdatedAt().toString());
            }

            String messageId = firebaseMessaging.send(messageBuilder.build());
            log.info("dispatchOrderStatusChangedPush: pushed stagedOrderId={} status={} to tablet asset={} messageId={}",
                    stagedOrder.getId(), stagedOrder.getApprovalStatus(), tabletAsset.getAssetTag(), messageId);
        } catch (FirebaseMessagingException ex) {
            log.warn("dispatchOrderStatusChangedPush: failed stagedOrderId={} status={} reason={}",
                    stagedOrder.getId(), stagedOrder.getApprovalStatus(), summarizeFirebaseError(ex));
        }
    }

    @Transactional
    public void registerFcmToken(String assetTag, String registrationToken) {
        if (!StringUtils.hasText(assetTag) || !StringUtils.hasText(registrationToken)) {
            return;
        }

        tabletAssetRepository.updateTokenByAssetTag(
                assetTag.trim(),
                registrationToken.trim(),
                LocalDateTime.now());
    }

    @Transactional
    public void recordHeartbeat(String assetTag, String appVersion) {
        if (!StringUtils.hasText(assetTag)) {
            return;
        }

        tabletAssetRepository.updateHeartbeatByAssetTag(
                assetTag.trim(),
                LocalDateTime.now(),
                StringUtils.hasText(appVersion) ? appVersion.trim() : null);
    }

    @Transactional
    public void acknowledgePush(Long stagedOrderId, String assetTag) {
        if (stagedOrderId == null || !StringUtils.hasText(assetTag)) {
            return;
        }

        TabletOrderDispatch dispatch = tabletOrderDispatchRepository.findByStagedOrderId(stagedOrderId).orElse(null);
        if (dispatch == null) {
            return;
        }

        if (StringUtils.hasText(dispatch.getTabletAssetTag())
                && !dispatch.getTabletAssetTag().equalsIgnoreCase(assetTag.trim())) {
            return;
        }

        dispatch.setAcknowledgedAt(LocalDateTime.now());
        tabletOrderDispatchRepository.save(dispatch);
    }

    @Transactional
    public void markPayloadPulled(Long stagedOrderId, String assetTag) {
        if (stagedOrderId == null || !StringUtils.hasText(assetTag)) {
            return;
        }

        TabletOrderDispatch dispatch = tabletOrderDispatchRepository.findByStagedOrderId(stagedOrderId).orElse(null);
        if (dispatch == null) {
            return;
        }

        if (StringUtils.hasText(dispatch.getTabletAssetTag())
                && !dispatch.getTabletAssetTag().equalsIgnoreCase(assetTag.trim())) {
            return;
        }

        dispatch.setPayloadPulledAt(LocalDateTime.now());
        tabletOrderDispatchRepository.save(dispatch);
    }

    private TabletOrderDispatch createDispatch(StagedRestaurantOrder stagedOrder) {
        TabletOrderDispatch dispatch = new TabletOrderDispatch();
        dispatch.setStagedOrderId(stagedOrder.getId());
        dispatch.setRestaurantId(stagedOrder.getRestaurantId());
        dispatch.setRestaurantName(stagedOrder.getRestaurantName());
        dispatch.setRequestedAt(LocalDateTime.now());
        return tabletOrderDispatchRepository.save(dispatch);
    }

    private void recordFailure(TabletOrderDispatch dispatch, String reason) {
        LocalDateTime now = LocalDateTime.now();
        dispatch.setLastAttemptedAt(now);
        dispatch.setPushFailedAt(now);
        dispatch.setLastFailureReason(reason);
        dispatch.setFailureCount(dispatch.getFailureCount() + 1);
        tabletOrderDispatchRepository.save(dispatch);

        log.warn("dispatchOrderPush failure stagedOrderId={} count={} reason={}", dispatch.getStagedOrderId(),
                dispatch.getFailureCount(), reason);

        maybeNotifySupport(dispatch);
    }

    private void maybeNotifySupport(TabletOrderDispatch dispatch) {
        if (!StringUtils.hasText(supportEmail)) {
            return;
        }

        if (dispatch.getFailureCount() < Math.max(1, maxFailuresBeforeEmail)) {
            return;
        }

        if (dispatch.getSupportEmailNotifiedAt() != null) {
            return;
        }

        String subject = "DeliverMore tablet order push failing repeatedly";
        String body = """
            Repeated push failures were detected.

            Restaurant: %s (%s)
            Tablet asset tag: %s
            Staged order id: %s
            Failure count: %s
            Last failure: %s
            Last attempted at: %s
            """.formatted(
            dispatch.getRestaurantName(),
            dispatch.getRestaurantId(),
            dispatch.getTabletAssetTag(),
            dispatch.getStagedOrderId(),
            dispatch.getFailureCount(),
            dispatch.getLastFailureReason(),
            dispatch.getLastAttemptedAt());

        try {
            emailService.sendMail(supportEmail, subject, body);
            dispatch.setSupportEmailNotifiedAt(LocalDateTime.now());
            tabletOrderDispatchRepository.save(dispatch);
        } catch (RuntimeException ex) {
            log.error("maybeNotifySupport failed for stagedOrderId={}: {}", dispatch.getStagedOrderId(),
                    ex.getMessage(), ex);
        }
    }

    private String summarizeFirebaseError(FirebaseMessagingException ex) {
        StringBuilder summary = new StringBuilder();
        if (ex.getMessagingErrorCode() != null) {
            summary.append(ex.getMessagingErrorCode());
        }
        if (StringUtils.hasText(ex.getMessage())) {
            if (summary.length() > 0) {
                summary.append(" - ");
            }
            summary.append(ex.getMessage());
        }
        return summary.length() == 0 ? "unknown Firebase error" : summary.toString();
    }

    private boolean isRestaurantTabletSendingEnabled(StagedRestaurantOrder stagedOrder) {
        if (stagedOrder.getRestaurantId() == null) {
            return false;
        }

        LocalDate effectiveDate = stagedOrder.getSubmittedAt() == null
                ? LocalDate.now()
                : stagedOrder.getSubmittedAt().toLocalDate();

        return restaurantRepository.findEffectiveByRestaurantId(stagedOrder.getRestaurantId(), effectiveDate)
                .stream()
                .findFirst()
                .map(Restaurant::getSendToTablet)
                .orElse(false);
    }
}