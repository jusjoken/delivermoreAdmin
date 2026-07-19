package ca.admin.delivermore.views.utility;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import ca.admin.delivermore.data.service.TabletProvisioningService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping({"/api/tablet/provision", "/api/provision"})
public class TabletProvisioningApiController {

    private final TabletProvisioningService tabletProvisioningService;

    public TabletProvisioningApiController(TabletProvisioningService tabletProvisioningService) {
        this.tabletProvisioningService = tabletProvisioningService;
    }

    @PostMapping(value = {"", "/", "/claim"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> claimProvisioning(
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest servletRequest) {
        String assetTag = firstNonBlank(
            queryParams.get("assetTag"),
            value(requestBody, "assetTag"));

        String nonce = firstNonBlank(
                queryParams.get("nonce"),
                queryParams.get("token"),
                queryParams.get("code"),
                queryParams.get("provisioningNonce"),
                queryParams.get("provisioningCode"),
                value(requestBody, "nonce"),
                value(requestBody, "token"),
                value(requestBody, "code"),
                value(requestBody, "provisioningNonce"),
                value(requestBody, "provisioningCode"));

        String appVersion = firstNonBlank(
                queryParams.get("appVersion"),
                value(requestBody, "appVersion"));

        return claimInternal(assetTag, nonce, appVersion, servletRequest);
    }

    @GetMapping(value = {"", "/", "/claim"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> claimProvisioningGet(
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest servletRequest) {
        String assetTag = firstNonBlank(queryParams.get("assetTag"));

        String nonce = firstNonBlank(
                queryParams.get("nonce"),
                queryParams.get("token"),
                queryParams.get("code"),
                queryParams.get("provisioningNonce"),
                queryParams.get("provisioningCode"));

        String appVersion = firstNonBlank(queryParams.get("appVersion"));
        return claimInternal(assetTag, nonce, appVersion, servletRequest);
    }

    private ResponseEntity<?> claimInternal(
            String assetTag,
            String nonce,
            String appVersion,
            HttpServletRequest servletRequest) {
        if ((assetTag == null || assetTag.isBlank()) && (nonce == null || nonce.isBlank())) {
            return jsonError(HttpStatus.BAD_REQUEST, "assetTag is required");
        }

        String runtimeBaseUrl = ServletUriComponentsBuilder.fromRequestUri(servletRequest)
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();

        try {
            TabletProvisioningService.ProvisioningClaimResult result;
            if (assetTag != null && !assetTag.isBlank()) {
                result = tabletProvisioningService.claimProvisioningByAssetTag(assetTag, appVersion);
            } else {
                result = tabletProvisioningService.claimProvisioning(nonce, runtimeBaseUrl, appVersion);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "Invalid provisioning request" : ex.getMessage();
            if (message.toLowerCase().contains("not found")) {
                return jsonError(HttpStatus.NOT_FOUND, message);
            }
            return jsonError(HttpStatus.BAD_REQUEST, message);
        } catch (IllegalStateException ex) {
            return jsonError(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "status", "error",
                        "message", message == null ? "Request failed" : message));
    }

    private String value(Map<String, Object> body, String key) {
        if (body == null || key == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}