package ca.admin.delivermore.views.utility;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.PollEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.shared.Registration;

import ca.admin.delivermore.data.service.LogViewerService;
import ca.admin.delivermore.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Log Viewer")
@Route(value = "logviewer", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class LogViewerView extends VerticalLayout {

    private static final int POLL_INTERVAL_MS = 2000;
    private static final int CHUNK_SIZE_BYTES = 64 * 1024;
    private static final int MAX_TEXT_CHARS = 250_000;
    private static final DateTimeFormatter STATUS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogViewerService logViewerService;

    private final ComboBox<String> sourceSelect = new ComboBox<>("Source");
    private final ComboBox<LogViewerService.LogFileItem> fileSelect = new ComboBox<>("Log File");
    private final IntegerField tailLines = new IntegerField("Tail Lines");
    private final TextField filterContains = new TextField("Contains");
    private final Checkbox liveFollow = new Checkbox("Live follow");
    private final Checkbox wordWrap = new Checkbox("Word wrap");

    private final Button loadButton = new Button();
    private final Button loadOlderButton = new Button();
    private final Button refreshButton = new Button();
    private final Button downloadButton = new Button();
    private final Anchor downloadLink = new Anchor();

    private final Span status = new Span("Ready");
    private final Pre logPre = new Pre();
    private final Div viewerContainer = new Div();

    private String activeSource;
    private String activeFileId;
    private long activeOffset;
    private long loadedStartOffset;
    private String logContent = "";
    private long displayBytes = 0;
    private long fileBytes = 0;
    private LocalDateTime lastUpdated;
    private Registration pollRegistration;

    public LogViewerView(LogViewerService logViewerService) {
        this.logViewerService = logViewerService;

        setSizeFull();
        setPadding(false);
        setSpacing(true);
        getStyle().set("overflow", "hidden");

        add(createToolbar(), createViewer(), createFooter());
        setFlexGrow(1, viewerContainer);

        configureActions();
        initializeDefaults();
    }

    private HorizontalLayout createToolbar() {
        Map<String, String> sourceOptions = logViewerService.getSourceOptions();

        sourceSelect.setItems(sourceOptions.keySet());
        sourceSelect.setItemLabelGenerator(key -> sourceOptions.getOrDefault(key, key));
        sourceSelect.setWidth("140px");

        fileSelect.setItemLabelGenerator(item -> item.available() ? item.displayName() : item.displayName() + " (missing)");
        fileSelect.setWidth("240px");

        tailLines.setStepButtonsVisible(true);
        tailLines.setMin(10);
        tailLines.setMax(10000);
        tailLines.setValue(300);
        tailLines.setWidth("120px");

        filterContains.setPlaceholder("text to match");
        filterContains.setClearButtonVisible(true);
        filterContains.setWidth("220px");

        liveFollow.setValue(false);
        wordWrap.setValue(true);

        loadButton.setText("Load");
        loadButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        loadButton.setTooltipText("Load selected log");
        loadButton.getElement().setAttribute("aria-label", "Load selected log");
        configureIconButton(loadOlderButton, VaadinIcon.ANGLE_DOUBLE_UP, "Load older lines");
        configureIconButton(refreshButton, VaadinIcon.REFRESH, "Refresh current log");
        configureIconButton(downloadButton, VaadinIcon.DOWNLOAD_ALT, "Download selected log");

        downloadLink.add(downloadButton);
        downloadLink.getElement().setAttribute("download", true);

        HorizontalLayout layout = new HorizontalLayout(
                sourceSelect,
                fileSelect,
                tailLines,
                filterContains,
                liveFollow,
                wordWrap,
                loadButton,
                loadOlderButton,
                refreshButton,
                downloadLink);
        layout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        layout.setWidthFull();
        layout.setWrap(true);
        return layout;
    }

    private HorizontalLayout createFooter() {
        status.getStyle().set("font-size", "var(--lumo-font-size-s)");
        status.getStyle().set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout footer = new HorizontalLayout(status);
        footer.setWidthFull();
        footer.setPadding(false);
        footer.setSpacing(false);
        footer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        footer.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
        footer.getStyle().set("padding", "var(--lumo-space-xs) var(--lumo-space-s)");
        return footer;
    }

    private void configureIconButton(Button button, VaadinIcon iconType, String tooltip) {
        Icon icon = iconType.create();
        button.setIcon(icon);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
        button.setTooltipText(tooltip);
        button.getElement().setAttribute("aria-label", tooltip);
    }

    private Div createViewer() {
        logPre.setWidthFull();
        logPre.setHeightFull();
        logPre.getStyle().set("font-family", "monospace");
        logPre.getStyle().set("margin", "0");
        logPre.getStyle().set("padding", "var(--lumo-space-s)");
        applyWordWrap(true);

        viewerContainer.removeAll();
        viewerContainer.add(logPre);
        viewerContainer.setSizeFull();
        viewerContainer.getStyle().set("overflow", "auto");
        viewerContainer.getStyle().set("scrollbar-gutter", "stable");
        return viewerContainer;
    }

    private void configureActions() {
        sourceSelect.addValueChangeListener(event -> reloadFileList(event.getValue()));
        fileSelect.addValueChangeListener(event -> updateDownloadResource());

        loadButton.addClickListener(event -> loadSelectedFile());
        loadOlderButton.addClickListener(event -> loadOlder());
        refreshButton.addClickListener(event -> refreshCurrent());
        filterContains.addValueChangeListener(event -> renderLogContent(false));

        liveFollow.addValueChangeListener(event -> configurePolling(event.getValue()));
        wordWrap.addValueChangeListener(event -> applyWordWrap(Boolean.TRUE.equals(event.getValue())));
        addAttachListener(event -> {
            pollRegistration = event.getUI().addPollListener(this::handlePoll);
            // Ensure textarea internals are updated after attach/render.
            applyWordWrap(Boolean.TRUE.equals(wordWrap.getValue()));
        });
        addDetachListener(event -> {
            if (pollRegistration != null) {
                pollRegistration.remove();
                pollRegistration = null;
            }
        });
    }

    private void initializeDefaults() {
        List<String> keys = logViewerService.getSourceOptions().keySet().stream().toList();
        if (keys.isEmpty()) {
            status.setText("No log sources configured");
            return;
        }

        sourceSelect.setValue(keys.getFirst());
        reloadFileList(keys.getFirst());

        if (fileSelect.getValue() != null) {
            loadSelectedFile();
        }
    }

    private void reloadFileList(String sourceKey) {
        if (sourceKey == null) {
            fileSelect.clear();
            fileSelect.setItems(List.of());
            return;
        }

        try {
            List<LogViewerService.LogFileItem> files = logViewerService.listFiles(sourceKey);
            fileSelect.setItems(files);
            fileSelect.setValue(files.stream().filter(item -> LogViewerService.CURRENT_FILE_ID.equals(item.id())).findFirst().orElse(null));
            updateDownloadResource();
            updateStatus("Loaded " + files.size() + " log file entries");
        } catch (Exception ex) {
            updateStatus("Failed to list files: " + ex.getMessage());
            Notification.show("Unable to load log files", 4000, Notification.Position.BOTTOM_START);
        }
    }

    private void loadSelectedFile() {
        String source = sourceSelect.getValue();
        LogViewerService.LogFileItem selectedFile = fileSelect.getValue();

        if (source == null || selectedFile == null) {
            Notification.show("Choose source and log file first", 3000, Notification.Position.BOTTOM_START);
            return;
        }

        try {
            String content = logViewerService.tail(source, selectedFile.id(), safeTailLines());
            setLogContent(content, true);
            activeSource = source;
            activeFileId = selectedFile.id();
            activeOffset = logViewerService.fileSize(source, selectedFile.id());
            loadedStartOffset = estimateStartOffset(activeOffset, content);
            fileBytes = activeOffset;
            lastUpdated = LocalDateTime.now();
            updateDownloadResource();
            updateStatus("Loaded " + selectedFile.displayName());
        } catch (Exception ex) {
            updateStatus("Load failed: " + ex.getMessage());
            Notification.show("Unable to load log file", 4000, Notification.Position.BOTTOM_START);
        }
    }

    private void refreshCurrent() {
        if (activeSource == null || activeFileId == null) {
            loadSelectedFile();
            return;
        }

        try {
            String content = logViewerService.tail(activeSource, activeFileId, safeTailLines());
            setLogContent(content, true);
            activeOffset = logViewerService.fileSize(activeSource, activeFileId);
            loadedStartOffset = estimateStartOffset(activeOffset, content);
            fileBytes = activeOffset;
            lastUpdated = LocalDateTime.now();
            updateStatus("Refreshed");
        } catch (Exception ex) {
            updateStatus("Refresh failed: " + ex.getMessage());
            Notification.show("Unable to refresh log file", 4000, Notification.Position.BOTTOM_START);
        }
    }

    private void appendLiveChunk() {
        if (activeSource == null || activeFileId == null) {
            return;
        }

        try {
            LogViewerService.LogChunk chunk = logViewerService.readChunk(activeSource, activeFileId, activeOffset, CHUNK_SIZE_BYTES);
            if (chunk.rotated()) {
                String content = logViewerService.tail(activeSource, activeFileId, safeTailLines());
                setLogContent(content, true);
                activeOffset = logViewerService.fileSize(activeSource, activeFileId);
                loadedStartOffset = estimateStartOffset(activeOffset, content);
                fileBytes = activeOffset;
                lastUpdated = LocalDateTime.now();
                updateStatus("Log rotated; reloaded tail");
                return;
            }

            if (!chunk.text().isEmpty()) {
                String appended = logContent + chunk.text();
                if (appended.length() > MAX_TEXT_CHARS) {
                    int cutIndex = appended.length() - MAX_TEXT_CHARS;
                    String removedPrefix = appended.substring(0, cutIndex);
                    loadedStartOffset += removedPrefix.getBytes(StandardCharsets.UTF_8).length;
                    appended = appended.substring(cutIndex);
                }
                setLogContent(appended, Boolean.TRUE.equals(liveFollow.getValue()));
            }

            activeOffset = chunk.nextOffset();
            fileBytes = activeOffset;
            lastUpdated = LocalDateTime.now();
            updateStatus("Live follow active");
        } catch (Exception ex) {
            updateStatus("Live update failed: " + ex.getMessage());
        }
    }

    private void loadOlder() {
        if (activeSource == null || activeFileId == null) {
            loadSelectedFile();
            return;
        }

        if (loadedStartOffset <= 0) {
            updateStatus("Already at start of file");
            return;
        }

        try {
            LogViewerService.LogBackChunk older = logViewerService.readChunkBefore(
                    activeSource,
                    activeFileId,
                    loadedStartOffset,
                    CHUNK_SIZE_BYTES);

            if (older.text().isEmpty()) {
                updateStatus("No older content available");
                return;
            }

            String combined = older.text() + logContent;

            loadedStartOffset = older.startOffset();
            setLogContent(combined, false);
            lastUpdated = LocalDateTime.now();

            if (loadedStartOffset == 0) {
                updateStatus("Loaded older section (start of file reached)");
            } else {
                updateStatus("Loaded older section");
            }
        } catch (Exception ex) {
            updateStatus("Load older failed: " + ex.getMessage());
            Notification.show("Unable to load older log content", 4000, Notification.Position.BOTTOM_START);
        }
    }

    @SuppressWarnings("unused")
    private void handlePoll(PollEvent pollEvent) {
        if (Boolean.TRUE.equals(liveFollow.getValue())) {
            appendLiveChunk();
        }
    }

    private int safeTailLines() {
        Integer lines = tailLines.getValue();
        if (lines == null) {
            return 300;
        }
        return Math.max(10, Math.min(lines, 10000));
    }

    private void configurePolling(Boolean enabled) {
        getUI().ifPresent(ui -> ui.setPollInterval(Boolean.TRUE.equals(enabled) ? POLL_INTERVAL_MS : -1));
        if (Boolean.TRUE.equals(enabled) && (activeSource == null || activeFileId == null)) {
            loadSelectedFile();
        }
    }

    private void applyWordWrap(boolean wrap) {
        if (wrap) {
            logPre.getStyle().set("white-space", "pre-wrap");
            logPre.getStyle().set("min-width", "100%");
            logPre.getStyle().set("overflow-wrap", "anywhere");
            logPre.getStyle().set("word-break", "break-word");
            viewerContainer.getStyle().set("overflow-x", "hidden");
        } else {
            logPre.getStyle().set("white-space", "pre");
            // In nowrap mode, content can exceed viewport width, enabling immediate horizontal scrolling.
            logPre.getStyle().set("min-width", "max-content");
            logPre.getStyle().remove("overflow-wrap");
            logPre.getStyle().remove("word-break");
            viewerContainer.getStyle().set("overflow-x", "auto");
        }
    }

    private void setLogContent(String content, boolean scrollToLatest) {
        logContent = content == null ? "" : content;
        renderLogContent(scrollToLatest);
    }

    private long estimateStartOffset(long endOffset, String text) {
        long bytes = text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
        return Math.max(0, endOffset - bytes);
    }

    private void renderLogContent(boolean scrollToLatest) {
        String filtered = applyContainsFilter(logContent, filterContains.getValue());
        logPre.setText(filtered);
        displayBytes = filtered.getBytes(StandardCharsets.UTF_8).length;
        if (scrollToLatest) {
            scrollToLatest();
        }
    }

    private String applyContainsFilter(String input, String contains) {
        if (contains == null || contains.isBlank()) {
            return input;
        }

        String query = contains.toLowerCase();
        StringBuilder out = new StringBuilder();
        String[] lines = input.split("\\R", -1);
        for (String line : lines) {
            if (line.toLowerCase().contains(query)) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private void updateStatus(String message) {
        String updatedPart = lastUpdated == null ? "n/a" : STATUS_TIME.format(lastUpdated);
        status.setText(message + " | updated: " + updatedPart + " | view bytes: " + displayBytes + " | file bytes: " + fileBytes);
    }

    private void updateDownloadResource() {
        String source = sourceSelect.getValue();
        LogViewerService.LogFileItem selected = fileSelect.getValue();

        if (source == null || selected == null) {
            downloadLink.removeHref();
            downloadLink.getElement().removeAttribute("download");
            downloadButton.setEnabled(false);
            return;
        }

        try {
            String fileName = logViewerService.fileName(source, selected.id());
            downloadLink.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                    logViewerService.openFileStream(source, selected.id()),
                    fileName,
                    "text/plain",
                    logViewerService.fileSize(source, selected.id()))));
            downloadLink.getElement().setAttribute("download", fileName);
            downloadButton.setEnabled(true);
        } catch (Exception ex) {
            downloadLink.removeHref();
            downloadLink.getElement().removeAttribute("download");
            downloadButton.setEnabled(false);
            Notification.show("Download unavailable: " + ex.getMessage(), 3000, Notification.Position.BOTTOM_START);
        }
    }

    private void scrollToLatest() {
        viewerContainer.getElement().executeJs("this.scrollTop = this.scrollHeight;");
    }
}
