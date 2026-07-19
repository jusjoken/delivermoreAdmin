package ca.admin.delivermore.data.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import ca.admin.delivermore.collector.data.service.EmailService;
import ca.admin.delivermore.data.entity.TabletAsset;

@Service
public class TabletProvisioningService {

    private static final int DEFAULT_EXPIRY_MINUTES = 12 * 60;
    private static final int QR_SIZE_PX = 760;

    private final TabletAssetRepository tabletAssetRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private final String configuredBaseUrl;
    private final String configuredProvisioningEmail;
    private final String tabletApiKey;
    private final int expiryMinutes;

    public TabletProvisioningService(
            TabletAssetRepository tabletAssetRepository,
            EmailService emailService,
            @Value("${app.tablet.provisioning.base-url:}") String configuredBaseUrl,
            @Value("${app.tablet.provisioning.email:}") String configuredProvisioningEmail,
            @Value("${app.tablet.api-key:}") String tabletApiKey,
            @Value("${app.tablet.provisioning.qr-expiry-minutes:720}") int expiryMinutes) {
        this.tabletAssetRepository = tabletAssetRepository;
        this.emailService = emailService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.configuredBaseUrl = normalizeBaseUrl(configuredBaseUrl);
        this.configuredProvisioningEmail = configuredProvisioningEmail == null ? "" : configuredProvisioningEmail.trim();
        this.tabletApiKey = tabletApiKey == null ? "" : tabletApiKey.trim();
        this.expiryMinutes = Math.max(5, expiryMinutes <= 0 ? DEFAULT_EXPIRY_MINUTES : expiryMinutes);
    }

    @Transactional
    public ProvisioningQrPackage issueProvisioningQr(Long assetId, String requestedBy, String runtimeBaseUrl) {
        TabletAsset asset = tabletAssetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Tablet asset not found"));

        if (asset.isArchived()) {
            throw new IllegalStateException("Archived assets cannot be provisioned");
        }
        if (asset.getRestaurantId() == null || !StringUtils.hasText(asset.getRestaurantName())) {
            throw new IllegalStateException("Assign a restaurant before generating a provisioning QR");
        }
        if (!StringUtils.hasText(tabletApiKey)) {
            throw new IllegalStateException("Tablet API key is not configured on the server");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(expiryMinutes);
        String nonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        asset.setProvisioningNonce(nonce);
        asset.setProvisioningExpiresAt(expiresAt);
        tabletAssetRepository.save(asset);

        String baseUrl = resolveBaseUrl(runtimeBaseUrl);
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Tablet provisioning base URL is not configured");
        }

        String claimPath = "api/tablet/provision/claim";
        String claimUrl = baseUrl + "/" + claimPath;
        String payloadJson = buildPayloadJson(baseUrl, claimPath, asset.getAssetTag());
        byte[] qrPng = renderQrPng(payloadJson);

        return new ProvisioningQrPackage(
                asset.getId(),
                asset.getAssetTag(),
                asset.getAssetName(),
                asset.getRestaurantName(),
                claimUrl,
                nonce,
                expiresAt,
                payloadJson,
                qrPng,
                configuredProvisioningEmail,
                requestedBy == null ? "" : requestedBy.trim());
    }

    @Transactional
    public ProvisioningClaimResult claimProvisioning(String nonce, String runtimeBaseUrl, String appVersion) {
        if (!StringUtils.hasText(nonce)) {
            throw new IllegalArgumentException("Provisioning nonce is required");
        }

        TabletAsset asset = tabletAssetRepository.findFirstByProvisioningNonceAndArchivedFalse(nonce.trim())
                .orElseThrow(() -> new IllegalArgumentException("Provisioning code is invalid or expired"));

        LocalDateTime now = LocalDateTime.now();
        if (asset.getProvisioningExpiresAt() == null || asset.getProvisioningExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Provisioning code is invalid or expired");
        }
        if (asset.getRestaurantId() == null) {
            throw new IllegalStateException("Tablet asset is not assigned to a restaurant");
        }
        if (!StringUtils.hasText(tabletApiKey)) {
            throw new IllegalStateException("Tablet API key is not configured on the server");
        }

        asset.setProvisionedAt(now);
        asset.setProvisioningNonce(null);
        asset.setProvisioningExpiresAt(null);
        if (StringUtils.hasText(appVersion)) {
            asset.setLastHeartbeatAppVersion(appVersion.trim());
        }
        tabletAssetRepository.save(asset);

        String baseUrl = resolveBaseUrl(runtimeBaseUrl);
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("Tablet provisioning base URL is not configured");
        }

