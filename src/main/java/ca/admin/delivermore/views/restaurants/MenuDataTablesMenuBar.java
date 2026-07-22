package ca.admin.delivermore.views.restaurants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import ca.admin.delivermore.collector.data.service.TeamsRepository;
import ca.admin.delivermore.components.custom.DeliveryZonesDialog;
import ca.admin.delivermore.data.service.DeliveryZoneService;
import ca.admin.delivermore.data.service.RestaurantMenuEditorService;
import ca.admin.delivermore.views.UIUtilities;

public class MenuDataTablesMenuBar extends MenuBar {

    private static final List<String> DEFAULT_ITEM_TAG_OPTIONS = List.of(
            "HOT",
            "HALAL",
            "VEGAN",
            "DAIRY_FREE",
            "VEGETARIAN",
            "RAW",
            "GLUTEN_FREE",
            "NUT_FREE");

    private static final List<String> DEFAULT_ITEM_ALLERGEN_OPTIONS = List.of(
            "Milk",
            "Eggs",
            "Fish",
            "Shellfish",
            "Tree nuts",
            "Peanuts",
            "Wheat",
            "Soybeans");

    private static final List<String> DEFAULT_MAJOR_GROUP_OPTIONS = List.of(
            "Food",
            "NA beverages",
            "Alcoholic beverages");

    private static final List<String> DEFAULT_TAXATION_CATEGORY_OPTIONS = List.of(
            "Food",
            "Alcohol");

    private final RestaurantMenuEditorService restaurantMenuEditorService;
    private final DeliveryZoneService deliveryZoneService;
    private final TeamsRepository teamsRepository;
    private final Runnable onSettingsChanged;

    public MenuDataTablesMenuBar(
            RestaurantMenuEditorService restaurantMenuEditorService,
            DeliveryZoneService deliveryZoneService,
            TeamsRepository teamsRepository,
            Runnable onSettingsChanged) {
        this.restaurantMenuEditorService = restaurantMenuEditorService;
        this.deliveryZoneService = deliveryZoneService;
        this.teamsRepository = teamsRepository;
        this.onSettingsChanged = onSettingsChanged == null ? () -> {
        } : onSettingsChanged;
        configureDataTablesMenu();
    }

    private void configureDataTablesMenu() {
        MenuItem dataTables = addItem("Data Tables");

        dataTables.getSubMenu().addItem("Mark Items As List", event -> openMasterListDialog(
                "Edit Mark Items As List",
                "One value per line. This list is used by Item Settings.",
                restaurantMenuEditorService.getItemTagOptions(DEFAULT_ITEM_TAG_OPTIONS),
                values -> {
                    restaurantMenuEditorService.saveItemTagOptions(values);
                    onSettingsChanged.run();
                }));

        dataTables.getSubMenu().addItem("Item Allergens List", event -> openMasterListDialog(
                "Edit Item Allergens List",
                "One value per line. This list is used by Item Settings.",
                restaurantMenuEditorService.getItemAllergenOptions(DEFAULT_ITEM_ALLERGEN_OPTIONS),
                values -> {
                    restaurantMenuEditorService.saveItemAllergenOptions(values);
                    onSettingsChanged.run();
                }));

        dataTables.getSubMenu().addItem("Major Group List", event -> openMasterListDialog(
                "Edit Major Group List",
                "One value per line. This list is used in Choices & Addons > More > Major group.",
                restaurantMenuEditorService.getMajorGroupOptions(DEFAULT_MAJOR_GROUP_OPTIONS),
                values -> {
                    restaurantMenuEditorService.saveMajorGroupOptions(values);
                    onSettingsChanged.run();
                }));

        dataTables.getSubMenu().addItem("Taxation Categories List", event -> openTaxationCategoryRatesDialog());

        dataTables.getSubMenu().addItem("Service Fee Tax", event -> openNamedTaxRateDialog(
                "Service Fee Tax",
                "Configure the single tax applied to service fee.",
                restaurantMenuEditorService.getServiceFeeTaxRate(),
                restaurantMenuEditorService::saveServiceFeeTaxRate));

        dataTables.getSubMenu().addItem("Delivery Fee Tax", event -> openNamedTaxRateDialog(
                "Delivery Fee Tax",
                "Configure the single tax applied to delivery fee.",
                restaurantMenuEditorService.getDeliveryFeeTaxRate(),
                restaurantMenuEditorService::saveDeliveryFeeTaxRate));

        dataTables.getSubMenu().addItem("Checkout Service Fee", event -> openCheckoutServiceFeeDialog());

        dataTables.getSubMenu().addItem("Delivery Zones", event -> openDeliveryZonesDialog());

        dataTables.getSubMenu().addItem("Delivery Fee Info Text", event -> openCheckoutInfoTextDialog(
                "Delivery Fee Info Text",
                "Shown from the info button near Delivery fee at checkout.",
                restaurantMenuEditorService.getCheckoutDeliveryFeeInfoText(),
                restaurantMenuEditorService::saveCheckoutDeliveryFeeInfoText));

        dataTables.getSubMenu().addItem("Fees and Taxes Info Text", event -> openCheckoutInfoTextDialog(
                "Fees and Taxes Info Text",
                "Shown from the info button near Fees and taxes at checkout.",
                restaurantMenuEditorService.getCheckoutFeesTaxesInfoText(),
                restaurantMenuEditorService::saveCheckoutFeesTaxesInfoText));
    }

