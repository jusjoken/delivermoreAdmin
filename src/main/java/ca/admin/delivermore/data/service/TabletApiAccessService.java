package ca.admin.delivermore.data.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ca.admin.delivermore.data.entity.TabletAsset;

@Service
public class TabletApiAccessService {

    private final TabletAssetRepository tabletAssetRepository;
    private final String tabletApiKey;

    public TabletApiAccessService(
            TabletAssetRepository tabletAssetRepository,
            @Value("${app.tablet.api-key:}") String tabletApiKey) {
        this.tabletAssetRepository = tabletAssetRepository;
        this.tabletApiKey = tabletApiKey == null ? "" : tabletApiKey;
    }

    public TabletAsset authorize(String assetTagHeader, String apiKeyHeader) {
        if (tabletApiKey.isBlank()) {
            throw new TabletApiAccessException(
                    TabletApiAccessException.Reason.SERVER_NOT_CONFIGURED,
                    "Tablet API key is not configured on the server.");
        }

        if (apiKeyHeader == null || apiKeyHeader.isBlank() || !tabletApiKey.equals(apiKeyHeader)) {
            throw new TabletApiAccessException(
                    TabletApiAccessException.Reason.INVALID_CREDENTIALS,
                    "Invalid tablet API credentials.");
        }

        if (assetTagHeader == null || assetTagHeader.isBlank()) {
            throw new TabletApiAccessException(
                    TabletApiAccessException.Reason.INVALID_CREDENTIALS,
                    "Missing tablet asset tag header.");
        }

        TabletAsset tabletAsset = tabletAssetRepository.findByAssetTag(assetTagHeader.trim());
        if (tabletAsset == null || tabletAsset.isArchived()) {
            throw new TabletApiAccessException(
                    TabletApiAccessException.Reason.INVALID_CREDENTIALS,
                    "Unknown or archived tablet asset.");
        }

        if (tabletAsset.getRestaurantId() == null) {
            throw new TabletApiAccessException(
                    TabletApiAccessException.Reason.NOT_ASSIGNED,
                    "Tablet asset is not assigned to a restaurant.");
        }

        return tabletAsset;
    }

    public static class TabletApiAccessException extends RuntimeException {

        public enum Reason {
            INVALID_CREDENTIALS,
            NOT_ASSIGNED,
            SERVER_NOT_CONFIGURED
        }

        private final Reason reason;

        public TabletApiAccessException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}