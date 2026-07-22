package ca.admin.delivermore.components.custom;

import java.util.UUID;
import java.util.function.Consumer;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import software.xdev.vaadin.maps.leaflet.MapContainer;
import software.xdev.vaadin.maps.leaflet.basictypes.LDivIcon;
import software.xdev.vaadin.maps.leaflet.basictypes.LDivIconOptions;
import software.xdev.vaadin.maps.leaflet.basictypes.LLatLng;
import software.xdev.vaadin.maps.leaflet.basictypes.LPoint;
import software.xdev.vaadin.maps.leaflet.layer.raster.LTileLayer;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarker;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarkerOptions;
import software.xdev.vaadin.maps.leaflet.map.LMap;
import software.xdev.vaadin.maps.leaflet.registry.LComponentManagementRegistry;
import software.xdev.vaadin.maps.leaflet.registry.LDefaultComponentManagementRegistry;

public class LeafletPointPickerDialog extends Dialog {

    private static final String PICKER_MARKER_HTML = "<div style=\"width:16px;height:16px;border-radius:50%;"
            + "background:#ffffff;border:3px solid #d62728;box-shadow:0 1px 5px rgba(0,0,0,.35);\"></div>";

    public record SelectedPoint(Double latitude, Double longitude) {
    }

    private final Consumer<SelectedPoint> onConfirm;
    private final LComponentManagementRegistry registry;
    private final MapContainer mapContainer;
    private final LMap map;
    private final Span coordinatesLabel = new Span("Click the map to choose a location.");

    private LMarker selectedMarker;
    private final LMarkerOptions selectedMarkerOptions;
    private Double selectedLatitude;
    private Double selectedLongitude;

    public LeafletPointPickerDialog(
            String title,
            String helpText,
            SelectedPoint centerPoint,
            SelectedPoint initialSelection,
            Consumer<SelectedPoint> onConfirm) {
        this.onConfirm = onConfirm;
        setId("leaflet-point-picker-" + UUID.randomUUID());
        setHeaderTitle(title);
        setWidth("960px");
        setMaxWidth("95vw");
        setHeight("760px");
        setMaxHeight("95vh");
        setResizable(true);

        registry = new LDefaultComponentManagementRegistry(this);
        mapContainer = new MapContainer(registry);
        mapContainer.setSizeFull();

        map = mapContainer.getlMap();
        map.addLayer(LTileLayer.createDefaultForOpenStreetMapTileServer(registry));

        LDivIconOptions markerIconOptions = new LDivIconOptions();
        markerIconOptions.setHtml(PICKER_MARKER_HTML);
        markerIconOptions.setIconSize(new LPoint(registry, 16, 16));
        markerIconOptions.setIconAnchor(new LPoint(registry, 8, 8));
        selectedMarkerOptions = new LMarkerOptions()
            .withTitle("Selected location")
            .withIcon(new LDivIcon(registry, markerIconOptions));

        SelectedPoint safeCenter = centerPoint != null && centerPoint.latitude() != null && centerPoint.longitude() != null
                ? centerPoint
                : new SelectedPoint(51.037d, -113.402d);
        int initialZoom = initialSelection != null && initialSelection.latitude() != null && initialSelection.longitude() != null ? 16 : 12;
        map.setView(new LLatLng(registry, safeCenter.latitude(), safeCenter.longitude()), initialZoom);

        if (initialSelection != null && initialSelection.latitude() != null && initialSelection.longitude() != null) {
            updateSelectedPoint(initialSelection.latitude(), initialSelection.longitude(), false);
        }

        String clickFuncReference = map.clientComponentJsAccessor() + ".deliverMorePointPicker";
        registry.execJs(clickFuncReference + "=e => document.getElementById('" + getId().orElseThrow()
                + "').$server.mapClicked(e.latlng.lat, e.latlng.lng)");
        map.on("click", clickFuncReference);

        Span help = new Span(helpText == null || helpText.isBlank()
                ? "Click the map to confirm the exact delivery point."
                : helpText);
        help.getStyle().set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout body = new VerticalLayout(help, coordinatesLabel, mapContainer);
        body.setPadding(false);
        body.setSpacing(true);
        body.setSizeFull();
        body.setFlexGrow(1, mapContainer);
        add(body);

        Button cancel = new Button("Cancel", event -> close());
        Button confirm = new Button("Use selected location", event -> confirmSelection());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(cancel, confirm);

        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                map.fixInvalidSizeAfterCreation(null);
            }
        });
    }

    @ClientCallable
    @SuppressWarnings("unused")
    private void mapClicked(double latitude, double longitude) {
        updateSelectedPoint(latitude, longitude, true);
    }

    private void updateSelectedPoint(double latitude, double longitude, boolean panToPoint) {
        selectedLatitude = round(latitude);
        selectedLongitude = round(longitude);
        LLatLng latLng = new LLatLng(registry, selectedLatitude, selectedLongitude);
        if (selectedMarker == null) {
            selectedMarker = new LMarker(registry, latLng, selectedMarkerOptions).addTo(map);
        } else {
            selectedMarker.setLatLng(latLng);
        }
        if (panToPoint) {
            map.panTo(latLng);
        }
        coordinatesLabel.setText(String.format("Selected coordinates: %.6f, %.6f", selectedLatitude, selectedLongitude));
    }

    private void confirmSelection() {
        if (selectedLatitude == null || selectedLongitude == null) {
            Notification notification = Notification.show("Select a point on the map first", 2500, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        onConfirm.accept(new SelectedPoint(selectedLatitude, selectedLongitude));
        close();
    }

    private double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}