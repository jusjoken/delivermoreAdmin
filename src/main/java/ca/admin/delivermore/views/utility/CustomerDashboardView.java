package ca.admin.delivermore.views.utility;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Receiver;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.data.service.CustomerProfileService;
import ca.admin.delivermore.data.service.CustomerProfileService.CustomerTopRow;
import ca.admin.delivermore.data.service.CustomerProfileService.DashboardSnapshot;
import ca.admin.delivermore.data.service.CustomerProfileService.ImportedCustomerRecord;
import ca.admin.delivermore.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Customers")
@Route(value = "customers", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class CustomerDashboardView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(CustomerDashboardView.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CANADA);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", Locale.CANADA);
    private static final DateTimeFormatter LAST_ORDER_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm a", Locale.CANADA);

    private final RestaurantRepository restaurantRepository;
    private final CustomerProfileService customerProfileService;

    private final Select<RestaurantOption> restaurantSelect = new Select<>();
    private final Span importSummary = new Span("Select a restaurant, then upload a customer CSV export.");

    private final DatePicker startDate = new DatePicker("From");
    private final DatePicker endDate = new DatePicker("To");
    private final Span totalProfiles = new Span();
    private final Span newProfiles = new Span();
    private final Span existingProfiles = new Span();
    private final Span overlapProfiles = new Span();

    private final Grid<CustomerTopRow> topCustomersGrid = new Grid<>();

    public CustomerDashboardView(RestaurantRepository restaurantRepository, CustomerProfileService customerProfileService) {
        this.restaurantRepository = restaurantRepository;
        this.customerProfileService = customerProfileService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(buildImportCard());
        add(buildDashboardCard());

        loadRestaurants();
        refreshDashboard();
    }

    private Component buildImportCard() {
        VerticalLayout card = createCard();
        card.add(new H3("Customer Import (one-time / occasional)"));

        restaurantSelect.setLabel("Restaurant");
        restaurantSelect.setItemLabelGenerator(RestaurantOption::label);
        restaurantSelect.setWidth("380px");

        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFiles(1);
        upload.setAutoUpload(true);
        upload.addSucceededListener(event -> {
            RestaurantOption selected = restaurantSelect.getValue();
            if (selected == null) {
                showError("Choose a restaurant before importing.");
                return;
            }

            try {
                ImportCounters counters = importCsv(
                        selected.restaurant(),
                        new InputStreamReader(buffer.getInputStream(event.getFileName()), StandardCharsets.UTF_8));
                importSummary.setText(String.format(
                        Locale.CANADA,
                        "Imported %d rows. New profiles: %d, matched profiles: %d, conflicts: %d, errors: %d",
                        counters.processed,
                        counters.newProfiles,
                        counters.matchedProfiles,
                        counters.conflicts,
                        counters.errors));
                refreshDashboard();
                showSuccess("Customer import complete.");
            } catch (Exception ex) {
                log.error("Customer import failed", ex);
                showError("Import failed: " + ex.getMessage());
            }
        });

        importSummary.getStyle().set("color", "var(--lumo-secondary-text-color)");

        card.add(restaurantSelect, upload, importSummary);
        return card;
    }

    private Component buildDashboardCard() {
        VerticalLayout card = createCard();
        card.setSizeFull();
        card.add(new H3("Customer Dashboard"));

        startDate.setValue(LocalDate.now().minusDays(30));
        endDate.setValue(LocalDate.now());

        Button refresh = new Button("Refresh", event -> refreshDashboard());
        refresh.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout filters = new HorizontalLayout(startDate, endDate, refresh);
        filters.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout kpis = new HorizontalLayout(
                createKpi("Total profiles", totalProfiles),
                createKpi("New in range", newProfiles),
                createKpi("Existing in range", existingProfiles),
                createKpi("Cross-restaurant", overlapProfiles));
        kpis.setWidthFull();
        kpis.setSpacing(true);

        configureTopCustomersGrid();

        card.add(filters, kpis, topCustomersGrid);
        card.setFlexGrow(1, topCustomersGrid);
        return card;
    }

    private void configureTopCustomersGrid() {
        topCustomersGrid.setWidthFull();
        topCustomersGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        topCustomersGrid.addColumn(CustomerTopRow::customerProfileId).setHeader("Profile Id").setAutoWidth(true).setFlexGrow(0);
        topCustomersGrid.addColumn(CustomerTopRow::fullName).setHeader("Name").setAutoWidth(true);
        topCustomersGrid.addColumn(CustomerTopRow::email).setHeader("Email").setAutoWidth(true);
        topCustomersGrid.addColumn(CustomerTopRow::phone).setHeader("Phone").setAutoWidth(true);
        topCustomersGrid.addColumn(CustomerTopRow::totalOrders).setHeader("Orders").setAutoWidth(true).setFlexGrow(0);
        topCustomersGrid.addColumn(row -> String.format(Locale.CANADA, "$%.2f", row.totalSpent())).setHeader("Spent").setAutoWidth(true).setFlexGrow(0);
        topCustomersGrid.addColumn(row -> row.lastOrderAt() == null
            ? ""
            : row.lastOrderAt().format(LAST_ORDER_DISPLAY_FORMAT)).setHeader("Last Order").setAutoWidth(true);
        topCustomersGrid.addColumn(CustomerTopRow::lastRestaurantName).setHeader("Last Restaurant").setAutoWidth(true);
    }

    private void loadRestaurants() {
        List<RestaurantOption> options = restaurantRepository.getEffectiveRestaurants(LocalDate.now()).stream()
                .map(restaurant -> new RestaurantOption(restaurant, restaurant.getName() + " (" + restaurant.getRestaurantId() + ")"))
                .toList();
        restaurantSelect.setItems(options);
        if (!options.isEmpty()) {
            restaurantSelect.setValue(options.getFirst());
        }
    }

    private void refreshDashboard() {
        DashboardSnapshot snapshot = customerProfileService.buildDashboardSnapshot(startDate.getValue(), endDate.getValue());
        totalProfiles.setText(String.valueOf(snapshot.totalProfiles()));
        newProfiles.setText(String.valueOf(snapshot.newProfilesInRange()));
        existingProfiles.setText(String.valueOf(snapshot.existingProfilesInRange()));
        overlapProfiles.setText(String.valueOf(snapshot.multiRestaurantProfiles()));
        topCustomersGrid.setItems(snapshot.topProfiles());
    }

    private ImportCounters importCsv(Restaurant restaurant, InputStreamReader reader) throws Exception {
        ImportCounters counters = new ImportCounters();

        try (CSVReader csvReader = new CSVReader(reader)) {
            String[] header = csvReader.readNext();
            if (header == null || header.length == 0) {
                throw new IllegalArgumentException("CSV is empty");
            }

            Map<String, Integer> columns = mapColumns(header);
            validateRequiredColumns(columns);

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                counters.processed++;
                try {
                    ImportedCustomerRecord record = parseRow(row, columns);
                    var result = customerProfileService.importCustomerRecord(restaurant, record);
                    if (result.newProfile()) {
                        counters.newProfiles++;
                    } else {
                        counters.matchedProfiles++;
                    }
                    if (result.conflictingMatch()) {
                        counters.conflicts++;
                    }
                } catch (Exception ex) {
                    counters.errors++;
                    log.warn("Skipping malformed import row {}: {}", counters.processed, ex.getMessage());
                }
            }
        }

        return counters;
    }

    private Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.put(normalizeHeader(header[i]), i);
        }
        return columns;
    }

    private void validateRequiredColumns(Map<String, Integer> columns) {
        if (!columns.containsKey("client id")) {
            throw new IllegalArgumentException("CSV is missing 'Client ID' column");
        }
    }

    private ImportedCustomerRecord parseRow(String[] row, Map<String, Integer> columns) {
        String clientId = value(row, columns, "client id");
        String firstName = value(row, columns, "first name");
        String lastName = value(row, columns, "last name");
        String email = value(row, columns, "email");
        String phone = value(row, columns, "phone");
        String marketingConsent = value(row, columns, "marketing consent");
        String consentType = value(row, columns, "consent type");
        Long totalOrders = parseLong(value(row, columns, "total orders"));
        Double totalSpent = parseDouble(value(row, columns, "total spent"));
        LocalDateTime lastOrderAt = parseOrderDateTime(
                value(row, columns, "last order date yyyy-mm-dd"),
                value(row, columns, "last order"));

        return new ImportedCustomerRecord(
                clientId,
                firstName,
                lastName,
                email,
                phone,
                marketingConsent,
                consentType,
                totalOrders,
                totalSpent,
                lastOrderAt);
    }

    private LocalDateTime parseOrderDateTime(String dateValue, String timeValue) {
        if (dateValue == null || dateValue.isBlank()) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(dateValue.trim(), DATE_FORMAT);
            if (timeValue == null || timeValue.isBlank()) {
                return date.atStartOfDay();
            }
            LocalTime time = LocalTime.parse(timeValue.trim(), TIME_FORMAT);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String value(String[] row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index < 0 || index >= row.length) {
            return "";
        }
        return row[index] == null ? "" : row[index].trim();
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.CANADA);
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private Component createKpi(String label, Span value) {
        VerticalLayout tile = new VerticalLayout();
        tile.setPadding(false);
        tile.setSpacing(false);
        tile.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        tile.getStyle().set("border-radius", "10px");
        tile.getStyle().set("padding", "var(--lumo-space-s)");
        tile.getStyle().set("min-width", "180px");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        labelSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");

        value.getStyle().set("font-size", "1.4rem");
        value.getStyle().set("font-weight", "700");

        tile.add(labelSpan, value);
        return tile;
    }

    private VerticalLayout createCard() {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setWidthFull();
        card.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        card.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        card.getStyle().set("background", "var(--lumo-base-color)");
        return card;
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private record RestaurantOption(Restaurant restaurant, String label) {
    }

    private static final class ImportCounters {
        private int processed;
        private int newProfiles;
        private int matchedProfiles;
        private int conflicts;
        private int errors;
    }
}
