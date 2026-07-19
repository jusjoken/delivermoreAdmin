package ca.admin.delivermore.data.service;

import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.entity.TabletAssetHistory;
import ca.admin.delivermore.security.AuthenticatedUser;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class TabletAssetService {

    private final TabletAssetRepository assetRepo;
    private final TabletAssetHistoryRepository historyRepo;
    private final AuthenticatedUser authenticatedUser;

    public TabletAssetService(TabletAssetRepository assetRepo,
                              TabletAssetHistoryRepository historyRepo,
                              AuthenticatedUser authenticatedUser) {
        this.assetRepo = assetRepo;
        this.historyRepo = historyRepo;
        this.authenticatedUser = authenticatedUser;
    }

    @Transactional
    public TabletAsset create(TabletAsset asset) {
        LocalDateTime now = LocalDateTime.now();
        String who = currentUsername();

        asset.setCreatedAt(now);
        asset.setCreatedBy(who);
        asset.setUpdatedAt(now);
        asset.setUpdatedBy(who);

        TabletAsset saved = assetRepo.save(asset);
        addHistory(saved, TabletAssetHistory.ChangeType.CREATE);
        return saved;
    }

    @Transactional
    public TabletAsset update(TabletAsset asset) {
        if (asset == null || asset.getId() == null) {
            throw new IllegalArgumentException("Asset id is required for update");
        }

        LocalDateTime now = LocalDateTime.now();
        String who = currentUsername();

        TabletAsset current = assetRepo.findById(asset.getId()).orElseThrow();

        // Apply only admin-editable fields so background heartbeat/token writes are preserved.
        current.setAssetName(asset.getAssetName());
        current.setAssetTag(asset.getAssetTag());
        current.setAssignedFleetId(asset.getAssignedFleetId());
        current.setRestaurantName(asset.getRestaurantName());
        current.setRestaurantId(asset.getRestaurantId());
        current.setArchived(asset.isArchived());
        current.setNotes(asset.getNotes());

        // preserve original created metadata; update audit on each write.
        if (current.getCreatedAt() == null) {
            current.setCreatedAt(now);
        }
        if (current.getCreatedBy() == null || current.getCreatedBy().isBlank()) {
            current.setCreatedBy(who);
        }

        current.setUpdatedAt(now);
        current.setUpdatedBy(who);

        TabletAsset saved;
        try {
            saved = assetRepo.save(current);
        } catch (OptimisticLockingFailureException ex) {
            throw new IllegalStateException("This asset was updated by another process. Refresh and try again.", ex);
        }
        addHistory(saved, TabletAssetHistory.ChangeType.UPDATE);
        return saved;
    }

    @Transactional
    public TabletAsset setArchived(long assetId, boolean archived) {
        TabletAsset asset = assetRepo.findById(assetId).orElseThrow();
        asset.setArchived(archived);
        TabletAsset saved = update(asset); // ensures updatedAt/updatedBy + creates UPDATE history too
        addHistory(saved,
                archived ? TabletAssetHistory.ChangeType.ARCHIVE : TabletAssetHistory.ChangeType.UNARCHIVE);
        return saved;
    }

    // BEFORE
    // private void addHistory(TabletAsset asset, TabletAssetHistory.ChangeType type, String notes) {

    // AFTER
    private void addHistory(TabletAsset asset, TabletAssetHistory.ChangeType type) {
        TabletAssetHistory h = new TabletAssetHistory();
        h.setTabletAsset(asset);
        h.setChangeType(type);
        h.setChangedAt(Instant.now());
        h.setChangedBy(currentUsername());

        // snapshot fields (structured)
        h.setAssetName(asset.getAssetName());
        h.setAssetTag(asset.getAssetTag());
        h.setAssignedFleetId(asset.getAssignedFleetId());
        h.setRestaurantName(asset.getRestaurantName());
        h.setRestaurantId(asset.getRestaurantId());
        h.setArchived(asset.isArchived());

        // snapshot "current state note"
        h.setNotes(asset.getNotes());

        historyRepo.save(h);
    }
    
    private String currentUsername() {
        // Prefer authenticated Driver email if available; fallback to empty string
        return authenticatedUser.get()
                .map(d -> d.getEmail() == null ? "" : d.getEmail())
                .orElse("");
    }
}