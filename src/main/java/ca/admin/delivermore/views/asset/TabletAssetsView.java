package ca.admin.delivermore.views.asset;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;

import com.opencsv.CSVWriter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.service.TabletAssetRepository;
import ca.admin.delivermore.data.service.TabletAssetService;
import ca.admin.delivermore.data.service.TabletProvisioningService;
import ca.admin.delivermore.data.service.TabletProvisioningService.ProvisioningQrPackage;
import ca.admin.delivermore.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Asset Manager")
@Route(value = "tablet-assets", layout = MainLayout.class)
@RolesAllowed({"ADMIN", "MANAGER"})
public class TabletAssetsView extends VerticalLayout {

    private final TabletAssetService service;
    private final TabletAssetRepository assetRepo;
    private final DriversRepository driversRepo;
    private final RestaurantRepository restaurantRepo;
    private final TabletProvisioningService tabletProvisioningService;

    private final Grid<TabletAsset> grid = new Grid<>(TabletAsset.class, false);

    // cache: fleetId -> driver name (rebuilt on refresh)
    private Map<Long, String> driverNameByFleetId = new HashMap<>();

    public TabletAssetsView(TabletAssetService service,
                            TabletAssetRepository assetRepo,
                            DriversRepository driversRepo,
                            RestaurantRepository restaurantRepo,
                            TabletProvisioningService tabletProvisioningService) {
        this.service = service;
        this.assetRepo = assetRepo;
        this.driversRepo = driversRepo;
        this.restaurantRepo = restaurantRepo;
        this.tabletProvisioningService = tabletProvisioningService;

        //add(new H2("Tablet Assets"));

        configureGrid();
        add(buildToolbar(), grid);

        refresh();
    }

    private HorizontalLayout buildToolbar() {
        Button newBtn = new Button("New asset", e -> openEditDialog(null, true));
        newBtn.setVisible(hasRole("ADMIN")); // managers can’t create

        Button refreshBtn = new Button("Refresh", e -> refresh());
        Anchor exportCsvLink = new Anchor(buildCsvExportResource(), "");
        Button exportCsvBtn = new Button("Export CSV");
        exportCsvLink.add(exportCsvBtn);
        exportCsvLink.getElement().setAttribute("download", true);

        //add a count of unarchived assets
        Span count = new Span();
        count.getStyle().set("margin-left", "auto");
        refreshBtn.addClickListener(e -> {
            refresh();
            long total = assetRepo.countByArchivedFalse();
            count.setText(total + " total");
        });
        refreshBtn.click(); // trigger initial count display
        return new HorizontalLayout(newBtn, refreshBtn, exportCsvLink, count);
    }

    private StreamResource buildCsvExportResource() {
        return new StreamResource("tablet-assets.csv", () -> {
            try {
                List<TabletAsset> assets = assetRepo.findByArchivedFalseOrderByAssetNameAsc();
                assets.sort((left, right) -> {
                    int tagCompare = compareNatural(safeString(left.getAssetTag()), safeString(right.getAssetTag()));
                    if (tagCompare != 0) {
                        return tagCompare;
                    }
                    return compareNatural(safeString(left.getAssetName()), safeString(right.getAssetName()));
                });

                StringWriter output = new StringWriter();
                try (CSVWriter writer = new CSVWriter(output)) {
                    writer.writeNext(new String[] {
                            "Name",
                            "Asset tag",
                            "Assigned to",
                            "Restaurant / location",
                            "Last heartbeat",
                            "Tablet app version",
                            "Notes",
                            "Updated"
                    });

                    for (TabletAsset asset : assets) {
                        writer.writeNext(new String[] {
                                safeString(asset.getAssetName()),
                                safeString(asset.getAssetTag()),
                                resolveDriverNameFromCache(asset.getAssignedFleetId()),
                                safeString(asset.getRestaurantName()),
                                formatDateTime(asset.getLastHeartbeatAt()),
                                safeString(asset.getLastHeartbeatAppVersion()),
                                safeString(asset.getNotes()),
                                formatDateTime(asset.getUpdatedAt())
                        });
                    }
                }

                return new ByteArrayInputStream(output.toString().getBytes());
            } catch (Exception ex) {
                Notification.show("CSV export failed", 4000, Notification.Position.MIDDLE);
                return new ByteArrayInputStream(new byte[0]);
            }
        });
    }