    private void openMasterListDialog(String title,
            String helperText,
            List<String> currentValues,
            java.util.function.Consumer<List<String>> onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.LARGE);

        Span helper = new Span(helperText);
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");
        helper.getStyle().set("font-size", "var(--lumo-font-size-s)");

        TextArea valuesField = new TextArea("Values");
        valuesField.setWidthFull();
        valuesField.setHeight("360px");
        valuesField.setValue(String.join("\n", currentValues));

        content.add(helper, valuesField);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            List<String> values = parseMultiLineList(valuesField.getValue());
            onSave.accept(values);
            showSuccess("List updated");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openTaxationCategoryRatesDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Taxation Categories List");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.LARGE);

        Span helper = new Span("Set category name, tax name, and tax %. Checkout only shows categories used on the order.");
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");
        helper.getStyle().set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout rows = new VerticalLayout();
        rows.setPadding(false);
        rows.setSpacing(false);

        List<TaxRateRow> rowModels = new ArrayList<>();

        Button add = new Button("Add row", event -> addTaxRateRow(rows, rowModels, null));
        add.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        List<RestaurantMenuEditorService.TaxationCategoryRate> existing = restaurantMenuEditorService
                .getTaxationCategoryRates(DEFAULT_TAXATION_CATEGORY_OPTIONS);
        if (existing.isEmpty()) {
            addTaxRateRow(rows, rowModels, null);
        } else {
            existing.forEach(value -> addTaxRateRow(rows, rowModels, value));
        }

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            List<RestaurantMenuEditorService.TaxationCategoryRate> values = rowModels.stream()
                    .filter(row -> !row.removed)
                    .map(row -> new RestaurantMenuEditorService.TaxationCategoryRate(
                            trimToNull(row.nameField.getValue()),
                            trimToNull(row.taxNameField.getValue()) == null ? "" : trimToNull(row.taxNameField.getValue()),
                            row.rateField.getValue() == null ? 0d : Math.max(0d, row.rateField.getValue())))
                    .filter(value -> value.category() != null)
                    .toList();

            restaurantMenuEditorService.saveTaxationCategoryRates(values);
            onSettingsChanged.run();
            showSuccess("Taxation categories updated");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        content.add(helper, add, rows);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void addTaxRateRow(VerticalLayout rows, List<TaxRateRow> rowModels,
            RestaurantMenuEditorService.TaxationCategoryRate existing) {
        HorizontalLayout rowLayout = new HorizontalLayout();
        rowLayout.setWidthFull();
        rowLayout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);

        TextField nameField = new TextField("Category");
        nameField.setWidth("35%");
        nameField.setValue(existing == null || existing.category() == null ? "" : existing.category());

        TextField taxNameField = new TextField("Tax name");
        taxNameField.setWidth("35%");
        taxNameField.setValue(existing == null || existing.taxName() == null ? "" : existing.taxName());

        NumberField percentageField = new NumberField("Tax %");
        percentageField.setWidth("20%");
        percentageField.setMin(0d);
        percentageField.setStep(0.01d);
        Double initialPercentage = existing == null ? null : existing.percentage();
        percentageField.setValue(initialPercentage == null ? 0d : initialPercentage);

