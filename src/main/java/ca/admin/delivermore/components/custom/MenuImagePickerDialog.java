package ca.admin.delivermore.components.custom;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.upload.Receiver;
import com.vaadin.flow.component.upload.SucceededEvent;
import com.vaadin.flow.component.upload.Upload;

import ca.admin.delivermore.collector.data.entity.MenuImageAsset;
import com.flowingcode.vaadin.addons.imagecrop.Crop;
import com.flowingcode.vaadin.addons.imagecrop.ImageCrop;
import ca.admin.delivermore.data.service.MenuImageAssetService;
import ca.admin.delivermore.data.service.MenuImageSlot;

public class MenuImagePickerDialog extends Dialog {

    private static final int MIN_CROP_SIZE_PX = 40;

    private final MenuImageAssetService menuImageAssetService;
    private final MenuImageSlot slot;
    private final Consumer<Long> onSaved;

    private final Grid<MenuImageAsset> libraryGrid = new Grid<>();
    private final Image selectedPreview = new Image();
    private final Button cropSelectedButton = new Button("Crop selected image");
    private final Tab libraryTab = new Tab("Library");
    private final Tab uploadTab = new Tab("Add new to library");
    private final Tabs tabs = new Tabs(libraryTab, uploadTab);
    private Long selectedAssetId;
    private long imageCacheBuster = System.currentTimeMillis();
    private Component libraryContent;
    private Component uploadContent;
    private Upload upload;

    private final ByteArrayOutputStream uploadBuffer = new ByteArrayOutputStream(1024 * 1024);

