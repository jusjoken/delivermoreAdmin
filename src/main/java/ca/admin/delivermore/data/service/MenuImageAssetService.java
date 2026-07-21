package ca.admin.delivermore.data.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.admin.delivermore.collector.data.entity.MenuImageAsset;
import ca.admin.delivermore.collector.data.service.MenuImageAssetRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuCategoryRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuItemRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuVersionRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;

@Service
public class MenuImageAssetService {

    public record StoredImage(Long id, String url) {
    }

    private final MenuImageAssetRepository menuImageAssetRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuVersionRepository restaurantMenuVersionRepository;
    private final RestaurantMenuCategoryRepository restaurantMenuCategoryRepository;
    private final RestaurantMenuItemRepository restaurantMenuItemRepository;
    private final Path storageRoot;
    private final long maxUploadBytes;

    public MenuImageAssetService(
            MenuImageAssetRepository menuImageAssetRepository,
            RestaurantRepository restaurantRepository,
            RestaurantMenuVersionRepository restaurantMenuVersionRepository,
            RestaurantMenuCategoryRepository restaurantMenuCategoryRepository,
            RestaurantMenuItemRepository restaurantMenuItemRepository,
            @Value("${app.menu-images.storage-root:./data/menu-images}") String storageRoot,
            @Value("${app.menu-images.max-upload-bytes:4194304}") long maxUploadBytes) {
        this.menuImageAssetRepository = menuImageAssetRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantMenuVersionRepository = restaurantMenuVersionRepository;
        this.restaurantMenuCategoryRepository = restaurantMenuCategoryRepository;
        this.restaurantMenuItemRepository = restaurantMenuItemRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public String getImageUrl(Long imageAssetId) {
        if (imageAssetId == null) {
            return null;
        }
        return "/api/menu-images/" + imageAssetId;
    }

    public Optional<MenuImageAsset> getAsset(Long imageAssetId) {
        if (imageAssetId == null) {
            return Optional.empty();
        }
        return menuImageAssetRepository.findById(imageAssetId);
    }

    public List<MenuImageAsset> listAssetsForSlot(MenuImageSlot slot) {
        return menuImageAssetRepository.findByShapeTypeOrderByUpdatedAtDescCreatedAtDescIdDesc(slot.getShapeType());
    }

    @Transactional
    public StoredImage saveUploadedImage(String originalFilename, byte[] content, MenuImageSlot slot) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("Uploaded image is empty");
        }
        if (content.length > maxUploadBytes) {
            throw new IllegalStateException("Image is too large. Max allowed is " + readableSize(maxUploadBytes));
        }

        BufferedImage source = readImage(content);
        BufferedImage cropped = cropToAspect(source, slot.getAspectWidth(), slot.getAspectHeight());
        BufferedImage resized = resizeWithinBounds(cropped, slot.getMaxWidth(), slot.getMaxHeight());
        byte[] encoded = encodeJpeg(resized);