    private void configureGrid() {
        grid.removeAllColumns();
        grid.setMultiSort(true);

        Grid.Column<TabletAsset> nameCol = grid.addColumn(TabletAsset::getAssetName).setHeader("Name").setAutoWidth(true).setSortable(true);
        Grid.Column<TabletAsset> tagCol = grid.addColumn(TabletAsset::getAssetTag)
                .setHeader("Asset tag")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator((a, b) -> compareNatural(safeString(a.getAssetTag()), safeString(b.getAssetTag())));

        // Set default sort order after columns are added
        grid.sort(List.of(
                new GridSortOrder<>(tagCol, SortDirection.ASCENDING),
                new GridSortOrder<>(nameCol, SortDirection.ASCENDING)
        ));

        grid.addColumn(asset -> resolveDriverNameFromCache(asset.getAssignedFleetId()))
                .setHeader("Assigned to")
                .setAutoWidth(true)
                .setSortable(true);

        grid.addColumn(TabletAsset::getRestaurantName).setHeader("Restaurant / location").setAutoWidth(true).setSortable(true);

        grid.addColumn(new LocalDateTimeRenderer<>(
            TabletAsset::getLastHeartbeatAt,
            () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            .setHeader("Last heartbeat")
            .setAutoWidth(true)
            .setSortable(true);

        grid.addColumn(asset -> safeString(asset.getLastHeartbeatAppVersion()))
            .setHeader("Tablet app version")
            .setAutoWidth(true)
            .setSortable(true);

        grid.addComponentColumn(asset -> {
            String notes = asset.getNotes() == null ? "" : asset.getNotes().trim();
            if (notes.isBlank()) {
                return new Span("");
            }
            return new Span("Has notes");
        }).setHeader("Notes").setAutoWidth(true);   
        
        grid.setItemDetailsRenderer(createNotesRenderer());

        grid.addColumn(new LocalDateTimeRenderer<>(
                TabletAsset::getUpdatedAt,
                () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            .setHeader("Updated").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(this::createActionsMenu)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.setAllRowsVisible(true);
    }

    private static ComponentRenderer<HorizontalLayout, TabletAsset> createNotesRenderer() {
        return new ComponentRenderer<>(HorizontalLayout::new, (layout, asset) -> {
            layout.removeAll();

            String notes = asset.getNotes() == null ? "" : asset.getNotes().trim();
            if (notes.isBlank()) {
                return;
            }

            Span text = new Span(notes);
            text.getStyle().set("white-space", "pre-wrap"); // keep line breaks
            layout.add(text);
        });
    }

    private void refresh() {
        // rebuild driver cache once
        driverNameByFleetId = new HashMap<>();
        for (Driver d : driversRepo.findActiveOrderByNameAsc()) {
            if (d.getFleetId() != null) {
                driverNameByFleetId.put(d.getFleetId(), safeString(d.getName()));
            }
        }

        grid.setItems(assetRepo.findByArchivedFalseOrderByAssetNameAsc());
    }

    private MenuBar createActionsMenu(TabletAsset asset) {
        MenuBar actionsMenu = new MenuBar();
        actionsMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        var root = actionsMenu.addItem(VaadinIcon.ELLIPSIS_DOTS_V.create());
        root.getElement().setAttribute("aria-label", "Actions");

        root.getSubMenu().addItem("Edit", e -> openEditDialog(asset.getId(), false));
        root.getSubMenu().addItem("Provision QR", e -> openProvisionDialog(asset));

        if (hasRole("ADMIN")) {
            root.getSubMenu().addItem("History", e ->
                    root.getUI().ifPresent(ui -> ui.navigate("tablet-assets/history?assetId=" + asset.getId())));
            root.getSubMenu().addItem("Archive", e -> confirmArchive(asset));
        }

        return actionsMenu;
    }

    private void confirmArchive(TabletAsset asset) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Archive asset?");
        dialog.setText("This will archive '" + safeString(asset.getAssetName()) + "'. This cannot be easily undone.");
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Archive");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(ev -> {
            try {
                service.setArchived(asset.getId(), true);
                refresh();
            } catch (IllegalStateException | OptimisticLockingFailureException ex) {
                Notification.show(
                        "Asset changed in background. Refresh and try archive again.",
                        4000,
                        Notification.Position.MIDDLE);
                refresh();
            }
        });

        dialog.open();
    }

    private void openProvisionDialog(TabletAsset asset) {
        try {
            ProvisioningQrPackage qr = tabletProvisioningService.issueProvisioningQr(
                    asset.getId(),
                    currentUsername(),
                    runtimeBaseUrl());

            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Provision tablet: " + safeString(qr.assetTag()));
            dialog.setWidth("min(96vw, 860px)");

            VerticalLayout content = new VerticalLayout();
            content.setPadding(false);
            content.setSpacing(true);
            content.setWidthFull();

            StreamResource qrResource = new StreamResource(
                    "tablet-provision-" + safeString(qr.assetTag()) + ".png",
                    () -> new java.io.ByteArrayInputStream(qr.qrPng()));

            Image qrImage = new Image(qrResource, "Tablet provisioning QR");
            qrImage.setWidth("min(82vw, 520px)");
            qrImage.getStyle().set("display", "block");
            qrImage.getStyle().set("margin", "0 auto");
            qrImage.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
            qrImage.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
            qrImage.getStyle().set("padding", "8px");

            Span detail = new Span("Restaurant: " + safeString(qr.restaurantName())
                    + " | Expires: " + qr.expiresAt());
            detail.getStyle().set("font-size", "var(--lumo-font-size-s)");
            detail.getStyle().set("color", "var(--lumo-secondary-text-color)");

            Span instruction = new Span(
                    "Open the tablet app provisioning screen and scan this QR code to claim configuration.");

            content.add(qrImage, detail, instruction);
            dialog.add(content);

            Button sendEmail = new Button("Send QR Email", e -> {
                try {
                    tabletProvisioningService.sendProvisioningQrEmail(qr);
                    Notification.show("Provisioning QR emailed to " + qr.recipientEmail(), 4000,
                            Notification.Position.MIDDLE);
                } catch (IllegalStateException ex) {
                    Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
                }
            });

            Button regenerate = new Button("Regenerate", e -> {
                dialog.close();
                TabletAsset refreshed = assetRepo.findById(asset.getId()).orElse(asset);
                openProvisionDialog(refreshed);
            });

            Button close = new Button("Close", e -> dialog.close());
            dialog.getFooter().add(sendEmail, regenerate, close);
            dialog.open();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private void openEditDialog(Long assetId, boolean isNew) {
        boolean canSave = hasRole("ADMIN") || (!isNew && hasRole("MANAGER"));

        TabletAsset editable;
        if (isNew) {
            editable = new TabletAsset();
            editable.setAssetName("");
            editable.setAssetTag("");
            editable.setRestaurantName("");
            editable.setRestaurantId(null);
            editable.setAssignedFleetId(null);
            editable.setArchived(false);
            editable.setNotes("");
        } else {
            editable = assetRepo.findById(assetId).orElse(null);
            if (editable == null) {
                Notification.show("Asset not found", 3000, Notification.Position.MIDDLE);
                return;
            }
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "New asset" : "Edit asset");

        TextField assetName = new TextField("Name");
        TextField assetTag = new TextField("Asset tag");

        List<Driver> allDrivers = driversRepo.findActiveOrderByNameAsc();
        List<Restaurant> effectiveRestaurants = restaurantRepo.getEffectiveRestaurants(LocalDate.now());

        ComboBox<Driver> assignedDriver = new ComboBox<>("Assigned driver (optional)");
        assignedDriver.setItemLabelGenerator(d -> safeString(d.getName()));
        assignedDriver.setItems(allDrivers);
        assignedDriver.setClearButtonVisible(true);

        // Build a temp list of restaurant names for the combobox
        List<String> restaurantNames = effectiveRestaurants.stream()
                .map(Restaurant::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toCollection(ArrayList::new));

        ComboBox<String> restaurantName = new ComboBox<>("Restaurant / location (optional)");
        restaurantName.setItems(restaurantNames);
        restaurantName.setClearButtonVisible(true);
        restaurantName.setAllowCustomValue(true);
        restaurantName.setPlaceholder("Select from list or type a location…");

        restaurantName.addCustomValueSetListener(e -> {
            String typed = safeString(e.getDetail()).trim();
            if (typed.isBlank()) {
                restaurantName.clear();
                return;
            }

            boolean exists = restaurantNames.stream().anyMatch(x -> x.equalsIgnoreCase(typed));
            if (!exists) {
                restaurantNames.add(typed);
                restaurantNames.sort(String::compareToIgnoreCase);
                restaurantName.setItems(restaurantNames);
            }
            restaurantName.setValue(typed);
        });     

        TextArea notes = new TextArea("Notes");
        notes.setWidthFull();
        notes.setMaxLength(1000);
        notes.setPlaceholder("Optional note about this asset (current state)...");        

        Binder<TabletAsset> binder = new Binder<>(TabletAsset.class);

        binder.forField(assetName)
                .asRequired("Name is required")
                .bind(TabletAsset::getAssetName, TabletAsset::setAssetName);

        binder.forField(assetTag)
                .asRequired("Asset tag is required")
                .bind(TabletAsset::getAssetTag, TabletAsset::setAssetTag);

        binder.forField(notes)
                .bind(
                        a -> safeString(a.getNotes()),
                        TabletAsset::setNotes
                );
        
        binder.readBean(editable);

        // Pre-select driver (match by fleetId)
        assignedDriver.clear();
        if (editable.getAssignedFleetId() != null) {
            Driver match = allDrivers.stream()
                    .filter(d -> Objects.equals(d.getFleetId(), editable.getAssignedFleetId()))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                assignedDriver.setValue(match);
            }
        }

        // Pre-select restaurant/location by restaurantName (works for both DB + manual)
        restaurantName.clear();

        String currentLocation = safeString(editable.getRestaurantName()).trim();
        if (!currentLocation.isBlank()
                && restaurantNames.stream().noneMatch(x -> x.equalsIgnoreCase(currentLocation))) {
            restaurantNames.add(currentLocation);
            restaurantNames.sort(String::compareToIgnoreCase);
            restaurantName.setItems(restaurantNames);
        }
        restaurantName.setValue(currentLocation.isBlank() ? null : currentLocation);

        FormLayout form = new FormLayout(assetName, assetTag, assignedDriver, restaurantName, notes);
        form.setColspan(notes, 2);
        dialog.add(form);

        Button save = new Button("Save", e -> {
            if (!canSave) return;

            if (binder.writeBeanIfValid(editable)) {
                // Check for duplicate asset tag before saving
                TabletAsset existing = assetRepo.findByAssetTag(editable.getAssetTag());
                if (existing != null && !Objects.equals(existing.getId(), editable.getId())) {
                    Notification.show(
                        "Asset tag '" + editable.getAssetTag() + "' is already used by asset '" + safeString(existing.getAssetName()) + "'. Please use a unique tag.",
                        5000,
                        Notification.Position.MIDDLE
                    );
                    return;
                }
                Driver d = assignedDriver.getValue();
                editable.setAssignedFleetId(d == null ? null : d.getFleetId());

                String selectedName = safeString(restaurantName.getValue()).trim();
                editable.setRestaurantName(selectedName);
                editable.setRestaurantId(null);

                if (!selectedName.isBlank()) {
                    Restaurant match = effectiveRestaurants.stream()
                            .filter(r -> r.getName() != null && r.getName().trim().equalsIgnoreCase(selectedName))
                            .findFirst()
                            .orElse(null);

                    if (match != null) {
                        editable.setRestaurantId(match.getRestaurantId()); // or getId() depending on your entity
                        editable.setRestaurantName(match.getName());       // normalize to canonical DB name
                    }
                }

                if (isNew) {
                    service.create(editable);
                } else {
                    try {
                        service.update(editable);
                    } catch (IllegalStateException | OptimisticLockingFailureException ex) {
                        Notification.show(
                                "Asset changed in background. Refresh and save your edits again.",
                                5000,
                                Notification.Position.MIDDLE);
                        refresh();
                        return;
                    }
                }

                dialog.close();
                refresh();
            }
        });
        save.setEnabled(canSave);

        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(save, cancel);

        dialog.open();
    }

    private String resolveDriverNameFromCache(Long fleetId) {
        if (fleetId == null) return "";
        return driverNameByFleetId.getOrDefault(fleetId, "");
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(value);
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;

        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals(role) || a.getAuthority().equals("ROLE_" + role)
        );
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "unknown";
        }
        return auth.getName();
    }