    public MenuImagePickerDialog(
            MenuImageAssetService menuImageAssetService,
            MenuImageSlot slot,
            Long currentAssetId,
            Consumer<Long> onSaved) {
        this.menuImageAssetService = menuImageAssetService;
        this.slot = slot;
        this.onSaved = onSaved;
        this.selectedAssetId = currentAssetId;

        setHeaderTitle("Select " + slotLabel(slot));
        setDraggable(true);
        setResizable(true);
        setWidth("980px");
        setMaxWidth("95vw");

        libraryContent = buildLibraryContent();
        uploadContent = buildUploadContent();
        uploadContent.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean showLibrary = event.getSelectedTab() == libraryTab;
            libraryContent.setVisible(showLibrary);
            uploadContent.setVisible(!showLibrary);
        });

        VerticalLayout body = new VerticalLayout(tabs, libraryContent, uploadContent);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        add(body);

        Button cancel = new Button("Cancel", event -> close());
        Button clear = new Button("Remove image", event -> {
            this.onSaved.accept(null);
            close();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Use selected", event -> {
            this.onSaved.accept(selectedAssetId);
            close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, clear, save);

        refreshLibrary();
        refreshPreview();
    }

    private Component buildLibraryContent() {
        libraryGrid.removeAllColumns();
        libraryGrid.setHeight("460px");
        libraryGrid.addComponentColumn(asset -> {
            String imageUrl = menuImageAssetService.getImageUrl(asset.getId());
            if (imageUrl != null && !imageUrl.isBlank()) {
                imageUrl = imageUrl + "?v=" + imageCacheBuster;
            }
            Image thumb = new Image(imageUrl, "thumbnail");
            thumb.setWidth("120px");
            thumb.getStyle().set("border-radius", "6px");
            thumb.getStyle().set("object-fit", "cover");
            return thumb;
        }).setHeader("Image").setAutoWidth(true).setFlexGrow(0);
        libraryGrid.addColumn(MenuImageAsset::getOriginalFilename).setHeader("Name").setFlexGrow(1);
        libraryGrid.addColumn(asset -> asset.getWidthPx() + "x" + asset.getHeightPx()).setHeader("Size").setAutoWidth(true);
        libraryGrid.addColumn(asset -> readableSize(asset.getFileSizeBytes())).setHeader("File").setAutoWidth(true);
        libraryGrid.addComponentColumn(this::buildDeleteButton).setHeader("").setAutoWidth(true).setFlexGrow(0);
        libraryGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        libraryGrid.addSelectionListener(event -> {
            event.getFirstSelectedItem().ifPresent(asset -> {
                selectedAssetId = asset.getId();
                refreshPreview();
                updateLibraryActionStates();
            });
        });

        selectedPreview.setAlt("Selected image preview");
        selectedPreview.setWidth("320px");
        selectedPreview.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        selectedPreview.getStyle().set("border-radius", "8px");
        selectedPreview.getStyle().set("background", "var(--lumo-contrast-5pct)");
        selectedPreview.getStyle().set("object-fit", "cover");

        cropSelectedButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        cropSelectedButton.addClickListener(event -> openCropDialogForSelectedAsset());

        VerticalLayout right = new VerticalLayout(
                new Span("Selected image"),
                selectedPreview,
                cropSelectedButton,
                new Span("Shape: " + slot.getShapeType() + "  Aspect: " + slot.getAspectWidth() + ":" + slot.getAspectHeight()));
        right.setWidth("360px");
        right.setPadding(false);
        right.setSpacing(true);

        HorizontalLayout layout = new HorizontalLayout(libraryGrid, right);
        layout.setWidthFull();
        layout.setFlexGrow(1, libraryGrid);
        layout.setFlexGrow(0, right);
        layout.setAlignItems(HorizontalLayout.Alignment.START);
        return layout;
    }

    private Component buildUploadContent() {
        Receiver receiver = new Receiver() {
            @Override
            public OutputStream receiveUpload(String fileName, String mimeType) {
                uploadBuffer.reset();
                return uploadBuffer;
            }
        };

        upload = new Upload(receiver);
        upload.setAcceptedFileTypes("image/*");
        upload.setAutoUpload(true);
        upload.setMaxFiles(1);
        upload.setWidthFull();
        upload.addSucceededListener(this::openCropDialog);

        Span note = new Span(
                "Upload a source image, then crop it manually before saving. The crop box is locked to "
                        + slot.getAspectWidth() + ":" + slot.getAspectHeight()
                        + ". Max upload: " + readableSize(menuImageAssetService.getMaxUploadBytes()) + ".");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)");
        note.getStyle().set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout uploadPanel = new VerticalLayout(note, upload);
        uploadPanel.setPadding(false);
        uploadPanel.setSpacing(true);
        uploadPanel.setWidthFull();
        return uploadPanel;
    }

    private void openCropDialog(SucceededEvent event) {
        String mimeType = event.getMIMEType() == null || event.getMIMEType().isBlank() ? "image/jpeg" : event.getMIMEType();
        String source = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(uploadBuffer.toByteArray());

        openCropDialog(source, event.getFileName(), null, true);
    }

    private void openCropDialogForSelectedAsset() {
        if (selectedAssetId == null) {
            showError("Select an image first");
            return;
        }

        MenuImageAsset asset = menuImageAssetService.getAsset(selectedAssetId)
                .orElseThrow(() -> new IllegalStateException("Selected image no longer exists"));
        String source = menuImageAssetService.getImageUrl(asset.getId()) + "?v=" + System.currentTimeMillis();
        openCropDialog(source, asset.getOriginalFilename(), asset.getId(), false);
    }

    private void openCropDialog(String source, String sourceFileName, Long replaceAssetId, boolean fromUpload) {

        ImageCrop cropper = new ImageCrop(source);
        cropper.setAspect((double) slot.getAspectWidth() / (double) slot.getAspectHeight());
        boolean isLogoSlot = slot == MenuImageSlot.RESTAURANT_LOGO;
        cropper.setCircularCrop(isLogoSlot);
        cropper.setKeepSelection(true);
        cropper.setRuleOfThirds(true);
        cropper.setLocked(false);
        cropper.setDisabled(false);
        // Keep a small minimum so users can both shrink and enlarge the selection.
        cropper.setCropMinWidth(MIN_CROP_SIZE_PX);
        cropper.setCropMinHeight(MIN_CROP_SIZE_PX);
        cropper.setCrop(buildDefaultCrop());
        cropper.getElement().getStyle().set("width", "100%");
        cropper.getElement().getStyle().set("height", "520px");

        Dialog cropDialog = new Dialog();
        cropDialog.setHeaderTitle("Crop " + slotLabel(slot));
        cropDialog.setWidth("1100px");
        cropDialog.setMaxWidth("95vw");
        cropDialog.setModal(true);
        cropDialog.setCloseOnOutsideClick(false);

        Button cancel = new Button("Cancel", event1 -> cropDialog.close());

        Checkbox circularCropToggle = null;
        if (isLogoSlot) {
            circularCropToggle = new Checkbox("Crop as circle", true);
            circularCropToggle.addValueChangeListener(event -> cropper.setCircularCrop(Boolean.TRUE.equals(event.getValue())));
        }

        Button saveNew = new Button("Save as new", event1 -> {
            try {
                byte[] croppedBytes = decodeCroppedBytes(cropper.getCroppedImageDataUri());
                MenuImageAssetService.StoredImage stored = menuImageAssetService.saveUploadedImage(
                        sourceFileName,
                        croppedBytes,
                        slot);
                selectedAssetId = stored.id();
                bumpImageCacheBuster();
                refreshLibrary();
                refreshPreview();
                if (fromUpload) {
                    clearUploadSelection();
                }
                showLibraryTab();
                updateLibraryActionStates();
                showSuccess("Image saved");
                cropDialog.close();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        saveNew.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        if (replaceAssetId != null) {
            Button replaceExisting = new Button("Replace existing", event1 -> {
                try {
                    byte[] croppedBytes = decodeCroppedBytes(cropper.getCroppedImageDataUri());
                    MenuImageAssetService.StoredImage stored = menuImageAssetService.replaceAssetImage(
                            replaceAssetId,
                            sourceFileName,
                            croppedBytes,
                            slot);
                    selectedAssetId = stored.id();
                        bumpImageCacheBuster();
                    refreshLibrary();
                    refreshPreview();
                    showLibraryTab();
                    updateLibraryActionStates();
                    showSuccess("Image replaced");
                    cropDialog.close();
                } catch (IllegalStateException ex) {
                    showError(ex.getMessage());
                }
            });
            replaceExisting.addThemeVariants(ButtonVariant.LUMO_ERROR);
            if (circularCropToggle != null) {
                cropDialog.add(circularCropToggle, cropper);
            } else {
                cropDialog.add(cropper);
            }
            cropDialog.getFooter().add(cancel, replaceExisting, saveNew);
            cropDialog.open();
            return;
        }

        if (circularCropToggle != null) {
            cropDialog.add(circularCropToggle, cropper);
        } else {
            cropDialog.add(cropper);
        }
        cropDialog.getFooter().add(cancel, saveNew);
        cropDialog.open();
    }

    private Crop buildDefaultCrop() {
        if (slot == MenuImageSlot.RESTAURANT_LOGO) {
            return new Crop("%", 10, 10, 80, 80);
        }
        return new Crop("%", 5, 10, 90, 75);
    }

    private byte[] decodeCroppedBytes(String encodedText) {
        if (encodedText == null || encodedText.isBlank()) {
            throw new IllegalStateException("Cropped image was empty");
        }

        String base64Text = encodedText.trim();
        if (encodedText.contains(",")) {
            base64Text = encodedText.substring(encodedText.indexOf(',') + 1);
        }

        try {
            return Base64.getDecoder().decode(base64Text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decode cropped image", ex);
        }
    }

    private void refreshLibrary() {
        List<MenuImageAsset> assets = menuImageAssetService.listAssetsForSlot(slot);
        libraryGrid.setItems(assets);
        if (selectedAssetId == null) {
            libraryGrid.deselectAll();
            updateLibraryActionStates();
            return;
        }
        assets.stream()
                .filter(asset -> selectedAssetId.equals(asset.getId()))
                .findFirst()
                .ifPresentOrElse(libraryGrid::select, libraryGrid::deselectAll);
        updateLibraryActionStates();
    }

    private Button buildDeleteButton(MenuImageAsset asset) {
        Button button = new Button(VaadinIcon.TRASH.create(), event -> confirmDeleteAsset(asset));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL);
        button.getElement().setAttribute("aria-label", "Delete image");
        button.getElement().setAttribute("title", "Delete image");
        return button;
    }

    private void confirmDeleteAsset(MenuImageAsset asset) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete image from library?");
        dialog.setText(asset.getOriginalFilename() == null || asset.getOriginalFilename().isBlank()
                ? "Delete this image from the library?"
                : "Delete '" + asset.getOriginalFilename() + "' from the library?");
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try {
                menuImageAssetService.deleteAsset(asset.getId());
                if (asset.getId().equals(selectedAssetId)) {
                    selectedAssetId = null;
                    refreshPreview();
                }
                refreshLibrary();
                updateLibraryActionStates();
                showSuccess("Image deleted");
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        dialog.open();
    }

    private void clearUploadSelection() {
        uploadBuffer.reset();
        if (upload != null) {
            upload.clearFileList();
        }
    }

    private void showLibraryTab() {
        tabs.setSelectedTab(libraryTab);
        libraryContent.setVisible(true);
        uploadContent.setVisible(false);
    }

    private void refreshPreview() {
        if (selectedAssetId == null) {
            selectedPreview.setSrc("");
            updateLibraryActionStates();
            return;
        }
        String imageUrl = menuImageAssetService.getImageUrl(selectedAssetId);
        selectedPreview.setSrc(imageUrl == null ? "" : imageUrl + "?v=" + imageCacheBuster);
        updateLibraryActionStates();
    }

    private void bumpImageCacheBuster() {
        imageCacheBuster = System.currentTimeMillis();
    }

    private void updateLibraryActionStates() {
        cropSelectedButton.setEnabled(selectedAssetId != null);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3500, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private String slotLabel(MenuImageSlot slot) {
        return switch (slot) {
            case RESTAURANT_LOGO -> "Restaurant Logo";
            case MENU_HEADER -> "Menu Header Image";
            case MENU_GROUP -> "Menu Group Image";
            case MENU_ITEM -> "Menu Item Image";
        };
    }

    private String readableSize(long bytes) {
        long mb = 1024L * 1024L;
        if (bytes % mb == 0) {
            return (bytes / mb) + " MB";
        }
        return String.format("%.2f MB", bytes / (double) mb);
    }
}