        LocalDate now = LocalDate.now();
        String folder = slot.getShapeType().toLowerCase() + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue());
        String fileName = UUID.randomUUID() + ".jpg";
        Path directoryPath = storageRoot.resolve(folder);
        Path targetPath = directoryPath.resolve(fileName);

        try {
            Files.createDirectories(directoryPath);
            Files.write(targetPath, encoded);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store image file", ex);
        }

        MenuImageAsset asset = new MenuImageAsset();
        asset.setShapeType(slot.getShapeType());
        asset.setStoragePath(folder + "/" + fileName);
        asset.setContentType("image/jpeg");
        asset.setOriginalFilename(originalFilename);
        asset.setWidthPx(resized.getWidth());
        asset.setHeightPx(resized.getHeight());
        asset.setFileSizeBytes((long) encoded.length);
        MenuImageAsset saved = menuImageAssetRepository.save(asset);
        return new StoredImage(saved.getId(), getImageUrl(saved.getId()));
    }

    @Transactional
    public StoredImage replaceAssetImage(Long imageAssetId, String originalFilename, byte[] content, MenuImageSlot slot) {
        if (imageAssetId == null) {
            throw new IllegalStateException("No image selected to replace");
        }

        MenuImageAsset existing = menuImageAssetRepository.findById(imageAssetId)
                .orElseThrow(() -> new IllegalStateException("Image asset no longer exists"));

        if (!slot.getShapeType().equalsIgnoreCase(existing.getShapeType())) {
            throw new IllegalStateException("Selected image does not match the required image type");
        }

        if (content == null || content.length == 0) {
            throw new IllegalStateException("Uploaded image is empty");
        }
        if (content.length > maxUploadBytes) {
            throw new IllegalStateException("Image is too large. Max allowed is " + readableSize(maxUploadBytes));
        }

        BufferedImage source = readImage(content);
        BufferedImage cropped = cropToAspect(source, slot.getAspectWidth(), slot.getAspectHeight());
        BufferedImage resized = resizeWithinBounds(cropped, slot.getMaxWidth(), slot.getMaxHeight());
        byte[] encoded = encodeJpeg(resized);

        LocalDate now = LocalDate.now();
        String folder = slot.getShapeType().toLowerCase() + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue());
        String fileName = UUID.randomUUID() + ".jpg";
        Path directoryPath = storageRoot.resolve(folder);
        Path targetPath = directoryPath.resolve(fileName);

        try {
            Files.createDirectories(directoryPath);
            Files.write(targetPath, encoded);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store image file", ex);
        }

        String oldStoragePath = existing.getStoragePath();
        existing.setStoragePath(folder + "/" + fileName);
        existing.setContentType("image/jpeg");
        if (originalFilename != null && !originalFilename.isBlank()) {
            existing.setOriginalFilename(originalFilename);
        }
        existing.setWidthPx(resized.getWidth());
        existing.setHeightPx(resized.getHeight());
        existing.setFileSizeBytes((long) encoded.length);
        MenuImageAsset saved = menuImageAssetRepository.save(existing);

        if (oldStoragePath != null && !oldStoragePath.isBlank() && !oldStoragePath.equals(existing.getStoragePath())) {
            Path oldFilePath = storageRoot.resolve(oldStoragePath).normalize();
            if (oldFilePath.startsWith(storageRoot)) {
                try {
                    Files.deleteIfExists(oldFilePath);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete previous image file", ex);
                }
            }
        }

        return new StoredImage(saved.getId(), getImageUrl(saved.getId()));
    }

    @Transactional
    public void deleteAsset(Long imageAssetId) {
        if (imageAssetId == null) {
            return;
        }

        MenuImageAsset asset = menuImageAssetRepository.findById(imageAssetId)
                .orElseThrow(() -> new IllegalStateException("Image asset no longer exists"));

        if (isAssetInUse(imageAssetId)) {
            throw new IllegalStateException("Image is currently in use and cannot be deleted");
        }

        Path filePath = storageRoot.resolve(asset.getStoragePath()).normalize();
        if (filePath.startsWith(storageRoot)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to delete stored image file", ex);
            }
        }

        menuImageAssetRepository.delete(asset);
    }

    public Optional<ResolvedImageResource> resolveImageResource(Long imageAssetId) {
        Optional<MenuImageAsset> assetOpt = getAsset(imageAssetId);
        if (assetOpt.isEmpty()) {
            return Optional.empty();
        }

        MenuImageAsset asset = assetOpt.get();
        Path filePath = storageRoot.resolve(asset.getStoragePath()).normalize();
        if (!filePath.startsWith(storageRoot)) {
            return Optional.empty();
        }
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        Resource resource = new FileSystemResource(filePath);
        return Optional.of(new ResolvedImageResource(asset, resource));
    }

    public record ResolvedImageResource(MenuImageAsset asset, Resource resource) {
    }

    private boolean isAssetInUse(Long imageAssetId) {
        return restaurantRepository.countByLogoImageAssetId(imageAssetId) > 0
                || restaurantMenuVersionRepository.countByHeaderImageAssetId(imageAssetId) > 0
                || restaurantMenuCategoryRepository.countByImageAssetId(imageAssetId) > 0
                || restaurantMenuItemRepository.countByImageAssetId(imageAssetId) > 0;
    }

    private BufferedImage readImage(byte[] content) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                throw new IllegalStateException("Uploaded file is not a supported image");
            }
            return source;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded image", ex);
        }
    }

    private BufferedImage cropToAspect(BufferedImage source, int aspectWidth, int aspectHeight) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double targetRatio = (double) aspectWidth / (double) aspectHeight;
        double sourceRatio = (double) sourceWidth / (double) sourceHeight;

        int cropWidth = sourceWidth;
        int cropHeight = sourceHeight;
        if (sourceRatio > targetRatio) {
            cropWidth = (int) Math.round(sourceHeight * targetRatio);
        } else if (sourceRatio < targetRatio) {
            cropHeight = (int) Math.round(sourceWidth / targetRatio);
        }

        int x = Math.max(0, (sourceWidth - cropWidth) / 2);
        int y = Math.max(0, (sourceHeight - cropHeight) / 2);
        return source.getSubimage(x, y, cropWidth, cropHeight);
    }

    private BufferedImage resizeWithinBounds(BufferedImage source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1d, Math.min((double) maxWidth / width, (double) maxHeight / height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "jpg", outputStream)) {
                throw new IllegalStateException("JPEG encoder is not available");
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode image", ex);
        }
    }

    private String readableSize(long bytes) {
        long mb = 1024L * 1024L;
        if (bytes % mb == 0) {
            return (bytes / mb) + " MB";
        }
        return String.format("%.2f MB", bytes / (double) mb);
    }
}