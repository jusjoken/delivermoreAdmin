package ca.admin.delivermore.components.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import ca.admin.delivermore.collector.data.service.TeamsRepository;
import ca.admin.delivermore.collector.data.tookan.Team;
import ca.admin.delivermore.data.service.DeliveryZoneService;
import ca.admin.delivermore.views.UIUtilities;
import software.xdev.vaadin.maps.leaflet.MapContainer;
import software.xdev.vaadin.maps.leaflet.basictypes.LDivIcon;
import software.xdev.vaadin.maps.leaflet.basictypes.LDivIconOptions;
import software.xdev.vaadin.maps.leaflet.basictypes.LLatLng;
import software.xdev.vaadin.maps.leaflet.basictypes.LPoint;
import software.xdev.vaadin.maps.leaflet.layer.raster.LTileLayer;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarker;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarkerOptions;
import software.xdev.vaadin.maps.leaflet.layer.vector.LCircleOptions;
import software.xdev.vaadin.maps.leaflet.layer.vector.LCircle;
import software.xdev.vaadin.maps.leaflet.map.LMap;
import software.xdev.vaadin.maps.leaflet.registry.LComponentManagementRegistry;
import software.xdev.vaadin.maps.leaflet.registry.LDefaultComponentManagementRegistry;

public class DeliveryZonesDialog extends Dialog {

    private static final List<String> ZONE_COLORS = List.of(
            "#1f77b4",
            "#ff7f0e",
            "#2ca02c",
            "#d62728",
            "#9467bd",
            "#8c564b",
            "#e377c2",
            "#17becf");

    private static final String RADIUS_HANDLE_ICON_HTML = "<div style=\"width:14px;height:14px;border-radius:50%;"
            + "background:#ffffff;border:3px solid #0d6efd;box-shadow:0 1px 5px rgba(0,0,0,.35);\"></div>";

    private final DeliveryZoneService deliveryZoneService;
    private final TeamsRepository teamsRepository;
    private final LComponentManagementRegistry registry;
    private final MapContainer mapContainer;
    private final LMap map;
    private final LMarker baseMarker;
    private final List<LCircle> zoneCircles = new ArrayList<>();
    private final Select<Team> teamSelect = new Select<>();
    private final Span baseLocationSummary = new Span();
    private final Span selectedZoneSummary = new Span();
    private final NumberField radiusField = new NumberField("Radius (km)");
    private final VerticalLayout rowsLayout = new VerticalLayout();
    private final List<ZoneRow> rows = new ArrayList<>();
    private ZoneRow selectedRow;
    private LMarker radiusHandleMarker;
    private boolean updatingPreview;

