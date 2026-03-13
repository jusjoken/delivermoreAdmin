package ca.admin.delivermore.views.asset;

import ca.admin.delivermore.collector.data.service.DriversRepository;
import ca.admin.delivermore.collector.data.tookan.Driver;
import ca.admin.delivermore.data.entity.TabletAsset;
import ca.admin.delivermore.data.entity.TabletAssetHistory;
import ca.admin.delivermore.data.service.TabletAssetHistoryRepository;
import ca.admin.delivermore.data.service.TabletAssetRepository;
import ca.admin.delivermore.views.MainLayout;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@PageTitle("Asset History Browser")
@Route(value = "assets/history", layout = MainLayout.class)
@RolesAllowed({"ADMIN"})
public class TabletAssetHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private static final String ASSET_ID_QP = "assetId";

    private final TabletAssetHistoryRepository historyRepo;
    private final TabletAssetRepository assetRepo;
    private final DriversRepository driversRepo;

    private final Grid<TabletAssetHistory> grid = new Grid<>(TabletAssetHistory.class, false);

    private final Span subtitle = new Span("Select an asset to view its history.");

    private final ComboBox<TabletAsset> assetSelect = new ComboBox<>("Asset");

    // cache: fleetId -> driver name (rebuilt on refresh)
    private Map<Long, String> driverNameByFleetId = new HashMap<>();

    private Long assetId;

    public TabletAssetHistoryView(TabletAssetHistoryRepository historyRepo,
                                  DriversRepository driversRepo,TabletAssetRepository assetRepo) {
        this.historyRepo = historyRepo;
        this.assetRepo = assetRepo;
        this.driversRepo = driversRepo;

        setSizeFull();
        grid.setSizeFull();

        Button back = new Button("Back");
        back.addClickListener(e -> UI.getCurrent().navigate("assets"));

        configureAssetSelect();

        HorizontalLayout header = new HorizontalLayout(back);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        assetSelect.setWidth("520px");

        HorizontalLayout toolbar = new HorizontalLayout(assetSelect);
        toolbar.setAlignItems(Alignment.END);
        toolbar.setSpacing(true);

        add(header, subtitle, toolbar, grid);

        configureGrid();

        // initial empty state
        grid.setItems(List.of());
    }
    
    private static ComponentRenderer<HorizontalLayout, TabletAssetHistory> createNotesRenderer() {
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

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Read optional assetId from query string: /assets/history?assetId=123
        Optional<String> qp = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault(ASSET_ID_QP, List.of())
                .stream()
                .findFirst();

        Long qpAssetId = qp.flatMap(this::tryParseLong).orElse(null);
        if (qpAssetId == null) {
            // no asset selected in URL
            assetId = null;
            assetSelect.clear();
            subtitle.setText("Select an asset to view its history.");
            grid.setItems(List.of());
            return;
        }

        // If it's a valid id, select it and load
        TabletAsset current = assetRepo.findById(qpAssetId).orElse(null);
        if (current == null) {
            assetId = null;
            assetSelect.clear();
            subtitle.setText("Asset not found: " + qpAssetId);
            grid.setItems(List.of());
            replaceUrlToAsset(null); // clears query param
            return;
        }

        // Setting value will trigger listener; listener has guard, so OK.
        assetSelect.setValue(current);
    }

    private void configureAssetSelect() {
        List<TabletAsset> assets = assetRepo.findAll().stream()
                .sorted(Comparator.comparing(a -> safeString(a.getAssetName()).toLowerCase()))
                .toList();

        assetSelect.setItems(assets);
        assetSelect.setClearButtonVisible(true);

        assetSelect.setItemLabelGenerator(a -> {
            String name = safeString(a.getAssetName());
            String tag = safeString(a.getAssetTag());
            String archived = a.isArchived() ? " [ARCHIVED]" : "";
            if (!tag.isBlank()) return name + " (" + tag + ")" + archived;
            return name + archived;
        });

        assetSelect.addValueChangeListener(e -> {
            TabletAsset selected = e.getValue();

            if (selected == null) {
                assetId = null;
                subtitle.setText("Select an asset to view its history.");
                grid.setItems(List.of());
                replaceUrlToAsset(null); // clear assetId from URL
                return;
            }

            if (selected.getId() == null) return;
            if (assetId != null && assetId.equals(selected.getId())) return; // guard

            assetId = selected.getId();
            subtitle.setText("Asset ID: " + assetId);
            refresh();

            // Update URL without adding a browser history entry
            replaceUrlToAsset(assetId);
        });
    }

    private void replaceUrlToAsset(Long assetId) {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        String path = "assets/history";
        if (assetId != null) {
            path += "?" + ASSET_ID_QP + "=" + assetId;
        }

        ui.getPage().getHistory().replaceState(null, path);
    }

    private void configureGrid() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        grid.addColumn(h -> h.getChangedAt() == null ? "" : fmt.format(h.getChangedAt()))
                .setHeader("Changed at")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(h -> safeString(h.getChangedBy()))
                .setHeader("Changed by")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(h -> h.getChangeType() == null ? "" : h.getChangeType().name())
                .setHeader("Type")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(TabletAssetHistory::getAssetName)
                .setHeader("Name")
                .setAutoWidth(true);

        grid.addColumn(TabletAssetHistory::getAssetTag)
                .setHeader("Asset tag")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(asset -> resolveDriverNameFromCache(asset.getAssignedFleetId()))
                .setHeader("Assigned to")
                .setAutoWidth(true);

        grid.addColumn(h -> h.getAssignedFleetId() == null ? "" : h.getAssignedFleetId().toString())
                .setHeader("Assigned fleetId")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(TabletAssetHistory::getRestaurantName)
                .setHeader("Restaurant / location")
                .setAutoWidth(true);

        grid.addColumn(h -> h.getRestaurantId() == null ? "" : h.getRestaurantId().toString())
                .setHeader("RestaurantId")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addColumn(h -> h.isArchived() ? "Yes" : "")
                .setHeader("Archived")
                .setAutoWidth(true)
                .setFlexGrow(0);

        grid.addComponentColumn(asset -> {
            String notes = asset.getNotes() == null ? "" : asset.getNotes().trim();
            if (notes.isBlank()) {
                return new Span("");
            }
            return new Span("Has notes");
        }).setHeader("Notes").setAutoWidth(true);   
        
        grid.setItemDetailsRenderer(createNotesRenderer());
        
    }

    private void refresh() {
        // rebuild driver cache once
        driverNameByFleetId = new HashMap<>();
        for (Driver d : driversRepo.findAll()) {
            if (d.getFleetId() != null) {
                driverNameByFleetId.put(d.getFleetId(), safeString(d.getName()));
            }
        }

        if (assetId == null) {
            grid.setItems(List.of());
            return;
        }

        List<TabletAssetHistory> items = historyRepo.findByTabletAssetIdOrderByChangedAtDesc(assetId);

        grid.setItems(items);
    }

     private String resolveDriverNameFromCache(Long fleetId) {
        if (fleetId == null) return "";
        return driverNameByFleetId.getOrDefault(fleetId, "");
    }
    
    private Optional<Long> tryParseLong(String s) {
        try {
            return Optional.of(Long.parseLong(s));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }
}