    private String runtimeBaseUrl() {
        if (VaadinService.getCurrentRequest() == null) {
            return "";
        }

        if (!(VaadinService.getCurrentRequest() instanceof VaadinServletRequest servletRequest)) {
            return "";
        }

        String scheme = servletRequest.getHttpServletRequest().getScheme();
        String server = servletRequest.getHttpServletRequest().getServerName();
        int port = servletRequest.getHttpServletRequest().getServerPort();

        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
                || ("http".equalsIgnoreCase(scheme) && port == 80);

        if (defaultPort) {
            return scheme + "://" + server;
        }
        return scheme + "://" + server + ":" + port;
    }

    private static int compareNatural(String left, String right) {
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            char c1 = left.charAt(i);
            char c2 = right.charAt(j);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                int s1 = i, s2 = j;
                while (i < left.length() && Character.isDigit(left.charAt(i))) i++;
                while (j < right.length() && Character.isDigit(right.charAt(j))) j++;

                String n1 = left.substring(s1, i).replaceFirst("^0+(?!$)", "");
                String n2 = right.substring(s2, j).replaceFirst("^0+(?!$)", "");

                if (n1.length() != n2.length()) return Integer.compare(n1.length(), n2.length());
                int cmpNum = n1.compareTo(n2);
                if (cmpNum != 0) return cmpNum;
            } else {
                int cmp = Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return Integer.compare(left.length(), right.length());
    }
}