    public DeliveryZonesDialog(
            DeliveryZoneService deliveryZoneService,
            TeamsRepository teamsRepository,
            Long initialTeamId) {
        this.deliveryZoneService = deliveryZoneService;
        this.teamsRepository = teamsRepository;
        setId("delivery-zones-dialog-" + UUID.randomUUID());

        setHeaderTitle("Delivery Zones");
        setWidth("1200px");
        setMaxWidth("95vw");
        setHeight("860px");
        setMaxHeight("95vh");
        setResizable(true);

        registry = new LDefaultComponentManagementRegistry(this);
        mapContainer = new MapContainer(registry);
        mapContainer.setWidthFull();
        mapContainer.setHeight("420px");
        map = mapContainer.getlMap();
        map.addLayer(LTileLayer.createDefaultForOpenStreetMapTileServer(registry));
        baseMarker = new LMarker(registry, new LLatLng(registry, 51.037d, -113.402d)).addTo(map);

        selectedZoneSummary.getStyle().set("color", "var(--lumo-secondary-text-color)");
        selectedZoneSummary.getStyle().set("font-size", "var(--lumo-font-size-s)");

        radiusField.setMin(0.5d);
        radiusField.setStep(0.1d);
        radiusField.setWidth("140px");
        radiusField.addValueChangeListener(event -> {
            if (updatingPreview || selectedRow == null) {
                return;
            }
            Double value = event.getValue();
            if (value != null) {
                selectedRow.maxDistanceField().setValue(Math.max(0.5d, value));
                refreshMapPreview();
            }
        });

        teamSelect.setLabel("Base location");
        teamSelect.setItems(teamsRepository.findByActiveTrueOrderByTeamNameAsc());
        teamSelect.setItemLabelGenerator(Team::getTeamName);
        teamSelect.addValueChangeListener(event -> loadZonesForSelectedTeam());

        rowsLayout.setPadding(false);
        rowsLayout.setSpacing(true);
        rowsLayout.setWidthFull();

        Button addZoneButton = new Button("Add zone", VaadinIcon.PLUS.create(), event -> addRow(null));
        addZoneButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout radiusControls = new HorizontalLayout(radiusField);
        radiusControls.setWidthFull();
        radiusControls.setAlignItems(FlexComponent.Alignment.END);

        VerticalLayout mapPanel = new VerticalLayout(selectedZoneSummary, radiusControls, mapContainer);
        mapPanel.setPadding(false);
        mapPanel.setSpacing(true);
        mapPanel.setWidthFull();
        mapPanel.setFlexGrow(1, mapContainer);

        VerticalLayout body = new VerticalLayout(teamSelect, baseLocationSummary, mapPanel, addZoneButton, rowsLayout);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        add(body);

        Button cancel = new Button("Cancel", event -> close());
        Button save = new Button("Save zones", event -> saveZones());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, save);

