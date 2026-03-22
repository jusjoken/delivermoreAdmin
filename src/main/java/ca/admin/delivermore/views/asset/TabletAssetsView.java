package ca.admin.delivermore.views.asset;

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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Span;
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

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.service.TabletAssetRepository;
import ca.admin.delivermore.data.service.TabletAssetService;
import ca.admin.delivermore.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Asset Manager")
@Route(value = "assets", layout = MainLayout.class)
@RolesAllowed({"ADMIN", "MANAGER"})
public class TabletAssetsView extends VerticalLayout {

    private final TabletAssetService service;
    private final TabletAssetRepository assetRepo;
    private final DriversRepository driversRepo;
    private final RestaurantRepository restaurantRepo;

    private final Grid<TabletAsset> grid = new Grid<>(TabletAsset.class, false);

    // cache: fleetId -> driver name (rebuilt on refresh)
    private Map<Long, String> driverNameByFleetId = new HashMap<>();

    public TabletAssetsView(TabletAssetService service,
                            TabletAssetRepository assetRepo,
                            DriversRepository driversRepo,
                            RestaurantRepository restaurantRepo) {
        this.service = service;
        this.assetRepo = assetRepo;
        this.driversRepo = driversRepo;
        this.restaurantRepo = restaurantRepo;

        //add(new H2("Tablet Assets"));

        configureGrid();
        add(buildToolbar(), grid);

        refresh();
    }

    private HorizontalLayout buildToolbar() {
        Button newBtn = new Button("New asset", e -> openEditDialog(null, true));
        newBtn.setVisible(hasRole("ADMIN")); // managers can’t create

        Button refreshBtn = new Button("Refresh", e -> refresh());

        //add a count of unarchived assets
        Span count = new Span();
        count.getStyle().set("margin-left", "auto");
        refreshBtn.addClickListener(e -> {
            refresh();
            long total = assetRepo.countByArchivedFalse();
            count.setText(total + " total");
        });
        refreshBtn.click(); // trigger initial count display
        return new HorizontalLayout(newBtn, refreshBtn, count);
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
        
        grid.addComponentColumn(asset -> new Button("Edit", e -> openEditDialog(asset.getId(), false)))
                .setHeader("Edit");

        grid.addComponentColumn(asset -> {
            Button history = new Button("History");
            history.addClickListener(e ->
                    history.getUI().ifPresent(ui -> ui.navigate("assets/history?assetId=" + asset.getId()))
            );
            history.setVisible(hasRole("ADMIN"));
            return history;
        }).setHeader("History");

        grid.addComponentColumn(asset -> {
            Button archive = new Button("Archive");
            archive.addThemeVariants(ButtonVariant.LUMO_ERROR);

            archive.addClickListener(e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Archive asset?");
                dialog.setText("This will archive '" + safeString(asset.getAssetName()) + "'. This cannot be easily undone.");
                dialog.setCancelable(true);
                dialog.setCancelText("Cancel");

                dialog.setConfirmText("Archive");
                dialog.setConfirmButtonTheme("error primary");

                dialog.addConfirmListener(ev -> {
                    service.setArchived(asset.getId(), true);
                    refresh();
                });

                dialog.open();
            });

            archive.setVisible(hasRole("ADMIN"));
            return archive;
        }).setHeader("Archive");

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
                    service.update(editable);
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