        return new ProvisioningClaimResult(
                tabletApiKey,
                "/api/tablet/orders/register-token",
                "/api/tablet/orders/heartbeat",
                "/api/tablet/orders/pending");
    }

    @Transactional
    public ProvisioningClaimResult claimProvisioningByAssetTag(String assetTag, String appVersion) {
        if (!StringUtils.hasText(assetTag)) {
            throw new IllegalArgumentException("assetTag is required");
        }

        TabletAsset asset = tabletAssetRepository.findByAssetTag(assetTag.trim());
        if (asset == null || asset.isArchived()) {
            throw new IllegalArgumentException("Asset tag " + assetTag.trim() + " not found or not yet provisioned");
        }
        if (asset.getRestaurantId() == null) {
            throw new IllegalStateException("Tablet asset is not assigned to a restaurant");
        }
        if (!StringUtils.hasText(tabletApiKey)) {
            throw new IllegalStateException("Tablet API key is not configured on the server");
        }

        LocalDateTime now = LocalDateTime.now();
        asset.setProvisionedAt(now);
        if (StringUtils.hasText(appVersion)) {
            asset.setLastHeartbeatAppVersion(appVersion.trim());
        }
        tabletAssetRepository.save(asset);

        return new ProvisioningClaimResult(
                tabletApiKey,
                "/api/tablet/orders/register-token",
                "/api/tablet/orders/heartbeat",
                "/api/tablet/orders/pending");
    }

    public void sendProvisioningQrEmail(ProvisioningQrPackage provisioningPackage) {
        if (provisioningPackage == null) {
            throw new IllegalArgumentException("Provisioning package is required");
        }

        String recipient = provisioningPackage.recipientEmail();
        if (!StringUtils.hasText(recipient)) {
            throw new IllegalStateException("No provisioning email address is configured");
        }

        String subject = "DeliverMore tablet provisioning QR - " + provisioningPackage.assetTag();
        String body = String.format(
                Locale.CANADA,
                """
                Tablet provisioning QR generated.

                Asset: %s (%s)
                Restaurant: %s
                Expires at: %s
                Requested by: %s

                Scan the attached QR code from the tablet app to complete provisioning.
                """,
                provisioningPackage.assetName(),
                provisioningPackage.assetTag(),
                provisioningPackage.restaurantName(),
                provisioningPackage.expiresAt(),
                provisioningPackage.requestedBy());

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("tablet-provision-" + provisioningPackage.assetTag() + "-", ".png");
            Files.write(tempFile, provisioningPackage.qrPng());
            emailService.sendMailWithAttachment(
                    recipient,
                    subject,
                    body,
                    tempFile.toFile(),
                    "tablet-provision-" + provisioningPackage.assetTag() + ".png");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare provisioning QR email", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
        }
    }

    private String buildPayloadJson(String baseUrl, String claimPath, String assetTag) {
        try {
            return objectMapper.writeValueAsString(new ProvisioningQrPayload(
                    1,
                    baseUrl,
                    claimPath,
                    assetTag));
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? "unknown error" : ex.getMessage();
            throw new IllegalStateException("Failed to create provisioning payload: " + detail, ex);
        }
    }

    private byte[] renderQrPng(String payloadJson) {
        try {
            BitMatrix matrix = new com.google.zxing.MultiFormatWriter()
                    .encode(payloadJson, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return out.toByteArray();
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Failed to generate provisioning QR", ex);
        }
    }

    private String resolveBaseUrl(String runtimeBaseUrl) {
        if (StringUtils.hasText(configuredBaseUrl)) {
            return configuredBaseUrl;
        }
        return normalizeBaseUrl(runtimeBaseUrl);
    }

    private String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record ProvisioningQrPayload(
            int v,
            String baseUrl,
            String claimPath,
            String assetTag) {
    }

    public record ProvisioningQrPackage(
            Long assetId,
            String assetTag,
            String assetName,
            String restaurantName,
            String claimUrl,
            String nonce,
            LocalDateTime expiresAt,
            String payloadJson,
            byte[] qrPng,
            String recipientEmail,
            String requestedBy) {
    }

    public record ProvisioningClaimResult(
            String apiKey,
            String registerTokenPath,
            String heartbeatPath,
            String pendingOrdersPath) {
    }
}