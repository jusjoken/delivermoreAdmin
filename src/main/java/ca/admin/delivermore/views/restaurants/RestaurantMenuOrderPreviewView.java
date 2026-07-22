package ca.admin.delivermore.views.restaurants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;

import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.CategoryData;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.ItemData;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.OptionData;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.OptionGroupData;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.PreviewData;
import ca.admin.delivermore.data.service.RestaurantMenuOrderPreviewService.SizeData;
import ca.admin.delivermore.data.service.DeliveryZoneService;
import ca.admin.delivermore.data.service.CustomerProfileService;
import ca.admin.delivermore.data.service.LocationLookupService;
import ca.admin.delivermore.components.custom.LeafletPointPickerDialog;
import ca.admin.delivermore.data.service.RestaurantMenuOrderSubmissionService;
import ca.admin.delivermore.data.service.RestaurantMenuOrderSubmissionService.LineItemRequest;
import ca.admin.delivermore.data.service.RestaurantMenuOrderSubmissionService.SelectedOptionRequest;
import ca.admin.delivermore.data.service.RestaurantMenuOrderSubmissionService.SubmissionResult;
import ca.admin.delivermore.data.service.RestaurantMenuOrderSubmissionService.SubmitOrderRequest;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Menu Preview & Test Ordering")
@Route(value = "restaurants/menu-preview", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RestaurantMenuOrderPreviewView extends VerticalLayout implements BeforeEnterObserver {

    private static final String RESTAURANT_ID_QP = "restaurantId";
    private static final int MOBILE_OVERLAY_BREAKPOINT = 900;
    private static final String TIP_MODE_AMOUNT = "Tip Amount";
    private static final String TIP_MODE_PERCENT = "Tip Percent";
        private static final Set<String> PROVINCE_CODES = Set.of(
            "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT");

    private final RestaurantMenuOrderPreviewService previewService;
    private final RestaurantMenuOrderSubmissionService submissionService;
    private final DeliveryZoneService deliveryZoneService;
    private final LocationLookupService locationLookupService;
    private final CustomerProfileService customerProfileService;

    private Long restaurantId;
    private PreviewData previewData;
    private final List<OrderLine> cartLines = new ArrayList<>();
    private final CheckoutState checkoutState = new CheckoutState();

    private final H3 title = new H3("Preview & Test Ordering");
    private final Button backButton = new Button("Back to Menu Editor");
    private final VerticalLayout menuContent = new VerticalLayout();
    private final VerticalLayout cartContent = new VerticalLayout();
    private final Scroller menuScroller = new Scroller(menuContent);
    private final Scroller cartScroller = new Scroller(cartContent);
    private final MasterDetailLayout masterDetailLayout = new MasterDetailLayout();
    private final HorizontalLayout headerLayout = new HorizontalLayout();
    private final HorizontalLayout headerActionsRow = new HorizontalLayout();
    private final Button showCartButton = new Button("Show Cart", VaadinIcon.CART.create());
    private final Button closeCartButton = new Button("Close", VaadinIcon.CLOSE_SMALL.create());

    private Registration browserResizeRegistration;
    private boolean mobileOverlayMode;
    private boolean cartOverlayOpen;
    private boolean updatingTipFields;
    private VerticalLayout checkoutTipBlock;
    private RadioButtonGroup<String> checkoutTipModeField;
    private NumberField checkoutTipAmountField;
    private NumberField checkoutTipPercentField;
    private Span checkoutTipBaseTotalValue;
    private Span checkoutTipAmountValue;
    private Span checkoutEstimatedTotalValue;

    public RestaurantMenuOrderPreviewView(
            RestaurantMenuOrderPreviewService previewService,
            RestaurantMenuOrderSubmissionService submissionService,
            DeliveryZoneService deliveryZoneService,
            LocationLookupService locationLookupService,
            CustomerProfileService customerProfileService) {
        this.previewService = previewService;
        this.submissionService = submissionService;
        this.deliveryZoneService = deliveryZoneService;
        this.locationLookupService = locationLookupService;
        this.customerProfileService = customerProfileService;
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        menuContent.setPadding(false);
        menuContent.setSpacing(true);
        menuContent.setWidthFull();

        cartContent.setPadding(false);
        cartContent.setSpacing(true);
        cartContent.setWidthFull();
        cartContent.getStyle().set("box-sizing", "border-box");
        cartContent.getStyle().set("padding-bottom", "calc(var(--lumo-space-xl) + env(safe-area-inset-bottom, 0px))");

        menuScroller.setSizeFull();

        cartScroller.setSizeFull();

        masterDetailLayout.setSizeFull();
        masterDetailLayout.setMaster(menuScroller);
        masterDetailLayout.setDetail(cartScroller);
        masterDetailLayout.setMasterSize("62%");
        masterDetailLayout.setDetailSize("38%");
        masterDetailLayout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
        masterDetailLayout.setContainment(MasterDetailLayout.Containment.LAYOUT);

        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        backButton.addClickListener(event -> UI.getCurrent()
            .navigate(RestaurantMenuEditorView.class,
                new com.vaadin.flow.router.QueryParameters(Map.of(RESTAURANT_ID_QP, List.of(String.valueOf(restaurantId))))));

        showCartButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        showCartButton.setVisible(false);
        showCartButton.addClickListener(event -> openCartOverlay());

        closeCartButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        closeCartButton.setVisible(false);
        closeCartButton.addClickListener(event -> closeCartOverlay());

        masterDetailLayout.addBackdropClickListener(event -> closeCartOverlay());
        masterDetailLayout.addDetailEscapePressListener(event -> closeCartOverlay());

        headerActionsRow.setWidthFull();
        headerActionsRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headerActionsRow.setSpacing(true);
        headerActionsRow.add(backButton, showCartButton);

        headerLayout.setWidthFull();
        headerLayout.setPadding(false);
        headerLayout.setSpacing(false);
        headerLayout.getStyle().set("flex-wrap", "wrap");
        headerLayout.add(headerActionsRow, title);

        addAttachListener(event -> {
            if (browserResizeRegistration == null) {
                browserResizeRegistration = event.getUI()
                        .getPage()
                        .addBrowserWindowResizeListener(resizeEvent -> updateOverlayMode(resizeEvent.getWidth()));
            }
            event.getUI()
                    .getPage()
                    .executeJs("return window.innerWidth")
                    .then(Integer.class, this::updateOverlayMode);
        });
        addDetachListener(event -> {
            if (browserResizeRegistration != null) {
                browserResizeRegistration.remove();
                browserResizeRegistration = null;
            }
        });

        add(headerLayout, masterDetailLayout);
        setFlexGrow(1, masterDetailLayout);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        restaurantId = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault(RESTAURANT_ID_QP, List.of())
                .stream()
                .findFirst()
                .flatMap(this::parseLong)
                .orElse(null);

        if (restaurantId == null) {
            clearView("No restaurant selected. Open this page from the menu editor.");
            return;
        }

        try {
            previewData = previewService.loadPreviewData(restaurantId);
        } catch (IllegalStateException ex) {
            clearView(ex.getMessage());
            return;
        }

        applyDefaultCheckoutCityIfMissing();
        title.setText(previewData.restaurant().getName() + " - Preview & Test Ordering");
        renderMenu();
        renderCart();
    }

    private void updateOverlayMode(int viewportWidth) {
        boolean shouldUseOverlay = viewportWidth > 0 && viewportWidth < MOBILE_OVERLAY_BREAKPOINT;
        mobileOverlayMode = shouldUseOverlay;
        masterDetailLayout.setForceOverlay(shouldUseOverlay);

        if (shouldUseOverlay) {
            masterDetailLayout.setOverlayMode(MasterDetailLayout.OverlayMode.STACK);
            masterDetailLayout.setContainment(MasterDetailLayout.Containment.VIEWPORT);
        } else {
            masterDetailLayout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
            masterDetailLayout.setContainment(MasterDetailLayout.Containment.LAYOUT);
        }

        if (shouldUseOverlay) {
            if (cartOverlayOpen) {
                masterDetailLayout.setDetail(cartScroller);
            } else {
                masterDetailLayout.setDetail(null);
            }
        } else {
            cartOverlayOpen = false;
            masterDetailLayout.setDetail(cartScroller);
        }

        updateHeaderLayout();
        updateCartButtons();
    }

    private void updateHeaderLayout() {
        if (mobileOverlayMode) {
            headerLayout.setAlignItems(FlexComponent.Alignment.START);
            title.setWidthFull();
            title.getStyle().set("margin", "var(--lumo-space-s) 0 0 0");
            headerActionsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            headerActionsRow.expand(backButton);
            showCartButton.getStyle().set("flex-shrink", "0");
        } else {
            headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            title.setWidth(null);
            title.getStyle().set("margin", "0");
            headerActionsRow.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
            headerActionsRow.setFlexGrow(0, backButton);
            showCartButton.getStyle().remove("flex-shrink");
        }
    }

    private void openCartOverlay() {
        if (!mobileOverlayMode) {
            return;
        }
        cartOverlayOpen = true;
        masterDetailLayout.setDetail(cartScroller);
        updateCartButtons();
    }

    private void closeCartOverlay() {
        if (!mobileOverlayMode) {
            return;
        }
        cartOverlayOpen = false;
        masterDetailLayout.setDetail(null);
        updateCartButtons();
    }

    private void updateCartButtons() {
        updateShowCartButtonLabel();
        showCartButton.setVisible(mobileOverlayMode && !cartOverlayOpen);
        closeCartButton.setVisible(mobileOverlayMode && cartOverlayOpen);
    }

    private void updateShowCartButtonLabel() {
        int itemCount = cartLines.stream().mapToInt(OrderLine::quantity).sum();
        if (itemCount <= 0) {
            showCartButton.setText("Show Cart");
            return;
        }
        showCartButton.setText(itemCount == 1 ? "Cart 1 item" : "Cart " + itemCount + " items");
    }

    private void clearView(String message) {
        menuContent.removeAll();
        cartContent.removeAll();
        title.setText("Preview & Test Ordering");
        menuContent.add(createMutedText(message));
    }

    private void renderMenu() {
        menuContent.removeAll();
        if (previewData == null) {
            return;
        }

        Component menuHero = buildMenuHero();
        if (menuHero != null) {
            menuContent.add(menuHero);
        }

        for (CategoryData category : previewData.categories()) {
            menuContent.add(buildCategorySection(category));
        }
    }

    private Component buildMenuHero() {
        String heroImageUrl = previewData.menuHeaderImageUrl();
        if (heroImageUrl == null || heroImageUrl.isBlank()) {
            heroImageUrl = previewData.restaurantLogoImageUrl();
        }
        if (heroImageUrl == null || heroImageUrl.isBlank()) {
            return null;
        }

        HorizontalLayout hero = new HorizontalLayout();
        hero.setWidthFull();
        hero.setAlignItems(FlexComponent.Alignment.CENTER);
        hero.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        hero.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        hero.getStyle().set("padding", "var(--lumo-space-s)");
        hero.getStyle().set("background", "var(--lumo-contrast-5pct)");

        Image image = new Image(heroImageUrl, "Menu header image");
        image.setWidth("180px");
        image.getStyle().set("border-radius", "10px");
        image.getStyle().set("object-fit", "cover");

        VerticalLayout text = new VerticalLayout();
        text.setPadding(false);
        text.setSpacing(false);
        text.add(new H3(previewData.restaurant().getName()));
        text.add(createMutedText("Preview of customer-facing menu content"));

        hero.add(image, text);
        hero.expand(text);
        return hero;
    }

    private Component buildCategorySection(CategoryData category) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        H4 heading = new H4(category.name());
        heading.getStyle().set("margin", "0");
        heading.getStyle().set("font-size", "1.65rem");
        heading.getStyle().set("letter-spacing", "0.02em");

        section.add(heading);
        if (category.description() != null && !category.description().isBlank()) {
            Span description = new Span(category.description());
            description.getStyle().set("color", "var(--lumo-secondary-text-color)");
            section.add(description);
        }

        Div grid = new Div();
        grid.getStyle().set("display", "grid");
        grid.getStyle().set("grid-template-columns", "repeat(auto-fit, minmax(320px, 1fr))");
        grid.getStyle().set("gap", "12px");
        grid.setWidthFull();

        if (category.imageUrl() != null && !category.imageUrl().isBlank()) {
            grid.add(buildCategoryImageTile(category.imageUrl(), category.name()));
        }

        for (ItemData item : category.items()) {
            grid.add(buildItemCard(item));
        }
        section.add(grid);
        return section;
    }

    private Component buildCategoryImageTile(String imageUrl, String categoryName) {
        VerticalLayout tile = new VerticalLayout();
        tile.setPadding(false);
        tile.setSpacing(false);
        tile.setWidthFull();
        tile.getStyle().set("border", "2px dashed var(--lumo-contrast-20pct)");
        tile.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        tile.getStyle().set("background", "var(--lumo-base-color)");
        tile.getStyle().set("overflow", "hidden");

        Image image = new Image(imageUrl, categoryName + " image");
        image.setWidthFull();
        image.getStyle().set("aspect-ratio", "16 / 9");
        image.getStyle().set("object-fit", "cover");

        tile.add(image);
        return tile;
    }

    private Component buildItemCard(ItemData item) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.setWidthFull();
        card.getStyle().set("border", "2px solid var(--lumo-contrast-20pct)");
        card.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        card.getStyle().set("background", "var(--lumo-base-color)");
        card.getStyle().set("cursor", item.outOfStock() ? "not-allowed" : "pointer");
        card.getStyle().set("opacity", item.outOfStock() ? "0.7" : "1");
        card.getStyle().set("transition", "transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease");
        if (!item.outOfStock()) {
            card.getStyle().set("box-shadow", "var(--lumo-box-shadow-xs)");
            card.getElement().addEventListener("mouseenter", event -> {
                card.getStyle().set("transform", "translateY(-2px)");
                card.getStyle().set("box-shadow", "var(--lumo-box-shadow-s)");
                card.getStyle().set("border-color", "var(--lumo-primary-color-50pct)");
            });
            card.getElement().addEventListener("mouseleave", event -> {
                card.getStyle().set("transform", "translateY(0)");
                card.getStyle().set("box-shadow", "var(--lumo-box-shadow-xs)");
                card.getStyle().set("border-color", "var(--lumo-contrast-20pct)");
            });
            card.addClickListener(event -> openItemDialog(item));
        }

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.START);

        VerticalLayout text = new VerticalLayout();
        text.setPadding(false);
        text.setSpacing(false);
        text.setWidthFull();

        Span name = new Span(item.name());
        name.getStyle().set("font-weight", "700");
        name.getStyle().set("font-size", "1.05rem");

        Span price = new Span(formatCurrency(minimumDisplayPrice(item)));
        price.getStyle().set("font-weight", "700");
        price.getStyle().set("white-space", "nowrap");

        top.add(name, price);
        top.expand(name);

        Span description = new Span(item.description() == null ? "" : item.description());
        description.getStyle().set("color", "var(--lumo-secondary-text-color)");
        description.getStyle().set("font-size", "var(--lumo-font-size-s)");

        text.add(top);

        if (item.imageUrl() != null && !item.imageUrl().isBlank()) {
            Image itemImage = new Image(item.imageUrl(), item.name() + " image");
            itemImage.setWidthFull();
            itemImage.getStyle().set("aspect-ratio", "16 / 9");
            itemImage.getStyle().set("object-fit", "cover");
            itemImage.getStyle().set("border-radius", "8px");
            text.add(itemImage);
        }

        HorizontalLayout detailRow = new HorizontalLayout();
        detailRow.setWidthFull();
        detailRow.setAlignItems(FlexComponent.Alignment.START);

        VerticalLayout descriptionWrap = new VerticalLayout(description);
        descriptionWrap.setPadding(false);
        descriptionWrap.setSpacing(false);
        descriptionWrap.setWidthFull();
        detailRow.add(descriptionWrap);
        detailRow.expand(descriptionWrap);

        text.add(detailRow);

        HorizontalLayout badges = new HorizontalLayout();
        badges.setSpacing(true);
        badges.setPadding(false);
        if (item.outOfStock()) {
            badges.add(createBadge("No stock", "error"));
        }
        if (item.tags().contains("HOT")) {
            badges.add(createBadge("Hot", "contrast"));
        }
        card.add(text, badges);
        return card;
    }

    private Span createBadge(String text, String variant) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge " + variant);
        return badge;
    }

    private void openItemDialog(ItemData item) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(item.name());
        dialog.setWidth("720px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        if (item.description() != null && !item.description().isBlank()) {
            Span description = new Span(item.description());
            description.getStyle().set("color", "var(--lumo-secondary-text-color)");
            content.add(description);
        }

        TextArea instructionsField = new TextArea("Special instructions");
        instructionsField.setWidthFull();
        instructionsField.setPlaceholder("Example: No pepper / sugar / salt please.");

        NumberField quantityField = new NumberField("Quantity");
        quantityField.setWidth("160px");
        quantityField.setStep(1d);
        quantityField.setMin(1d);
        quantityField.setValue(1d);

        Span totalLabel = new Span();
        totalLabel.getStyle().set("font-weight", "700");
        totalLabel.getStyle().set("font-size", "1.1rem");

        VerticalLayout groupsContainer = new VerticalLayout();
        groupsContainer.setPadding(false);
        groupsContainer.setSpacing(true);
        groupsContainer.setWidthFull();

        List<GroupCollector> collectors = new ArrayList<>();
        List<OptionGroupData> activeGroups = new ArrayList<>();

        RadioButtonGroup<SizeData> sizeField = null;
        if (!item.sizes().isEmpty()) {
            sizeField = new RadioButtonGroup<>();
            sizeField.setLabel("Size");
            sizeField.setItems(item.sizes());
            sizeField.setItemLabelGenerator(size -> size.name() + " (" + formatCurrency(size.absolutePrice()) + ")");
            sizeField.setRequiredIndicatorVisible(true);
            SizeData defaultSize = item.sizes().stream().filter(SizeData::defaultSize).findFirst().orElse(item.sizes().getFirst());
            sizeField.setValue(defaultSize);
            content.add(sizeField);
        }

        final RadioButtonGroup<SizeData> sizeSelector = sizeField;

        Runnable updateTotals = () -> {
            double quantity = quantityField.getValue() == null ? 1d : Math.max(1d, quantityField.getValue());
            SizeData currentSize = sizeSelector == null ? null : sizeSelector.getValue();
            double unitPrice = resolveBaseUnitPrice(item, currentSize) + collectors.stream()
                    .flatMap(collector -> collector.collectSelections().get().stream())
                    .mapToDouble(selection -> selection.unitPrice() * selection.quantity())
                    .sum();
            totalLabel.setText("Current total: " + formatCurrency(unitPrice * quantity));
        };

        java.util.function.Consumer<SizeData> rebuildGroups = selectedSize -> {
            groupsContainer.removeAll();
            collectors.clear();
            activeGroups.clear();
            activeGroups.addAll(item.optionGroups());
            if (selectedSize != null) {
                activeGroups.addAll(selectedSize.optionGroups());
            }

            activeGroups.stream()
                    .sorted(Comparator.comparing(OptionGroupData::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .forEach(group -> {
                        GroupCollector collector = buildGroupCollector(group, updateTotals);
                        collectors.add(collector);
                        groupsContainer.add(collector.component());
                    });
            updateTotals.run();
        };

        if (sizeField != null) {
            RadioButtonGroup<SizeData> finalSizeField = sizeField;
            sizeField.addValueChangeListener(event -> rebuildGroups.accept(finalSizeField.getValue()));
            rebuildGroups.accept(sizeField.getValue());
        } else {
            rebuildGroups.accept(null);
        }

        quantityField.addValueChangeListener(event -> updateTotals.run());
        content.add(groupsContainer, instructionsField, quantityField, totalLabel);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button addToCart = new Button("Add to cart", event -> {
            SizeData selectedSize = sizeSelector == null ? null : sizeSelector.getValue();
            if (sizeSelector != null && selectedSize == null) {
                showError("Size is required");
                return;
            }

            for (GroupCollector collector : collectors) {
                Optional<String> validation = collector.validate().get();
                if (validation.isPresent()) {
                    showError(validation.get());
                    return;
                }
            }

            int quantity = quantityField.getValue() == null ? 1 : Math.max(1, quantityField.getValue().intValue());
            List<SelectedOptionLine> selections = collectors.stream()
                    .flatMap(collector -> collector.collectSelections().get().stream())
                    .toList();

            double unitBasePrice = resolveBaseUnitPrice(item, selectedSize);
            double optionTotal = selections.stream()
                    .mapToDouble(selection -> selection.unitPrice() * selection.quantity())
                    .sum();
            double unitPrice = roundCurrency(unitBasePrice + optionTotal);

            cartLines.add(new OrderLine(
                    item,
                    selectedSize,
                    selections,
                    instructionsField.getValue(),
                    quantity,
                    unitPrice));
            dialog.close();
            renderCart();
            showSuccess("Added to cart");
        });
        addToCart.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, addToCart);
        dialog.open();
    }

    private GroupCollector buildGroupCollector(OptionGroupData group, Runnable updateTotals) {
        if (!group.allowQuantity() && group.forceMax() <= 1) {
            return buildSingleSelectCollector(group, updateTotals);
        }
        return buildMultiSelectCollector(group, updateTotals);
    }

    private GroupCollector buildSingleSelectCollector(OptionGroupData group, Runnable updateTotals) {
        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setPadding(false);
        wrapper.setSpacing(false);

        Span groupTitle = new Span(group.name() + labelSuffix(group));
        groupTitle.getStyle().set("font-weight", "600");
        groupTitle.getStyle().set("margin-bottom", "var(--lumo-space-xs)");

        RadioButtonGroup<OptionChoice> choices = new RadioButtonGroup<>();
        List<OptionChoice> items = new ArrayList<>();
        if (!group.required()) {
            items.add(new OptionChoice(null));
        }
        group.options().stream()
                .filter(option -> !option.outOfStock())
                .map(OptionChoice::new)
                .forEach(items::add);
        choices.setItems(items);
        choices.setItemLabelGenerator(choice -> choice.option() == null
                ? "None"
                : choice.option().name() + priceSuffix(choice.option().price()));
        choices.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        choices.addValueChangeListener(event -> updateTotals.run());

        if (group.required()) {
            group.options().stream().filter(OptionData::defaultOption).findFirst().ifPresent(option -> choices.setValue(new OptionChoice(option)));
        }

        wrapper.add(groupTitle, choices);
        return new GroupCollector(
                wrapper,
                () -> {
                    OptionChoice selected = choices.getValue();
                    if (selected == null || selected.option() == null) {
                        return List.of();
                    }
                    return List.of(new SelectedOptionLine(group.name(), selected.option().name(), selected.option().price(), 1, group.taxationCategory()));
                },
                () -> {
                    if (group.required() && (choices.getValue() == null || choices.getValue().option() == null)) {
                        return Optional.of(group.name() + " is required");
                    }
                    return Optional.empty();
                });
    }

    private GroupCollector buildMultiSelectCollector(OptionGroupData group, Runnable updateTotals) {
        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setPadding(false);
        wrapper.setSpacing(false);

        Span groupTitle = new Span(group.name() + labelSuffix(group));
        groupTitle.getStyle().set("font-weight", "600");
        groupTitle.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
        wrapper.add(groupTitle);

        List<MultiSelectionRow> rows = new ArrayList<>();
        for (OptionData option : group.options()) {
            if (option.outOfStock()) {
                continue;
            }
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(FlexComponent.Alignment.END);
            row.setWidthFull();

            Checkbox selected = new Checkbox(option.name() + priceSuffix(option.price()));
            selected.setValue(option.defaultOption());

            NumberField quantity = new NumberField("Qty");
            quantity.setStep(1d);
            quantity.setMin(0d);
            quantity.setWidth("120px");
            quantity.setVisible(group.allowQuantity());
            quantity.setValue(option.defaultOption() ? 1d : 0d);
            if (!group.allowQuantity()) {
                quantity.setEnabled(false);
            }

            selected.addValueChangeListener(event -> {
                if (group.allowQuantity()) {
                    Double currentQty = quantity.getValue();
                    quantity.setValue(Boolean.TRUE.equals(event.getValue()) ? Math.max(1d, currentQty == null ? 1d : currentQty) : 0d);
                }
                updateTotals.run();
            });
            quantity.addValueChangeListener(event -> updateTotals.run());

            row.add(selected, quantity);
            row.expand(selected);
            wrapper.add(row);
            rows.add(new MultiSelectionRow(option, selected, quantity));
        }

        return new GroupCollector(
                wrapper,
                () -> rows.stream()
                        .filter(row -> Boolean.TRUE.equals(row.selected().getValue()))
                        .map(row -> new SelectedOptionLine(
                                group.name(),
                                row.option().name(),
                                row.option().price(),
                                group.allowQuantity() ? Math.max(1, toInt(row.quantity().getValue())) : 1,
                                group.taxationCategory()))
                        .toList(),
                () -> {
                    int selectedCount = rows.stream()
                            .filter(row -> Boolean.TRUE.equals(row.selected().getValue()))
                            .mapToInt(row -> group.allowQuantity() ? Math.max(1, toInt(row.quantity().getValue())) : 1)
                            .sum();
                    if (group.forceMin() > 0 && selectedCount < group.forceMin()) {
                        return Optional.of(group.name() + " requires at least " + group.forceMin() + " selection(s)");
                    }
                    if (group.forceMax() > 0 && selectedCount > group.forceMax()) {
                        return Optional.of(group.name() + " allows at most " + group.forceMax() + " selection(s)");
                    }
                    return Optional.empty();
                });
    }

    private void renderCart() {
        cartContent.removeAll();
        if (previewData == null) {
            return;
        }

        updateShowCartButtonLabel();

        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headerRow.getStyle().set("position", "sticky");
        headerRow.getStyle().set("top", "0");
        headerRow.getStyle().set("z-index", "1");
        headerRow.getStyle().set("background", "var(--lumo-base-color)");
        headerRow.getStyle().set("padding", "var(--lumo-space-s) 0");
        headerRow.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        H4 header = new H4("Cart & Checkout");
        header.getStyle().set("margin", "0");
        headerRow.add(header, closeCartButton);
        headerRow.expand(header);
        cartContent.add(headerRow);

        cartContent.add(buildRestaurantInfoCard());
        cartContent.add(buildCartCard());
        cartContent.add(buildCheckoutCard());
    }

    private Component buildRestaurantInfoCard() {
        VerticalLayout card = createCard();
        Span restaurantName = new Span(previewData.restaurant().getName());
        restaurantName.getStyle().set("font-weight", "600");
        card.add(restaurantName);

        DeliveryZoneService.DeliveryQuote deliveryQuote = resolveCurrentDeliveryQuote();
        Double restaurantDistanceKm = deliveryZoneService.distanceToRestaurantKm(
                previewData.restaurant(),
                checkoutState.customerLatitude,
                checkoutState.customerLongitude);

        HorizontalLayout deliveryFeeRow = new HorizontalLayout();
        deliveryFeeRow.setPadding(false);
        deliveryFeeRow.setSpacing(true);
        deliveryFeeRow.setAlignItems(FlexComponent.Alignment.CENTER);
        deliveryFeeRow.add(
                createMutedText("Delivery fee: " + formatCurrency(calculateDeliveryFee())),
                createInlineInfoButton("Delivery fee details", () -> openInfoDialog(
                        "Delivery Fee",
                        renderInfoTextWithPlaceholders(resolveDeliveryFeeInfoText()))));
        card.add(deliveryFeeRow);

        if (!deliveryQuote.zonesConfigured()) {
            card.add(createMutedText(deliveryQuote.message()));
        } else if (!deliveryQuote.matched()) {
            card.add(createMutedText(deliveryQuote.message()));
        } else if (deliveryQuote.zoneName() != null && !deliveryQuote.zoneName().isBlank()) {
            card.add(createMutedText("Zone: " + deliveryQuote.zoneName()
                    + (deliveryQuote.distanceKm() == null ? "" : " | Base distance: " + formatDistance(deliveryQuote.distanceKm()))));
        }

        if (restaurantDistanceKm != null) {
            card.add(createMutedText("Approx distance to restaurant: " + formatDistance(restaurantDistanceKm)));
        }
        return card;
    }

    private Component buildCartCard() {
        VerticalLayout card = createCard();
        H4 cartHeader = new H4("Cart");
        cartHeader.getStyle().set("margin", "0");
        card.add(cartHeader);

        if (cartLines.isEmpty()) {
            card.add(createMutedText("No items added yet. Select an item from the menu to begin testing the order flow."));
            return card;
        }

        for (int index = 0; index < cartLines.size(); index++) {
            int lineIndex = index;
            OrderLine line = cartLines.get(index);
            card.add(buildOrderLineCard(line, () -> {
                cartLines.remove(lineIndex);
                renderCart();
            }));
        }

        card.add(buildTotalsSection());
        return card;
    }

    private Component buildOrderLineCard(OrderLine line, Runnable onRemove) {
        VerticalLayout wrapper = new VerticalLayout();
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        wrapper.getStyle().set("padding", "0 0 12px 0");

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull();
        Span lineTitle = new Span(line.item().name() + (line.selectedSize() == null ? "" : " [" + line.selectedSize().name() + "]"));
        lineTitle.getStyle().set("font-weight", "600");
        Span price = new Span(formatCurrency(line.totalPrice()));
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> onRemove.run());
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        top.add(lineTitle, price, remove);
        top.expand(lineTitle);

        wrapper.add(top);
        wrapper.add(createMutedText("Qty: " + line.quantity()));
        for (SelectedOptionLine selection : line.selections()) {
            wrapper.add(createMutedText(selection.groupName() + ": " + selection.optionName()
                    + (selection.quantity() > 1 ? " x" + selection.quantity() : "")
                    + priceSuffix(selection.unitPrice())));
        }
        if (line.instructions() != null && !line.instructions().isBlank()) {
            wrapper.add(createMutedText("Instructions: " + line.instructions()));
        }
        return wrapper;
    }

    private Component buildTotalsSection() {
        VerticalLayout totals = new VerticalLayout();
        totals.setPadding(false);
        totals.setSpacing(false);
        totals.setWidthFull();
        totals.getStyle().set("padding-top", "12px");

        totals.add(buildTotalRow("Sub-total", calculateSubTotal()));
        totals.add(buildTotalRowWithLabelActions(
                "Fees and taxes",
                calculateFeesAndTaxesTotal(),
                createInlineInfoButton("Fees and taxes details", () -> openInfoDialog(
                        "Fees and Taxes",
                        renderInfoTextWithPlaceholders(resolveFeesTaxesInfoText()))),
                createInlineActionButton("$", "Preview-only breakdown", this::openFeesTaxesBreakdownDialog)));
        if (isOnlinePaymentMethod(checkoutState.paymentMethod) || calculateTipAmount() > 0d) {
            totals.add(buildTotalRow("Tip", calculateTipAmount()));
        }
        totals.add(buildStrongTotalRow("Total", calculateGrandTotal()));
        return totals;
    }

    private Component buildCheckoutCard() {
        VerticalLayout card = createCard();
        card.getStyle().set("margin-bottom", "calc(var(--lumo-space-m) + env(safe-area-inset-bottom, 0px))");
        H4 checkoutHeader = new H4("Checkout");
        checkoutHeader.getStyle().set("margin", "0");
        card.add(checkoutHeader);

        TextField contactEmail = new TextField("Email");
        contactEmail.setWidthFull();
        contactEmail.setRequiredIndicatorVisible(true);
        contactEmail.setValue(checkoutState.contactEmail);
        contactEmail.setValueChangeMode(ValueChangeMode.ON_BLUR);
        contactEmail.addValueChangeListener(event -> {
            checkoutState.contactEmail = valueOrBlank(event.getValue());
            attemptCustomerPrefill();
        });

        TextField contactPhone = new TextField("Phone");
        contactPhone.setWidthFull();
        contactPhone.setRequiredIndicatorVisible(true);
        contactPhone.setValue(checkoutState.contactPhone);
        contactPhone.setValueChangeMode(ValueChangeMode.ON_BLUR);
        contactPhone.addValueChangeListener(event -> {
            checkoutState.contactPhone = valueOrBlank(event.getValue());
            attemptCustomerPrefill();
        });

        TextField contactName = new TextField("Contact name");
        contactName.setWidthFull();
        contactName.setRequiredIndicatorVisible(true);
        contactName.setValue(checkoutState.contactName);
        contactName.addValueChangeListener(event -> checkoutState.contactName = valueOrBlank(event.getValue()));

        TextField street = new TextField("Street address");
        street.setWidthFull();
        street.setRequiredIndicatorVisible(true);
        street.setValue(checkoutState.street);
        street.addValueChangeListener(event -> {
            checkoutState.street = valueOrBlank(event.getValue());
            clearConfirmedCustomerLocation(true);
        });

        TextField city = new TextField("Town / city");
        city.setWidthFull();
        city.setRequiredIndicatorVisible(true);
        city.setValue(checkoutState.city);
        city.addValueChangeListener(event -> {
            checkoutState.city = valueOrBlank(event.getValue());
            clearConfirmedCustomerLocation(true);
        });

        TextField postal = new TextField("Postal code (optional, helps lookup)");
        postal.setWidthFull();
        postal.setValue(checkoutState.postalCode);
        postal.addValueChangeListener(event -> {
            checkoutState.postalCode = valueOrBlank(event.getValue());
            clearConfirmedCustomerLocation(true);
        });

        Button confirmLocation = new Button("Confirm location on map", event -> openCheckoutLocationDialog());
        confirmLocation.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button clearLocation = new Button("Clear confirmed location", event -> clearConfirmedCustomerLocation(true));
        clearLocation.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY_INLINE);
        Span locationStatus = createMutedText(resolveCheckoutLocationStatus());
        VerticalLayout locationStatusBlock = new VerticalLayout(locationStatus);
        locationStatusBlock.setPadding(false);
        locationStatusBlock.setSpacing(false);
        locationStatusBlock.setWidthFull();

        ComboBox<String> paymentMethod = new ComboBox<>("Payment method");
        paymentMethod.setWidthFull();
        paymentMethod.setRequiredIndicatorVisible(true);
        paymentMethod.setItems(List.of("Cash", "Card to delivery person", "Online"));
        paymentMethod.setValue(checkoutState.paymentMethod);

        Checkbox fulfilmentOption = new Checkbox("In-person delivery");
        fulfilmentOption.setValue(checkoutState.inPersonDelivery);
        fulfilmentOption.addValueChangeListener(event -> checkoutState.inPersonDelivery = Boolean.TRUE.equals(event.getValue()));

        paymentMethod.addValueChangeListener(event -> {
            String selectedMethod = event.getValue() == null ? "Cash" : event.getValue();
            checkoutState.paymentMethod = selectedMethod;
            if (!isOnlinePaymentMethod(selectedMethod)) {
                checkoutState.tipMode = TIP_MODE_AMOUNT;
                checkoutState.tipAmount = 0d;
                checkoutState.tipPercent = 0d;
            }
            renderCart();
        });
        applyFulfillmentRule(checkoutState.paymentMethod, fulfilmentOption);

        checkoutTipBlock = new VerticalLayout();
        checkoutTipBlock.setPadding(false);
        checkoutTipBlock.setSpacing(true);
        checkoutTipBlock.setWidthFull();

        checkoutTipModeField = new RadioButtonGroup<>();
        checkoutTipModeField.setLabel("Tip mode");
        checkoutTipModeField.setWidthFull();
        checkoutTipModeField.setItems(TIP_MODE_AMOUNT, TIP_MODE_PERCENT);
        checkoutTipModeField.setValue(checkoutState.tipMode);

        checkoutTipPercentField = new NumberField("Tip %");
        checkoutTipPercentField.setWidthFull();
        checkoutTipPercentField.setMin(0);
        checkoutTipPercentField.setMax(999);
        checkoutTipPercentField.setStep(1);
        checkoutTipPercentField.setStepButtonsVisible(true);
        checkoutTipPercentField.setValue(checkoutState.tipPercent);
        checkoutTipPercentField.setValueChangeMode(ValueChangeMode.EAGER);
        checkoutTipPercentField.setAutoselect(true);

        checkoutTipAmountField = UIUtilities.getNumberField("Tip amount", false, "$");
        checkoutTipAmountField.setWidthFull();
        checkoutTipAmountField.setMin(0);
        checkoutTipAmountField.setStep(0.01);
        checkoutTipAmountField.setValue(checkoutState.tipAmount);
        checkoutTipAmountField.setValueChangeMode(ValueChangeMode.EAGER);
        checkoutTipAmountField.setAutoselect(true);

        checkoutTipBaseTotalValue = new Span();
        checkoutTipAmountValue = new Span();
        checkoutEstimatedTotalValue = new Span();

        VerticalLayout tipInfoBlock = new VerticalLayout();
        tipInfoBlock.setPadding(false);
        tipInfoBlock.setSpacing(false);
        tipInfoBlock.setWidthFull();
        tipInfoBlock.add(
                buildInfoRow("Current total before tip", checkoutTipBaseTotalValue),
                buildInfoRow("Tip amount", checkoutTipAmountValue),
                buildInfoRow("Estimated total with tip", checkoutEstimatedTotalValue));

        checkoutTipModeField.addValueChangeListener(event -> {
            String selectedMode = event.getValue() == null ? TIP_MODE_AMOUNT : event.getValue();
            checkoutState.tipMode = selectedMode;
            if (TIP_MODE_PERCENT.equals(selectedMode)) {
                checkoutState.tipPercent = 0d;
                checkoutState.tipAmount = 0d;
            }
            syncTipFields();
        });

        checkoutTipAmountField.addValueChangeListener(event -> {
            if (updatingTipFields) {
                return;
            }
            if (TIP_MODE_PERCENT.equals(checkoutState.tipMode)) {
                return;
            }
            checkoutState.tipAmount = roundCurrency(event.getValue() == null ? 0d : Math.max(0d, event.getValue()));
            double baseTotal = calculateTipBaseTotal();
            if (baseTotal <= 0d) {
                checkoutState.tipPercent = 0d;
            } else {
                checkoutState.tipPercent = Math.min(999d,
                        roundCurrency((checkoutState.tipAmount / baseTotal) * 100d));
            }
            refreshTipSummary();
        });

        checkoutTipPercentField.addValueChangeListener(event -> {
            if (updatingTipFields) {
                return;
            }
            if (!TIP_MODE_PERCENT.equals(checkoutState.tipMode)) {
                return;
            }
            checkoutState.tipPercent = event.getValue() == null ? 0d : Math.max(0d, event.getValue());
            checkoutState.tipAmount = roundCurrency(calculateTipBaseTotal() * (checkoutState.tipPercent / 100d));
            refreshTipSummary();
        });

        TextArea comments = new TextArea("Comments");
        comments.setWidthFull();
        comments.setValue(checkoutState.comments);
        comments.addValueChangeListener(event -> checkoutState.comments = valueOrBlank(event.getValue()));

        card.add(contactEmail, contactPhone, contactName, street, city, postal,
            new HorizontalLayout(confirmLocation, clearLocation),
            locationStatusBlock);
        checkoutTipBlock.add(
                checkoutTipModeField,
                checkoutTipPercentField,
                checkoutTipAmountField,
                tipInfoBlock);
        checkoutTipBlock.setVisible(isOnlinePaymentMethod(checkoutState.paymentMethod));
        card.add(paymentMethod, checkoutTipBlock, fulfilmentOption, comments);

        syncTipFields();

        Button placeOrder = new Button("Place Delivery Order Now", event -> {
            if (cartLines.isEmpty()) {
                showError("Add at least one item before placing the order");
                return;
            }
            try {
                SubmissionResult result = submissionService.submitOrder(buildSubmitOrderRequest());
                cartLines.clear();
                checkoutState.clear();
                applyDefaultCheckoutCityIfMissing();
                renderCart();
                if (mobileOverlayMode) {
                    closeCartOverlay();
                }
                showSuccess(result.message());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        placeOrder.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        placeOrder.setWidthFull();
        placeOrder.setEnabled(resolveCurrentDeliveryQuote().matched());
        card.add(placeOrder);
        return card;
    }

    private SubmitOrderRequest buildSubmitOrderRequest() {
        return new SubmitOrderRequest(
                restaurantId,
                checkoutState.contactName,
                checkoutState.contactEmail,
                checkoutState.contactPhone,
                checkoutState.street,
                checkoutState.city,
                checkoutState.postalCode,
            checkoutState.customerLatitude,
            checkoutState.customerLongitude,
                checkoutState.paymentMethod,
                checkoutState.inPersonDelivery,
                calculateTipAmount(),
                checkoutState.comments,
                cartLines.stream()
                        .map(line -> new LineItemRequest(
                                line.item().id(),
                                line.item().name(),
                                line.selectedSize() == null ? "" : line.selectedSize().name(),
                                line.quantity(),
                                line.unitPrice(),
                                line.instructions(),
                                line.primaryTaxCategory(),
                                line.selections().stream()
                                        .map(selection -> new SelectedOptionRequest(
                                                selection.groupName(),
                                                selection.optionName(),
                                                selection.unitPrice(),
                                                selection.quantity(),
                                                selection.taxationCategory()))
                                        .toList()))
                        .toList());
    }

    private void applyFulfillmentRule(String paymentMethod, Checkbox fulfilmentOption) {
        boolean onlinePayment = isOnlinePaymentMethod(paymentMethod);
        fulfilmentOption.setVisible(onlinePayment);

        if (onlinePayment) {
            checkoutState.inPersonDelivery = false;
            fulfilmentOption.setValue(false);
            return;
        }

        checkoutState.inPersonDelivery = true;
        fulfilmentOption.setValue(true);
    }

    private HorizontalLayout buildTotalRow(String label, double value) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        Span left = createMutedText(label);
        Span right = createMutedText(formatCurrency(value));
        row.add(left, right);
        row.expand(left);
        return row;
    }

    private HorizontalLayout buildTotalRowWithLabelActions(String label, double value, Component... actions) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout left = new HorizontalLayout();
        left.setPadding(false);
        left.setSpacing(true);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.add(createMutedText(label));
        if (actions != null) {
            for (Component action : actions) {
                if (action != null) {
                    left.add(action);
                }
            }
        }

        Span right = createMutedText(formatCurrency(value));
        row.add(left, right);
        row.expand(left);
        return row;
    }

    private HorizontalLayout buildStrongTotalRow(String label, double value) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        Span left = new Span(label);
        left.getStyle().set("font-weight", "700");
        Span right = new Span(formatCurrency(value));
        right.getStyle().set("font-weight", "700");
        row.add(left, right);
        row.expand(left);
        return row;
    }

    private HorizontalLayout buildInfoRow(String label, Span valueSpan) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        Span left = createMutedText(label);
        valueSpan.getStyle().set("font-weight", "600");
        row.add(left, valueSpan);
        row.expand(left);
        return row;
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

    private Span createMutedText(String text) {
        Span span = new Span(text);
        span.getStyle().set("color", "var(--lumo-secondary-text-color)");
        span.getStyle().set("font-size", "var(--lumo-font-size-s)");
        return span;
    }

    private Button createInlineInfoButton(String tooltip, Runnable action) {
        Button button = new Button(VaadinIcon.INFO_CIRCLE_O.create(), event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.getStyle().set("padding", "0");
        button.getStyle().set("min-width", "var(--lumo-size-s)");
        button.setTooltipText(tooltip);
        return button;
    }

    private Button createInlineActionButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.getStyle().set("padding", "0 var(--lumo-space-xs)");
        button.getStyle().set("min-width", "var(--lumo-size-s)");
        button.setTooltipText(tooltip);
        return button;
    }

    private void openInfoDialog(String title, String bodyText) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        Div body = new Div();
        body.getStyle().set("white-space", "pre-wrap");
        body.getStyle().set("max-width", "520px");
        body.setText(bodyText);

        Button close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(body);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private void openFeesTaxesBreakdownDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Fees and Taxes Breakdown (Preview)");

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidth("420px");

        for (TaxLine taxLine : calculateUsedCategoryTaxLines()) {
            body.add(buildInfoRow(formatTaxLineLabel(taxLine.taxName(), taxLine.ratePercent()), new Span(formatCurrency(taxLine.amount()))));
        }
        body.add(buildInfoRow("Service fee", new Span(formatCurrency(calculateServiceFee()))));
        body.add(buildInfoRow(
                "Service fee tax (" + formatTaxLabel(previewData.serviceFeeTax().taxName(), previewData.serviceFeeTax().percentage()) + ")",
                new Span(formatCurrency(calculateServiceFeeTax()))));
        body.add(buildInfoRow("Delivery fee", new Span(formatCurrency(calculateDeliveryFee()))));
        body.add(buildInfoRow(
                "Delivery fee tax (" + formatTaxLabel(previewData.deliveryFeeTax().taxName(), previewData.deliveryFeeTax().percentage()) + ")",
                new Span(formatCurrency(calculateDeliveryFeeTax()))));

        Button close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(body);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private String resolveDeliveryFeeInfoText() {
        String configured = previewData.deliveryFeeInfoText();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "A delivery fee of {{DELIVERY_FEE}} applies to each order.";
    }

    private String resolveFeesTaxesInfoText() {
        String configured = previewData.feesTaxesInfoText();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "Fees and taxes include item taxes, service fee ({{SERVICE_FEE_PERCENT}}), service fee tax, delivery fee ({{DELIVERY_FEE}}), and delivery fee tax.";
    }

    private String renderInfoTextWithPlaceholders(String template) {
        String withDeliveryFee = template.replace("{{DELIVERY_FEE}}", formatCurrency(calculateDeliveryFee()));
        return withDeliveryFee.replace("{{SERVICE_FEE_PERCENT}}", formatPercent(previewData.serviceFeeRate() * 100d));
    }

    private DeliveryZoneService.DeliveryQuote resolveCurrentDeliveryQuote() {
        if (previewData == null || previewData.restaurant() == null) {
            return new DeliveryZoneService.DeliveryQuote(false, true, 0d, null, null, "");
        }
        return deliveryZoneService.quoteForRestaurant(
                previewData.restaurant(),
                checkoutState.customerLatitude,
                checkoutState.customerLongitude);
    }

    private String resolveCheckoutLocationStatus() {
        if (checkoutState.customerLatitude == null || checkoutState.customerLongitude == null) {
            return "Confirm the delivery point before placing the order.";
            
        }

        DeliveryZoneService.DeliveryQuote deliveryQuote = resolveCurrentDeliveryQuote();
        StringBuilder status = new StringBuilder(String.format(
                Locale.CANADA,
                "Confirmed coordinates: %.6f, %.6f",
                checkoutState.customerLatitude,
                checkoutState.customerLongitude));
        if (deliveryQuote.matched()) {
            status.append(" | Fee ").append(formatCurrency(deliveryQuote.deliveryFee()));
            if (deliveryQuote.zoneName() != null && !deliveryQuote.zoneName().isBlank()) {
                status.append(" | ").append(deliveryQuote.zoneName());
            }
        } else if (deliveryQuote.message() != null && !deliveryQuote.message().isBlank()) {
            status.append(" | ").append(deliveryQuote.message());
        }
        return status.toString();
    }

    private void clearConfirmedCustomerLocation(boolean rerender) {
        if (checkoutState.customerLatitude == null && checkoutState.customerLongitude == null) {
            return;
        }
        checkoutState.customerLatitude = null;
        checkoutState.customerLongitude = null;
        if (rerender && previewData != null) {
            renderCart();
        }
    }

    private void openCheckoutLocationDialog() {
        applyDefaultCheckoutCityIfMissing();
        String address = buildCheckoutAddress();
        Optional<LocationLookupService.LookupResult> geocoded = locationLookupService.geocodeAddress(address);
        Optional<DeliveryZoneService.BaseLocation> baseLocation = deliveryZoneService.getBaseLocation(previewData.restaurant().getTeamId());

        boolean geocodeFailedForAddress = !address.isBlank() && geocoded.isEmpty();

        LeafletPointPickerDialog.SelectedPoint centerPoint = geocoded
                .map(result -> new LeafletPointPickerDialog.SelectedPoint(result.latitude(), result.longitude()))
                .or(() -> checkoutState.customerLatitude != null && checkoutState.customerLongitude != null
                        ? Optional.of(new LeafletPointPickerDialog.SelectedPoint(checkoutState.customerLatitude, checkoutState.customerLongitude))
                        : Optional.empty())
                .or(() -> baseLocation
                        .filter(DeliveryZoneService.BaseLocation::hasCoordinates)
                        .map(base -> new LeafletPointPickerDialog.SelectedPoint(base.latitude(), base.longitude())))
                .orElse(new LeafletPointPickerDialog.SelectedPoint(51.037d, -113.402d));

        LeafletPointPickerDialog.SelectedPoint initialSelection = checkoutState.customerLatitude != null && checkoutState.customerLongitude != null
                ? new LeafletPointPickerDialog.SelectedPoint(checkoutState.customerLatitude, checkoutState.customerLongitude)
                : geocoded.map(result -> new LeafletPointPickerDialog.SelectedPoint(result.latitude(), result.longitude())).orElse(null);

        String helpText = geocoded.isPresent()
                ? "The map is centered on the typed address. Adjust the point if needed, then confirm it."
            : geocodeFailedForAddress
                ? String.format(
                    Locale.CANADA,
                    "%s not found. Please select your location on the map to continue.",
                    address)
                : "Click the delivery point on the map, then confirm it.";

        LeafletPointPickerDialog dialog = new LeafletPointPickerDialog(
                "Confirm Delivery Location",
                helpText,
                centerPoint,
                initialSelection,
                point -> {
                    checkoutState.customerLatitude = point.latitude();
                    checkoutState.customerLongitude = point.longitude();
                    renderCart();
                });
        dialog.open();
    }

    private String buildCheckoutAddress() {
        List<String> parts = new ArrayList<>();
        if (checkoutState.street != null && !checkoutState.street.isBlank()) {
            parts.add(checkoutState.street.trim());
        }
        if (checkoutState.city != null && !checkoutState.city.isBlank()) {
            parts.add(checkoutState.city.trim());
        }
        if (checkoutState.postalCode != null && !checkoutState.postalCode.isBlank()) {
            parts.add(checkoutState.postalCode.trim());
        }
        return String.join(", ", parts);
    }

    private void applyDefaultCheckoutCityIfMissing() {
        if (checkoutState.city != null && !checkoutState.city.isBlank()) {
            return;
        }

        String defaultCity = resolveDefaultCheckoutCity();
        if (!defaultCity.isBlank()) {
            checkoutState.city = defaultCity;
        }
    }

    private String resolveDefaultCheckoutCity() {
        if (previewData == null || previewData.restaurant() == null) {
            return "";
        }

        String fromRestaurantLocation = extractCityFromAddress(previewData.restaurant().getLocationAddress());
        if (!fromRestaurantLocation.isBlank()) {
            return fromRestaurantLocation;
        }

        return deliveryZoneService.getBaseLocation(previewData.restaurant().getTeamId())
                .map(DeliveryZoneService.BaseLocation::address)
                .map(this::extractCityFromAddress)
                .filter(city -> !city.isBlank())
                .orElse("");
    }

    private String extractCityFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }

        String normalized = address.trim();
        List<String> tokens = java.util.Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!isLikelyLocalityToken(token)) {
                continue;
            }

            String province = i + 1 < tokens.size() ? extractProvinceCode(tokens.get(i + 1)) : "";
            return province.isBlank() ? token : token + ", " + province;
        }

        if (normalized.toLowerCase(Locale.ROOT).contains("strathmore")) {
            String province = extractProvinceCode(normalized);
            return province.isBlank() ? "Strathmore" : "Strathmore, " + province;
        }
        return "";
    }

    private boolean isLikelyLocalityToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        if (upper.contains("CANADA")) {
            return false;
        }
        if (!extractProvinceCode(token).isBlank()) {
            return false;
        }
        if (token.chars().anyMatch(Character::isDigit)) {
            return false;
        }

        String compact = upper.replace('.', ' ');
        return !compact.matches(".*\\b(STREET|ST|AVENUE|AVE|ROAD|RD|DRIVE|DR|BOULEVARD|BLVD|LANE|LN|WAY|TRAIL|TR|CRES|CRESCENT|COURT|CT|PLACE|PL|CIRCLE|CIR|HIGHWAY|HWY)\\b.*");
    }

    private String extractProvinceCode(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim().toUpperCase(Locale.ROOT);
        String first = trimmed.split("\\s+")[0];
        return PROVINCE_CODES.contains(first) ? first : "";
    }

    private double minimumDisplayPrice(ItemData item) {
        if (item.sizes().isEmpty()) {
            return item.basePrice();
        }
        return item.sizes().stream().mapToDouble(SizeData::absolutePrice).min().orElse(item.basePrice());
    }

    private double resolveBaseUnitPrice(ItemData item, SizeData selectedSize) {
        if (selectedSize != null) {
            return selectedSize.absolutePrice();
        }
        return item.basePrice();
    }

    private double calculateSubTotal() {
        return roundCurrency(cartLines.stream().mapToDouble(OrderLine::totalPrice).sum());
    }

    private double calculateServiceFee() {
        return roundCurrency(calculateSubTotal() * previewData.serviceFeeRate());
    }

    private double calculateCategoryTax() {
        return roundCurrency(calculateUsedCategoryTaxLines().stream().mapToDouble(TaxLine::amount).sum());
    }

    private double calculateServiceFeeTax() {
        double serviceFee = calculateServiceFee();
        double rate = previewData.serviceFeeTax().percentage();
        if (serviceFee <= 0d || rate <= 0d) {
            return 0d;
        }
        return roundCurrency(serviceFee * (rate / 100d));
    }

    private double calculateDeliveryFee() {
        DeliveryZoneService.DeliveryQuote deliveryQuote = resolveCurrentDeliveryQuote();
        if (!deliveryQuote.matched()) {
            return 0d;
        }
        return roundCurrency(deliveryQuote.deliveryFee());
    }

    private double calculateDeliveryFeeTax() {
        double deliveryFee = calculateDeliveryFee();
        double rate = previewData.deliveryFeeTax().percentage();
        if (deliveryFee <= 0d || rate <= 0d) {
            return 0d;
        }
        return roundCurrency(deliveryFee * (rate / 100d));
    }

    private double calculateFeesAndTaxesTotal() {
        return roundCurrency(
                calculateCategoryTax()
                        + calculateServiceFee()
                        + calculateServiceFeeTax()
                        + calculateDeliveryFee()
                        + calculateDeliveryFeeTax());
    }

    private double calculateTipBaseTotal() {
        return roundCurrency(calculateSubTotal()
                + calculateServiceFee()
                + calculateCategoryTax()
                + calculateServiceFeeTax()
                + calculateDeliveryFee()
                + calculateDeliveryFeeTax());
    }

    private List<TaxLine> calculateUsedCategoryTaxLines() {
        Map<String, Double> taxByCategory = new LinkedHashMap<>();
        for (OrderLine line : cartLines) {
            double lineSubtotal = line.totalPrice();
            double optionSubtotalTotal = 0d;

            for (SelectedOptionLine selection : line.selections()) {
                double optionSubtotal = roundCurrency(selection.unitPrice() * selection.quantity() * line.quantity());
                if (optionSubtotal > 0d) {
                    optionSubtotalTotal = roundCurrency(optionSubtotalTotal + optionSubtotal);
                }

                String optionCategory = normalizeTaxationCategory(resolveSelectionTaxationCategory(line, selection));
                if (optionCategory.isEmpty()) {
                    continue;
                }

                Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> optionRate =
                        findTaxRateEntry(previewData.taxationRates(), optionCategory);
                if (optionRate == null || optionRate.getValue().percentage() <= 0d || optionSubtotal <= 0d) {
                    continue;
                }

                double optionTax = roundCurrency(optionSubtotal * (optionRate.getValue().percentage() / 100d));
                taxByCategory.merge(optionRate.getKey(), optionTax, Double::sum);
            }

            double baseSubtotal = roundCurrency(lineSubtotal - optionSubtotalTotal);
            String baseCategory = normalizeTaxationCategory(line.primaryTaxCategory());
            if (baseSubtotal > 0d && !baseCategory.isEmpty()) {
                Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> baseRate =
                        findTaxRateEntry(previewData.taxationRates(), baseCategory);
                if (baseRate != null && baseRate.getValue().percentage() > 0d) {
                    double baseTax = roundCurrency(baseSubtotal * (baseRate.getValue().percentage() / 100d));
                    taxByCategory.merge(baseRate.getKey(), baseTax, Double::sum);
                }
            }
        }

        Map<String, TaxLine> mergedByTaxIdentity = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : taxByCategory.entrySet()) {
            RestaurantMenuOrderPreviewService.TaxRateConfig config = previewData.taxationRates().get(entry.getKey());
            if (config == null) {
                continue;
            }
            String identity = buildTaxIdentityKey(config.taxName(), config.percentage());
            TaxLine existing = mergedByTaxIdentity.get(identity);
            if (existing == null) {
                mergedByTaxIdentity.put(identity,
                        new TaxLine(config.taxName(), config.percentage(), roundCurrency(entry.getValue())));
                continue;
            }
            mergedByTaxIdentity.put(identity,
                    new TaxLine(
                            existing.taxName(),
                            existing.ratePercent(),
                            roundCurrency(existing.amount() + entry.getValue())));
        }
        return new ArrayList<>(mergedByTaxIdentity.values());
    }

    private String buildTaxIdentityKey(String taxName, double percentage) {
        String name = taxName == null ? "" : taxName.trim().toLowerCase(Locale.CANADA);
        return name + "|" + String.format(Locale.CANADA, "%.4f", roundCurrency(percentage));
    }

    private String normalizeTaxationCategory(String category) {
        if (category == null || category.isBlank()) {
            return "";
        }
        return category.trim();
    }

    private String resolveSelectionTaxationCategory(OrderLine line, SelectedOptionLine selection) {
        String direct = normalizeTaxationCategory(selection.taxationCategory());
        if (!direct.isEmpty()) {
            return direct;
        }

        List<OptionGroupData> candidates = new ArrayList<>();
        if (line.item() != null && line.item().optionGroups() != null) {
            candidates.addAll(line.item().optionGroups());
        }
        if (line.selectedSize() != null && line.selectedSize().optionGroups() != null) {
            candidates.addAll(line.selectedSize().optionGroups());
        }

        OptionGroupData fallbackGroupMatch = null;
        for (OptionGroupData group : candidates) {
            if (group == null || group.name() == null) {
                continue;
            }
            if (!group.name().equalsIgnoreCase(selection.groupName())) {
                continue;
            }
            if (fallbackGroupMatch == null) {
                fallbackGroupMatch = group;
            }
            boolean optionMatches = group.options().stream()
                    .filter(option -> option != null && option.name() != null)
                    .anyMatch(option -> option.name().equalsIgnoreCase(selection.optionName()));
            if (optionMatches) {
                return normalizeTaxationCategory(group.taxationCategory());
            }
        }

        if (fallbackGroupMatch != null) {
            return normalizeTaxationCategory(fallbackGroupMatch.taxationCategory());
        }
        return "";
    }

    private Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> findTaxRateEntry(
            Map<String, RestaurantMenuOrderPreviewService.TaxRateConfig> taxationRates,
            String category) {
        if (taxationRates == null || category == null || category.isBlank()) {
            return null;
        }
        RestaurantMenuOrderPreviewService.TaxRateConfig exact = taxationRates.get(category);
        if (exact != null) {
            return Map.entry(category, exact);
        }
        for (Map.Entry<String, RestaurantMenuOrderPreviewService.TaxRateConfig> entry : taxationRates.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(category)) {
                return entry;
            }
        }
        return null;
    }

    private String formatTaxLineLabel(String taxName, double percentage) {
        return formatTaxLabel(taxName, percentage);
    }

    private String formatTaxLabel(String taxName, double percentage) {
        String normalizedName = taxName == null ? "" : taxName.trim();
        String rate = formatPercent(percentage);
        if (normalizedName.isEmpty()) {
            return rate;
        }
        return normalizedName + " (" + rate + ")";
    }

    private String formatPercent(double percentage) {
        String value = String.format(java.util.Locale.CANADA, "%.2f", percentage).replaceAll("\\.?0+$", "");
        return value + "%";
    }

    private String formatDistance(Double km) {
        if (km == null) {
            return "";
        }
        return String.format(Locale.CANADA, "%.2f km", km);
    }

    private double calculateTipAmount() {
        return roundCurrency(checkoutState.tipAmount);
    }

    private double calculateGrandTotal() {
        return roundCurrency(calculateTipBaseTotal() + calculateTipAmount());
    }

    private void refreshTipSummary() {
        if (checkoutTipBaseTotalValue != null) {
            checkoutTipBaseTotalValue.setText(formatCurrency(calculateTipBaseTotal()));
        }
        if (checkoutTipAmountValue != null) {
            checkoutTipAmountValue.setText(formatCurrency(calculateTipAmount()));
        }
        if (checkoutEstimatedTotalValue != null) {
            checkoutEstimatedTotalValue.setText(formatCurrency(calculateGrandTotal()));
        }
    }

    private void syncTipFields() {
        updatingTipFields = true;
        try {
            if (checkoutTipModeField != null && !Objects.equals(checkoutTipModeField.getValue(), checkoutState.tipMode)) {
                checkoutTipModeField.setValue(checkoutState.tipMode);
            }
            boolean percentMode = TIP_MODE_PERCENT.equals(checkoutState.tipMode);
            if (checkoutTipPercentField != null) {
                checkoutTipPercentField.setVisible(percentMode);
                Double percentValue = roundCurrency(checkoutState.tipPercent);
                if (!Objects.equals(checkoutTipPercentField.getValue(), percentValue)) {
                    checkoutTipPercentField.setValue(percentValue);
                }
            }
            if (checkoutTipAmountField != null) {
                checkoutTipAmountField.setVisible(!percentMode);
                checkoutTipAmountField.setReadOnly(false);
                Double amountValue = roundCurrency(checkoutState.tipAmount);
                if (!Objects.equals(checkoutTipAmountField.getValue(), amountValue)) {
                    checkoutTipAmountField.setValue(amountValue);
                }
            }
            if (checkoutTipAmountValue != null) {
                checkoutTipAmountValue.setVisible(true);
            }
            if (checkoutTipBlock != null) {
                checkoutTipBlock.setVisible(isOnlinePaymentMethod(checkoutState.paymentMethod));
            }
        } finally {
            updatingTipFields = false;
        }
        refreshTipSummary();
    }

    private boolean isOnlinePaymentMethod(String paymentMethod) {
        return "Online".equalsIgnoreCase(paymentMethod);
    }

    private String formatCurrency(double value) {
        return String.format(Locale.CANADA, "$%.2f", roundCurrency(value));
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String priceSuffix(double price) {
        return price == 0d ? "" : " (" + formatCurrency(price) + ")";
    }

    private String labelSuffix(OptionGroupData group) {
        List<String> parts = new ArrayList<>();
        if (group.required()) {
            parts.add("Required");
        }
        if (group.forceMin() > 0) {
            parts.add("Min " + group.forceMin());
        }
        if (group.forceMax() > 0) {
            parts.add("Max " + group.forceMax());
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    private int toInt(Double value) {
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Optional<Long> parseLong(String raw) {
        try {
            return Optional.of(Long.valueOf(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void attemptCustomerPrefill() {
        if (customerProfileService == null) {
            return;
        }

        if ((checkoutState.contactEmail == null || checkoutState.contactEmail.isBlank())
                && (checkoutState.contactPhone == null || checkoutState.contactPhone.isBlank())) {
            return;
        }

        customerProfileService.findPrefillCandidate(checkoutState.contactEmail, checkoutState.contactPhone)
                .ifPresent(prefill -> {
                    boolean changed = false;
                    boolean clearConfirmedLocation = false;

                    String preferredName = prefill.fullName().isBlank()
                            ? (prefill.firstName() + " " + prefill.lastName()).trim()
                            : prefill.fullName();

                    if (shouldApplyValue(checkoutState.contactName, preferredName)) {
                        checkoutState.contactName = preferredName;
                        changed = true;
                    }
                    if (shouldApplyValue(checkoutState.contactEmail, prefill.email())) {
                        checkoutState.contactEmail = prefill.email();
                        changed = true;
                    }
                    if (shouldApplyValue(checkoutState.contactPhone, prefill.phone())) {
                        checkoutState.contactPhone = prefill.phone();
                        changed = true;
                    }

                    List<CustomerProfileService.CustomerAddressChoice> addressChoices = prefill.addresses() == null
                            ? List.of()
                            : prefill.addresses();

                    if (addressChoices.size() > 1) {
                        // City can be prefilled from restaurant defaults; street is the best signal
                        // that the user has not chosen/typed a concrete delivery address yet.
                        if (isBlank(checkoutState.street)) {
                            openAddressChoiceDialog(prefill, addressChoices);
                        }
                    } else if (addressChoices.size() == 1) {
                        if (applyAddressChoice(addressChoices.get(0))) {
                            changed = true;
                        }
                    } else {
                        if (shouldApplyValue(checkoutState.street, prefill.streetAddress())) {
                            checkoutState.street = prefill.streetAddress();
                            changed = true;
                            clearConfirmedLocation = true;
                        }
                        if (shouldApplyValue(checkoutState.city, prefill.city())) {
                            checkoutState.city = prefill.city();
                            changed = true;
                            clearConfirmedLocation = true;
                        }
                        if (shouldApplyValue(checkoutState.postalCode, prefill.postalCode())) {
                            checkoutState.postalCode = prefill.postalCode();
                            changed = true;
                            clearConfirmedLocation = true;
                        }
                    }

                    if (changed) {
                        if (clearConfirmedLocation) {
                            clearConfirmedCustomerLocation(false);
                        }
                        renderCart();
                    }
                });
    }

    private boolean shouldApplyValue(String existing, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return false;
        }
        return existing == null || existing.isBlank();
    }

    private boolean applyAddressChoice(CustomerProfileService.CustomerAddressChoice choice) {
        boolean changed = false;
        if (choice == null) {
            return false;
        }
        String nextStreet = valueOrBlank(choice.streetAddress());
        if (!nextStreet.equals(checkoutState.street)) {
            checkoutState.street = nextStreet;
            changed = true;
        }
        String nextCity = valueOrBlank(choice.city());
        if (!nextCity.equals(checkoutState.city)) {
            checkoutState.city = nextCity;
            changed = true;
        }
        String nextPostal = valueOrBlank(choice.postalCode());
        if (!nextPostal.equals(checkoutState.postalCode)) {
            checkoutState.postalCode = nextPostal;
            changed = true;
        }

        boolean hasConfirmedSavedLocation = choice.latitude() != null
                && choice.longitude() != null
                && "MAP_CONFIRMED".equalsIgnoreCase(valueOrBlank(choice.locationSource()));

        if (hasConfirmedSavedLocation) {
            if (!Objects.equals(checkoutState.customerLatitude, choice.latitude())) {
                checkoutState.customerLatitude = choice.latitude();
                changed = true;
            }
            if (!Objects.equals(checkoutState.customerLongitude, choice.longitude())) {
                checkoutState.customerLongitude = choice.longitude();
                changed = true;
            }
        } else {
            if (checkoutState.customerLatitude != null || checkoutState.customerLongitude != null) {
                checkoutState.customerLatitude = null;
                checkoutState.customerLongitude = null;
                changed = true;
            }
        }
        return changed;
    }

    private void openAddressChoiceDialog(
            CustomerProfileService.CustomerPrefill prefill,
            List<CustomerProfileService.CustomerAddressChoice> addressChoices) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Choose delivery address");

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidth("520px");
        body.add(createMutedText("This customer has multiple saved addresses. Choose one for this order or keep a new address."));

        RadioButtonGroup<String> selector = new RadioButtonGroup<>();
        selector.setWidthFull();
        selector.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);

        List<String> options = new ArrayList<>();
        for (int i = 0; i < addressChoices.size(); i++) {
            options.add("address-" + i);
        }
        options.add("new-address");

        selector.setItems(options);
        selector.setItemLabelGenerator(option -> {
            if ("new-address".equals(option)) {
                return "New address";
            }
            int index = Integer.parseInt(option.substring("address-".length()));
            CustomerProfileService.CustomerAddressChoice choice = addressChoices.get(index);
            String number = "Address " + (index + 1);
            String details = buildAddressDisplay(choice.streetAddress(), choice.city(), choice.postalCode());
            return details.isBlank() ? number : number + " - " + details;
        });
        selector.setValue(options.getFirst());

        Button apply = new Button("Apply", event -> {
            String selected = selector.getValue();
            if (selected != null && selected.startsWith("address-")) {
                int index = Integer.parseInt(selected.substring("address-".length()));
                if (index >= 0 && index < addressChoices.size()) {
                    if (applyAddressChoice(addressChoices.get(index))) {
                        renderCart();
                    }
                }
            }
            dialog.close();
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button keepNew = new Button("Keep current", event -> dialog.close());
        keepNew.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        body.add(selector);
        dialog.add(body);
        dialog.getFooter().add(keepNew, apply);
        dialog.open();
    }

    private String buildAddressDisplay(String street, String city, String postalCode) {
        List<String> parts = new ArrayList<>();
        if (street != null && !street.isBlank()) {
            parts.add(street.trim());
        }
        if (city != null && !city.isBlank()) {
            parts.add(city.trim());
        }
        if (postalCode != null && !postalCode.isBlank()) {
            parts.add(postalCode.trim());
        }
        return String.join(", ", parts);
    }

    private record OptionChoice(OptionData option) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof OptionChoice other)) {
                return false;
            }
            return Objects.equals(option == null ? null : option.id(), other.option == null ? null : other.option.id());
        }

        @Override
        public int hashCode() {
            return Objects.hash(option == null ? null : option.id());
        }
    }

    private record MultiSelectionRow(OptionData option, Checkbox selected, NumberField quantity) {
    }

    private record SelectedOptionLine(String groupName, String optionName, double unitPrice, int quantity, String taxationCategory) {
    }

    private record GroupCollector(
            Component component,
            java.util.function.Supplier<List<SelectedOptionLine>> collectSelections,
            java.util.function.Supplier<Optional<String>> validate) {
    }

        private record TaxLine(String taxName, double ratePercent, double amount) {
        }

    private static final class OrderLine {
        private final ItemData item;
        private final SizeData selectedSize;
        private final List<SelectedOptionLine> selections;
        private final String instructions;
        private final int quantity;
        private final double unitPrice;

        private OrderLine(
                ItemData item,
                SizeData selectedSize,
                List<SelectedOptionLine> selections,
                String instructions,
                int quantity,
                double unitPrice) {
            this.item = item;
            this.selectedSize = selectedSize;
            this.selections = List.copyOf(selections);
            this.instructions = instructions;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        private ItemData item() {
            return item;
        }

        private SizeData selectedSize() {
            return selectedSize;
        }

        private List<SelectedOptionLine> selections() {
            return selections;
        }

        private String instructions() {
            return instructions;
        }

        private int quantity() {
            return quantity;
        }

        private double unitPrice() {
            return unitPrice;
        }

        private double totalPrice() {
            return BigDecimal.valueOf(unitPrice)
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        private String primaryTaxCategory() {
            String category = item == null ? null : item.taxationCategory();
            if (category == null || category.isBlank()) {
                return "Food";
            }
            return category.trim();
        }
    }

    private static final class CheckoutState {
        private String contactName = "";
        private String contactEmail = "";
        private String contactPhone = "";
        private String street = "";
        private String city = "";
        private String postalCode = "";
        private Double customerLatitude;
        private Double customerLongitude;
        private String paymentMethod = "Cash";
        private boolean inPersonDelivery = false;
        private String tipMode = TIP_MODE_AMOUNT;
        private double tipAmount = 0d;
        private double tipPercent = 0d;
        private String comments = "";

        private void clear() {
            contactName = "";
            contactEmail = "";
            contactPhone = "";
            street = "";
            city = "";
            postalCode = "";
            customerLatitude = null;
            customerLongitude = null;
            paymentMethod = "Cash";
            inPersonDelivery = false;
            tipMode = TIP_MODE_AMOUNT;
            tipAmount = 0d;
            tipPercent = 0d;
            comments = "";
        }
    }
}