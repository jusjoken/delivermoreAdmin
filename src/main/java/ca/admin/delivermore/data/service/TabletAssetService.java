package ca.admin.delivermore.data.service;

import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.entity.TabletAssetHistory;
import ca.admin.delivermore.security.AuthenticatedUser;

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
        LocalDateTime now = LocalDateTime.now();
        String who = currentUsername();

        // preserve createdAt/createdBy if already set; but ensure updated fields are set
        if (asset.getCreatedAt() == null) {
            asset.setCreatedAt(now);
        }
        if (asset.getCreatedBy() == null || asset.getCreatedBy().isBlank()) {
            asset.setCreatedBy(who);
        }

        asset.setUpdatedAt(now);
        asset.setUpdatedBy(who);

        TabletAsset saved = assetRepo.save(asset);
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