        Button remove = new Button("Remove", event -> {
            rowLayout.setVisible(false);
            for (int index = 0; index < rowModels.size(); index++) {
                TaxRateRow row = rowModels.get(index);
                if (row.layout == rowLayout) {
                    rowModels.set(index, row.markRemoved());
                    break;
                }
            }
        });
        remove.getElement().setAttribute("theme", "error tertiary");

        rowLayout.add(nameField, taxNameField, percentageField, remove);
        rowLayout.expand(nameField, taxNameField);
        rows.add(rowLayout);

        rowModels.add(new TaxRateRow(rowLayout, nameField, taxNameField, percentageField, false));
    }

    private void openNamedTaxRateDialog(
            String title,
            String helperText,
            RestaurantMenuEditorService.NamedTaxRate existing,
            java.util.function.Consumer<RestaurantMenuEditorService.NamedTaxRate> onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        Span helper = new Span(helperText);
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");
        helper.getStyle().set("font-size", "var(--lumo-font-size-s)");

        TextField nameField = new TextField("Tax name");
        nameField.setWidthFull();
        nameField.setValue(existing == null || existing.name() == null ? "" : existing.name());

        NumberField rateField = new NumberField("Tax %");
        rateField.setMin(0d);
        rateField.setStep(0.01d);
        rateField.setWidthFull();
        Double initialRate = 0d;
        if (existing != null) {
            Double existingPercentage = existing.percentage();
            if (existingPercentage != null) {
                initialRate = existingPercentage;
            }
        }
        rateField.setValue(initialRate);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            String name = trimToNull(nameField.getValue());
            Double pct = rateField.getValue();
            onSave.accept(new RestaurantMenuEditorService.NamedTaxRate(name == null ? "" : name, pct == null ? 0d : pct));
            showSuccess("Tax updated");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        content.add(helper, nameField, rateField);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openCheckoutServiceFeeDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Checkout Service Fee");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        Span helper = new Span("This value applies to all restaurants for checkout and is used in customer-facing fee explanations.");
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");
        helper.getStyle().set("font-size", "var(--lumo-font-size-s)");

        NumberField serviceFeePercent = new NumberField("Service fee %");
        serviceFeePercent.setMin(0d);
        serviceFeePercent.setStep(0.01d);
        serviceFeePercent.setWidthFull();
        serviceFeePercent.setValue(roundToScale(restaurantMenuEditorService.getCheckoutServiceFeeRate() * 100d, 2));

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            double serviceRate = serviceFeePercent.getValue() == null ? 0d : Math.max(0d, roundToScale(serviceFeePercent.getValue(), 2) / 100d);

            restaurantMenuEditorService.saveCheckoutServiceFeeRate(serviceRate);
            onSettingsChanged.run();

            showSuccess("Checkout service fee updated");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        content.add(helper, serviceFeePercent);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openDeliveryZonesDialog() {
        DeliveryZonesDialog dialog = new DeliveryZonesDialog(deliveryZoneService, teamsRepository, null);
        dialog.open();
    }

    private void openCheckoutInfoTextDialog(
            String title,
            String helperText,
            String existingValue,
            java.util.function.Consumer<String> onSave) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.LARGE);

        Span helper = new Span(helperText + " Placeholders: {{DELIVERY_FEE}}, {{SERVICE_FEE_PERCENT}}.");
        helper.getStyle().set("color", "var(--lumo-secondary-text-color)");
        helper.getStyle().set("font-size", "var(--lumo-font-size-s)");

        TextArea valueField = new TextArea("Message");
        valueField.setWidthFull();
        valueField.setHeight("320px");
        valueField.setValue(existingValue == null ? "" : existingValue);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            onSave.accept(valueField.getValue());
            onSettingsChanged.run();
            showSuccess("Info text updated");
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        content.add(helper, valueField);
        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private List<String> parseMultiLineList(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return Pattern.compile("\\r?\\n|,")
                .splitAsStream(input)
                .map(this::trimToNull)
                .filter(value -> value != null && !value.isEmpty())
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private double roundToScale(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record TaxRateRow(
            HorizontalLayout layout,
            TextField nameField,
            TextField taxNameField,
            NumberField rateField,
            boolean removed) {

        private TaxRateRow markRemoved() {
            return new TaxRateRow(layout, nameField, taxNameField, rateField, true);
        }
    }
}