        Team initialTeam = initialTeamId == null ? null : teamsRepository.findByTeamId(initialTeamId);
        if (initialTeam == null) {
            initialTeam = teamsRepository.findByActiveTrueOrderByTeamNameAsc().stream()
                    .filter(team -> team.getTeamName() != null && team.getTeamName().trim().equalsIgnoreCase("Strathmore"))
                    .findFirst()
                    .orElseGet(() -> {
                        List<Team> teams = teamsRepository.findByActiveTrueOrderByTeamNameAsc();
                        return teams.isEmpty() ? null : teams.get(0);
                    });
        }
        teamSelect.setValue(initialTeam);
        loadZonesForSelectedTeam();

        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                map.fixInvalidSizeAfterCreation(null);
                refreshMapPreview();
            }
        });
    }

    private void loadZonesForSelectedTeam() {
        rows.clear();
        rowsLayout.removeAll();
        selectedRow = null;

        Team team = teamSelect.getValue();
        if (team == null) {
            baseLocationSummary.setText("Select a base location to configure delivery zones.");
            selectedZoneSummary.setText("Select a base location and then choose a zone to preview it on the map.");
            refreshMapPreview();
            return;
        }

        baseLocationSummary.setText(deliveryZoneService.getBaseLocation(team.getTeamId())
                .map(DeliveryZoneService.BaseLocation::summary)
                .orElse("This base location is missing address and coordinate data."));

        List<DeliveryZoneService.DeliveryZoneConfig> configured = deliveryZoneService.listZonesForTeam(team.getTeamId());
        if (configured.isEmpty()) {
            addRow(new DeliveryZoneService.DeliveryZoneConfig("Zone 1", 5d, 4.50d, true, defaultZoneColor(0)));
        } else {
            for (int index = 0; index < configured.size(); index++) {
                addRow(configured.get(index), index);
            }
        }

        if (!rows.isEmpty()) {
            selectRow(rows.get(0));
        } else {
            selectedZoneSummary.setText("Add a zone to preview it on the map.");
            refreshMapPreview();
        }
    }

    private void addRow(DeliveryZoneService.DeliveryZoneConfig zone) {
        addRow(zone, rows.size());
    }

    private void addRow(DeliveryZoneService.DeliveryZoneConfig zone, int index) {
        ZoneRow row = new ZoneRow(zone, index);
        rows.add(row);
        rowsLayout.add(row.layout());
        if (selectedRow == null) {
            selectRow(row);
        }
    }

    private void selectRow(ZoneRow row) {
        selectedRow = row;
        if (row == null) {
            selectedZoneSummary.setText("Select a zone to preview it on the map.");
            refreshMapPreview();
            return;
        }

        row.maxDistanceField().setValue(row.maxDistanceField().getValue() == null ? 5d : row.maxDistanceField().getValue());
        updatingPreview = true;
        try {
            radiusField.setValue(row.maxDistanceField().getValue());
        } finally {
            updatingPreview = false;
        }
        selectedZoneSummary.setText(buildSelectedZoneSummary(row));
        refreshMapPreview();
    }

    private String buildSelectedZoneSummary(ZoneRow row) {
        String name = row.nameField().getValue() == null || row.nameField().getValue().isBlank()
                ? "Selected zone"
                : row.nameField().getValue().trim();
        Double radius = row.maxDistanceField().getValue();
        Double fee = row.feeField().getValue();
        return String.format(Locale.CANADA, "%s | Radius %s km | Fee %s | Color %s",
                name,
                radius == null ? "0.0" : String.format(Locale.CANADA, "%.1f", radius),
                UIUtilities.getNumberFormatted(fee == null ? 0d : fee),
                normalizeColor(row.colorField().getValue(), row.index()));
    }

    private void refreshMapPreview() {
        Team team = teamSelect.getValue();
        if (team == null) {
            clearZoneLayers();
            selectedZoneSummary.setText("Select a base location and then choose a zone to preview it on the map.");
            return;
        }

        Double latitude = parseDouble(team.getLatitude());
        Double longitude = parseDouble(team.getLongitude());
        if (latitude == null || longitude == null) {
            clearZoneLayers();
            selectedZoneSummary.setText("This base location does not have coordinates yet.");
            return;
        }

        LLatLng center = new LLatLng(registry, latitude, longitude);
        baseMarker.setLatLng(center);
        double maxRadius = 5d;

        clearZoneLayers();
        for (ZoneRow row : rows) {
            if (!row.layout().isVisible()) {
                continue;
            }

            Double radiusKm = normalizedRadius(row.maxDistanceField().getValue());
            if (radiusKm == null) {
                continue;
            }

            maxRadius = Math.max(maxRadius, radiusKm);
            String color = normalizeColor(row.colorField().getValue(), row.index());
            zoneCircles.add(createZoneCircle(center, radiusKm, color, row == selectedRow));
        }

        map.setView(center, zoomForRadius(maxRadius));

        if (selectedRow == null) {
            selectedZoneSummary.setText("Select a zone to preview it on the map.");
            removeRadiusHandle();
            return;
        }

        Double radiusKm = normalizedRadius(selectedRow.maxDistanceField().getValue());
        if (radiusKm == null) {
            selectedZoneSummary.setText("Select a valid radius for the chosen zone.");
            removeRadiusHandle();
            return;
        }

        selectedZoneSummary.setText(buildSelectedZoneSummary(selectedRow));
        updateRadiusHandle(center, latitude, longitude, radiusKm);
    }

    private void clearZoneLayers() {
        for (LCircle circle : zoneCircles) {
            circle.remove();
        }
        zoneCircles.clear();
    }

    private void removeRadiusHandle() {
        if (radiusHandleMarker != null) {
            radiusHandleMarker.remove();
            radiusHandleMarker = null;
        }
    }

    private LCircle createZoneCircle(LLatLng center, double radiusKm, String color, boolean selected) {
        LCircleOptions options = new LCircleOptions();
        options.setRadius(radiusKm * 1000d);
        options.setColor(color);
        options.setFillColor(color);
        options.setFillOpacity(selected ? 0.22d : 0.14d);
        options.setOpacity(selected ? 0.9d : 0.65d);
        options.setWeight(selected ? 4d : 2d);
        return new LCircle(registry, center, options).addTo(map);
    }

    private void updateRadiusHandle(LLatLng center, double baseLatitude, double baseLongitude, double radiusKm) {
        LLatLng handlePoint = pointAtDistance(baseLatitude, baseLongitude, radiusKm, 90d);
        if (radiusHandleMarker == null) {
            LDivIconOptions iconOptions = new LDivIconOptions();
            iconOptions.setHtml(RADIUS_HANDLE_ICON_HTML);
            iconOptions.setIconSize(new LPoint(registry, 14, 14));
            iconOptions.setIconAnchor(new LPoint(registry, 7, 7));
            LMarkerOptions options = new LMarkerOptions()
                    .withDraggable(true)
                    .withOpacity(0.95d)
                .withTitle("Drag to resize the zone radius")
                .withIcon(new LDivIcon(registry, iconOptions));
            radiusHandleMarker = new LMarker(registry, handlePoint, options).addTo(map);
            String dragFuncReference = radiusHandleMarker.clientComponentJsAccessor() + ".deliverMoreZoneRadiusHandle";
            registry.execJs(dragFuncReference + "=e => document.getElementById('" + getId().orElse("")
                    + "').$server.radiusHandleDragged(e.target.getLatLng().lat, e.target.getLatLng().lng)");
            radiusHandleMarker.on("dragend", dragFuncReference);
        } else {
            radiusHandleMarker.setLatLng(handlePoint);
        }
    }

    private int zoomForRadius(double radiusKm) {
        if (radiusKm <= 3d) {
            return 13;
        }
        if (radiusKm <= 8d) {
            return 12;
        }
        if (radiusKm <= 15d) {
            return 11;
        }
        if (radiusKm <= 30d) {
            return 10;
        }
        return 9;
    }

    @ClientCallable
    @SuppressWarnings("unused")
    private void radiusHandleDragged(double latitude, double longitude) {
        if (selectedRow == null || updatingPreview) {
            return;
        }

        Team team = teamSelect.getValue();
        if (team == null) {
            return;
        }

        Double baseLatitude = parseDouble(team.getLatitude());
        Double baseLongitude = parseDouble(team.getLongitude());
        if (baseLatitude == null || baseLongitude == null) {
            return;
        }

        double radiusKm = distanceKm(baseLatitude, baseLongitude, latitude, longitude);
        updatingPreview = true;
        try {
            selectedRow.maxDistanceField().setValue(Math.max(0.5d, round1(radiusKm)));
            radiusField.setValue(Math.max(0.5d, round1(radiusKm)));
        } finally {
            updatingPreview = false;
        }
        refreshMapPreview();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void saveZones() {
        Team team = teamSelect.getValue();
        if (team == null) {
            UIUtilities.showNotification("Select a base location first");
            return;
        }

        List<DeliveryZoneService.DeliveryZoneConfig> configured = new ArrayList<>();
        int colorIndex = 0;
        for (ZoneRow row : rows) {
            if (!row.layout().isVisible()) {
                continue;
            }
            String name = row.nameField().getValue() == null ? "" : row.nameField().getValue().trim();
            Double maxDistanceKm = row.maxDistanceField().getValue();
            Double fee = row.feeField().getValue();
            if (name.isBlank() && maxDistanceKm == null && fee == null) {
                continue;
            }
            if (name.isBlank() || maxDistanceKm == null || maxDistanceKm <= 0d) {
                UIUtilities.showNotification("Each delivery zone needs a name and a positive max distance.");
                return;
            }
            configured.add(new DeliveryZoneService.DeliveryZoneConfig(
                    name,
                    maxDistanceKm,
                    fee,
                    row.activeField().getValue(),
                    normalizeColor(row.colorField().getValue(), colorIndex)));
            colorIndex++;
        }

        deliveryZoneService.saveZonesForTeam(team.getTeamId(), configured);
        UIUtilities.showNotification("Delivery zones updated for " + team.getTeamName());
        close();
    }

    private final class ZoneRow {

        private final int index;
        private final HorizontalLayout layout = new HorizontalLayout();
        private final TextField nameField = new TextField("Zone name");
        private final NumberField maxDistanceField = UIUtilities.getNumberField("Max km", false, null);
        private final NumberField feeField = UIUtilities.getNumberField("Fee", false, "$");
        private final Input colorField = new Input();
        private final Checkbox activeField = new Checkbox("Active");

        private ZoneRow(DeliveryZoneService.DeliveryZoneConfig zone, int index) {
            this.index = index;
            layout.setWidthFull();
            layout.setAlignItems(FlexComponent.Alignment.END);
            layout.setFlexGrow(1, nameField, maxDistanceField, feeField, colorField);

            nameField.setWidthFull();
            maxDistanceField.setMin(0.1d);
            maxDistanceField.setStep(0.1d);
            maxDistanceField.setWidthFull();
            maxDistanceField.addValueChangeListener(event -> {
                if (selectedRow == this) {
                    updatingPreview = true;
                    try {
                        radiusField.setValue(event.getValue());
                    } finally {
                        updatingPreview = false;
                    }
                    refreshMapPreview();
                }
            });
            feeField.setMin(0d);
            feeField.setStep(0.25d);
            feeField.setWidthFull();

            colorField.setWidth("80px");
            colorField.getElement().setAttribute("type", "color");
            colorField.getElement().setAttribute("title", "Zone color");
            colorField.addValueChangeListener(event -> {
                if (selectedRow == this) {
                    refreshMapPreview();
                    selectedZoneSummary.setText(buildSelectedZoneSummary(this));
                }
            });

            if (zone != null) {
                nameField.setValue(zone.name() == null ? "" : zone.name());
                if (zone.maxDistanceKm() != null) {
                    maxDistanceField.setValue(zone.maxDistanceKm());
                }
                if (zone.fee() != null) {
                    feeField.setValue(zone.fee());
                }
                colorField.setValue(normalizeColor(zone.color(), index));
                activeField.setValue(zone.active());
            } else {
                colorField.setValue(defaultZoneColor(index));
                activeField.setValue(true);
            }

            Button preview = new Button(VaadinIcon.MAP_MARKER.create(), event -> selectRow(this));
            preview.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            preview.setTooltipText("Preview on map");

            Button remove = new Button(VaadinIcon.TRASH.create(), event -> layout.getParent().ifPresent(parent -> {
                parent.getElement().removeChild(layout.getElement());
                layout.setVisible(false);
                nameField.clear();
                maxDistanceField.clear();
                feeField.clear();
                activeField.setValue(false);
                if (selectedRow == this) {
                    selectedRow = null;
                    ZoneRow nextRow = rows.stream()
                            .filter(row -> row != this && row.layout.isVisible())
                            .findFirst()
                            .orElse(null);
                    if (nextRow != null) {
                        selectRow(nextRow);
                    } else {
                        refreshMapPreview();
                    }
                }
            }));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY_INLINE);
            layout.add(nameField, maxDistanceField, feeField, colorField, activeField, preview, remove);
        }

        private HorizontalLayout layout() {
            return layout;
        }

        private TextField nameField() {
            return nameField;
        }

        private NumberField maxDistanceField() {
            return maxDistanceField;
        }

        private NumberField feeField() {
            return feeField;
        }

        private Checkbox activeField() {
            return activeField;
        }

        private Input colorField() {
            return colorField;
        }

        private int index() {
            return index;
        }
    }

    private String defaultZoneColor(int index) {
        return ZONE_COLORS.get(Math.floorMod(index, ZONE_COLORS.size()));
    }

    private String normalizeColor(String color, int index) {
        if (color == null || color.isBlank()) {
            return defaultZoneColor(index);
        }
        return color.trim();
    }

    private Double normalizedRadius(Double value) {
        if (value == null || value <= 0d) {
            return null;
        }
        return value;
    }

    private double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0088d;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2d) * Math.sin(latDistance / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2d) * Math.sin(lonDistance / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthRadiusKm * c;
    }

    private LLatLng pointAtDistance(double baseLatitude, double baseLongitude, double radiusKm, double bearingDegrees) {
        double earthRadiusKm = 6371.0088d;
        double angularDistance = radiusKm / earthRadiusKm;
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(baseLatitude);
        double lon1 = Math.toRadians(baseLongitude);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance)
                + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        return new LLatLng(registry, Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
}