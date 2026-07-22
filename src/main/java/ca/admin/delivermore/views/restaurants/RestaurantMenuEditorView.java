package ca.admin.delivermore.views.restaurants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dnd.GridDropEvent;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuCategory;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItem;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemSize;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOption;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionGroup;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuVersion;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuVersion.WorkflowStatus;
import ca.admin.delivermore.collector.data.service.TeamsRepository;
import ca.admin.delivermore.components.custom.MenuImagePickerDialog;
import ca.admin.delivermore.data.service.MenuImageAssetService;
import ca.admin.delivermore.data.service.MenuImageSlot;
import ca.admin.delivermore.data.service.DeliveryZoneService;
import ca.admin.delivermore.data.service.RestaurantMenuEditorService;
import ca.admin.delivermore.views.MainLayout;
import ca.admin.delivermore.views.UIUtilities;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("Restaurant Menu Editor")
@Route(value = "restaurants/menu-editor", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RestaurantMenuEditorView extends VerticalLayout implements BeforeEnterObserver {

    private static final String RESTAURANT_ID_QP = "restaurantId";
    private static final String CATEGORY_TYPE = "category";
    private static final String ITEM_TYPE = "item";
    private static final String ITEM_SIZE_TYPE = "item_size";
        private static final String NUTRITION_PER_100G = "per_100g";
        private static final String NUTRITION_PER_SERVING = "per_serving";
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
        private static final List<String> ITEM_NUTRITION_FIELDS = List.of(
            "Total Calories (kcal)",
            "Carbohydrate (g)",
            "Total Fat (g)",
            "Protein (g)",
            "Sugar (g)",
            "Salt (g)");

    private final RestaurantMenuEditorService restaurantMenuEditorService;
    private final MenuImageAssetService menuImageAssetService;

    // UI Components
    private final H3 title = new H3("Restaurant Menu Editor");
    private final Span statusBanner = new Span();
    private final ComboBox<Restaurant> restaurantSelector = new ComboBox<>("Restaurant");
    private final Button createDraftButton = new Button("Create Draft");
    private final Button publishDraftButton = new Button("Publish Draft");
    private final Button previewButton = new Button("Preview & Test Ordering");
    private final MenuBar dataTablesMenu;
    private final Button refreshButton = new Button("Refresh");
    private final Image menuHeaderPreview = new Image();
    private final Button menuHeaderSelectButton = new Button("Select Header Image");
    
    // Left pane: TreeGrid hierarchy
    private final TreeGrid<MenuTreeNode> editorTree = new TreeGrid<>();
    
    // Right pane: Choice library
    private final Accordion choiceLibrary = new Accordion();
    
    // Data tracking
    private Long restaurantId;
    private Restaurant restaurant;
    private RestaurantMenuVersion currentMenuVersion;
    private final Map<RestaurantMenuCategory, List<RestaurantMenuItem>> categoryItemsMap = new LinkedHashMap<>();
    // category.id -> option groups linked to that category
    private final Map<Long, List<RestaurantMenuOptionGroup>> categoryOptionGroupsMap = new HashMap<>();
    // item.id → option groups linked to that item
    private final Map<Long, List<RestaurantMenuOptionGroup>> itemOptionGroupsMap = new HashMap<>();
    // item.id -> sizes linked to that item
    private final Map<Long, List<RestaurantMenuItemSize>> itemSizesMap = new HashMap<>();
    // optionGroup.id → options within that group
    private final Map<Long, List<RestaurantMenuOption>> groupOptionsMap = new HashMap<>();
    // Track expanded categories explicitly (TreeGrid API doesn't expose expanded items in this Vaadin version)
    private final Set<Long> expandedCategoryIds = new HashSet<>();
    private final List<String> itemTagOptions = new ArrayList<>(DEFAULT_ITEM_TAG_OPTIONS);
    private final List<String> itemAllergenOptions = new ArrayList<>(DEFAULT_ITEM_ALLERGEN_OPTIONS);
    private final List<String> majorGroupOptions = new ArrayList<>(DEFAULT_MAJOR_GROUP_OPTIONS);
    private final List<String> taxationCategoryOptions = new ArrayList<>(DEFAULT_TAXATION_CATEGORY_OPTIONS);
    private List<Restaurant> selectableRestaurants = List.of();
    private boolean updatingRestaurantSelector;
    
    // Helper class for TreeGrid nodes
    private record MenuTreeNode(String type, Object data, String displayName) {
    }

    // Tracks the node being dragged for reorder
    private MenuTreeNode draggedNode;
    private Long draggedChoiceGroupId;

        private record TreeUiState(Set<Long> expandedCategoryIds,
            Set<Long> expandedItemIds,
            Long selectedCategoryId,
            Long selectedItemId,
            Long selectedSizeId) {
    }

        private final Set<Long> expandedItemIds = new HashSet<>();

    private static final class ItemSettingsModel {
        private boolean outOfStock;
        private final Set<String> tags = new LinkedHashSet<>();
        private final Set<String> allergens = new LinkedHashSet<>();
        private String ingredients;
        private String additives;
        private String nutritionalSize = NUTRITION_PER_100G;
        private final Map<String, String> nutritionalValues = new LinkedHashMap<>();
    }

    public RestaurantMenuEditorView(
            RestaurantMenuEditorService restaurantMenuEditorService,
            MenuImageAssetService menuImageAssetService,
            DeliveryZoneService deliveryZoneService,
            TeamsRepository teamsRepository) {
        this.restaurantMenuEditorService = restaurantMenuEditorService;
        this.menuImageAssetService = menuImageAssetService;
        this.dataTablesMenu = new MenuDataTablesMenuBar(
                restaurantMenuEditorService,
                deliveryZoneService,
                teamsRepository,
                this::refreshItemSettingsOptionLists);
        setSizeFull();
        configureHeader();
        configureMenuHeaderControls();
        configureRestaurantSelector();
        configureEditorTree();
        configureChoiceLibrary();
        configureActions();

        SplitLayout splitLayout = new SplitLayout(editorTree, choiceLibrary);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(60); // 60% left, 40% right

        add(buildTopArea(), splitLayout);
        setFlexGrow(1, splitLayout);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        refreshSelectableRestaurants();

        Optional<String> restaurantIdValue = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault(RESTAURANT_ID_QP, List.of())
                .stream()
                .findFirst();

        Long requestedRestaurantId = restaurantIdValue.flatMap(this::parseLong).orElse(null);
        Restaurant selectedRestaurant = resolveSelectedRestaurant(requestedRestaurantId);
        if (selectedRestaurant == null) {
            restaurantId = null;
            restaurant = null;
            syncRestaurantSelector();
            clearEditor("No effective restaurants available.");
            return;
        }

        restaurant = selectedRestaurant;
        restaurantId = selectedRestaurant.getRestaurantId();
        syncRestaurantSelector();

        refreshEditorData();
    }

    private void configureHeader() {
        statusBanner.getStyle().set("padding", "var(--lumo-space-s)");
        statusBanner.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        statusBanner.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        statusBanner.setWidthFull();
    }

    private void configureRestaurantSelector() {
        restaurantSelector.setWidth("420px");
        restaurantSelector.setClearButtonVisible(false);
        restaurantSelector.setItemLabelGenerator(value -> {
            if (value == null) {
                return "";
            }
            return value.getName() + " (" + value.getRestaurantId() + ")";
        });
        restaurantSelector.addValueChangeListener(event -> {
            if (updatingRestaurantSelector || event.isFromClient() == false) {
                return;
            }
            Restaurant selected = event.getValue();
            if (selected == null || selected.getRestaurantId() == null) {
                return;
            }
            if (selected.getRestaurantId().equals(restaurantId)) {
                return;
            }
            UI.getCurrent().navigate("restaurants/menu-editor?restaurantId=" + selected.getRestaurantId());
        });
    }

    private VerticalLayout buildTopArea() {
        Button backButton = new Button("Back to Restaurants", e -> UI.getCurrent().navigate("restaurants"));
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout restaurantRow = new HorizontalLayout(restaurantSelector);
        restaurantRow.setWidthFull();
        restaurantRow.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout actions = new HorizontalLayout(createDraftButton, publishDraftButton, previewButton, dataTablesMenu, refreshButton);
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.setWidthFull();

        HorizontalLayout menuHeaderActions = new HorizontalLayout(menuHeaderSelectButton);
        menuHeaderActions.setSpacing(true);
        menuHeaderActions.setPadding(false);
        VerticalLayout menuHeaderRow = new VerticalLayout(
            new Span("Menu Header Image (16:9). If empty, preview uses restaurant logo."),
                menuHeaderPreview,
                menuHeaderActions);
        menuHeaderRow.setPadding(false);
        menuHeaderRow.setSpacing(true);

        VerticalLayout top = new VerticalLayout(backButton, title, restaurantRow, statusBanner, menuHeaderRow, actions);
        top.setPadding(false);
        top.setSpacing(true);
        top.setWidthFull();
        return top;
    }

    private void configureMenuHeaderControls() {
        menuHeaderPreview.setAlt("Menu header image preview");
        menuHeaderPreview.setWidth("280px");
        menuHeaderPreview.getStyle().set("height", "158px");
        menuHeaderPreview.getStyle().set("object-fit", "cover");
        menuHeaderPreview.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        menuHeaderPreview.getStyle().set("border-radius", "8px");
        menuHeaderPreview.getStyle().set("cursor", "pointer");
        menuHeaderPreview.getElement().setAttribute("title", "Click to select or edit header image");
        menuHeaderPreview.addClickListener(event -> openMenuHeaderImagePicker());

        menuHeaderSelectButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        menuHeaderSelectButton.addClickListener(event -> openMenuHeaderImagePicker());

    }

    private void openMenuHeaderImagePicker() {
        if (currentMenuVersion == null) {
            showError("No menu version loaded");
            return;
        }
        MenuImagePickerDialog picker = new MenuImagePickerDialog(
                menuImageAssetService,
                MenuImageSlot.MENU_HEADER,
                currentMenuVersion.getHeaderImageAssetId(),
                selectedId -> {
                    currentMenuVersion.setHeaderImageAssetId(selectedId);
                    restaurantMenuEditorService.saveMenuVersion(currentMenuVersion);
                    refreshMenuHeaderPreview();
                    showSuccess("Menu header updated");
                });
        picker.open();
    }

    private void refreshMenuHeaderPreview() {
        Long headerImageAssetId = currentMenuVersion == null ? null : currentMenuVersion.getHeaderImageAssetId();
        Long logoImageAssetId = restaurant == null ? null : restaurant.getLogoImageAssetId();
        Long assetIdToPreview = headerImageAssetId != null ? headerImageAssetId : logoImageAssetId;

        if (headerImageAssetId != null) {
            menuHeaderPreview.setWidth("280px");
            menuHeaderPreview.getStyle().set("height", "158px");
        } else if (logoImageAssetId != null) {
            menuHeaderPreview.setWidth("280px");
            menuHeaderPreview.getStyle().set("height", "280px");
        } else {
            menuHeaderPreview.setWidth("280px");
            menuHeaderPreview.getStyle().set("height", "158px");
        }

        String imageUrl = menuImageAssetService.getImageUrl(assetIdToPreview);
        if (imageUrl == null || imageUrl.isBlank()) {
            menuHeaderPreview.setSrc("");
            return;
        }
        menuHeaderPreview.setSrc(imageUrl);
    }

    private void refreshSelectableRestaurants() {
        selectableRestaurants = restaurantMenuEditorService.listEffectiveRestaurants();
        updatingRestaurantSelector = true;
        restaurantSelector.setItems(selectableRestaurants);
        updatingRestaurantSelector = false;
    }

    private Restaurant resolveSelectedRestaurant(Long requestedRestaurantId) {
        if (selectableRestaurants.isEmpty()) {
            return null;
        }

        if (requestedRestaurantId != null) {
            for (Restaurant candidate : selectableRestaurants) {
                if (requestedRestaurantId.equals(candidate.getRestaurantId())) {
                    return candidate;
                }
            }

            Restaurant fallback = restaurantMenuEditorService.getRestaurant(requestedRestaurantId);
            if (fallback != null) {
                return fallback;
            }
        }

        if (restaurantId != null) {
            for (Restaurant candidate : selectableRestaurants) {
                if (restaurantId.equals(candidate.getRestaurantId())) {
                    return candidate;
                }
            }
        }

        return selectableRestaurants.getFirst();
    }

    private void syncRestaurantSelector() {
        updatingRestaurantSelector = true;
        if (restaurant == null || restaurantId == null) {
            restaurantSelector.clear();
        } else {
            restaurantSelector.setValue(restaurant);
        }
        updatingRestaurantSelector = false;
    }

    private void refreshItemSettingsOptionLists() {
        itemTagOptions.clear();
        itemTagOptions.addAll(restaurantMenuEditorService.getItemTagOptions(DEFAULT_ITEM_TAG_OPTIONS));

        itemAllergenOptions.clear();
        itemAllergenOptions.addAll(restaurantMenuEditorService.getItemAllergenOptions(DEFAULT_ITEM_ALLERGEN_OPTIONS));

        majorGroupOptions.clear();
        majorGroupOptions.addAll(restaurantMenuEditorService.getMajorGroupOptions(DEFAULT_MAJOR_GROUP_OPTIONS));

        taxationCategoryOptions.clear();
        taxationCategoryOptions.addAll(restaurantMenuEditorService.getTaxationCategoryOptions(DEFAULT_TAXATION_CATEGORY_OPTIONS));
    }

    private void configureEditorTree() {
        editorTree.setSizeFull();
        editorTree.setSelectionMode(Grid.SelectionMode.SINGLE);
        
        // Configure hierarchical data provider
        editorTree.setDataProvider(new AbstractBackEndHierarchicalDataProvider<MenuTreeNode, Void>() {
            @Override
            public int getChildCount(HierarchicalQuery<MenuTreeNode, Void> query) {
                MenuTreeNode parent = query.getParent();
                if (parent == null) {
                    // Root level: categories
                    return categoryItemsMap.size();
                } else if (CATEGORY_TYPE.equals(parent.type())) {
                    // Children of category: items
                    RestaurantMenuCategory category = (RestaurantMenuCategory) parent.data();
                    List<RestaurantMenuItem> items = categoryItemsMap.get(category);
                    return items != null ? items.size() : 0;
                } else if (ITEM_TYPE.equals(parent.type())) {
                    RestaurantMenuItem item = (RestaurantMenuItem) parent.data();
                    List<RestaurantMenuItemSize> sizes = itemSizesMap.get(item.getId());
                    return sizes != null ? sizes.size() : 0;
                }
                return 0;
            }

            @Override
            protected Stream<MenuTreeNode> fetchChildrenFromBackEnd(HierarchicalQuery<MenuTreeNode, Void> query) {
                MenuTreeNode parent = query.getParent();
                if (parent == null) {
                    // Root level: return category nodes
                    return categoryItemsMap.keySet().stream()
                            .map(cat -> new MenuTreeNode(CATEGORY_TYPE, cat, cat.getName()));
                } else if (CATEGORY_TYPE.equals(parent.type())) {
                    // Children: return item nodes for this category
                    RestaurantMenuCategory category = (RestaurantMenuCategory) parent.data();
                    List<RestaurantMenuItem> items = categoryItemsMap.get(category);
                    return items != null
                            ? items.stream()
                                    .map(item -> new MenuTreeNode(ITEM_TYPE, item, item.getName()))
                            : Stream.empty();
                } else if (ITEM_TYPE.equals(parent.type())) {
                    RestaurantMenuItem item = (RestaurantMenuItem) parent.data();
                    List<RestaurantMenuItemSize> sizes = itemSizesMap.get(item.getId());
                    return sizes != null
                        ? sizes.stream().map(size -> new MenuTreeNode(ITEM_SIZE_TYPE, size, size.getName()))
                        : Stream.empty();
                }
                return Stream.empty();
            }

            @Override
            public boolean hasChildren(MenuTreeNode node) {
                if (node == null) {
                    return !categoryItemsMap.isEmpty();
                }
                if (CATEGORY_TYPE.equals(node.type())) {
                    RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
                    List<RestaurantMenuItem> items = categoryItemsMap.get(category);
                    return items != null && !items.isEmpty();
                }
                if (ITEM_TYPE.equals(node.type())) {
                    RestaurantMenuItem item = (RestaurantMenuItem) node.data();
                    List<RestaurantMenuItemSize> sizes = itemSizesMap.get(item.getId());
                    return sizes != null && !sizes.isEmpty();
                }
                return false;
            }
        });

        // Name column must be added first so it owns the expand/collapse toggle.
        // We reorder columns afterward to place the handle first.
        HorizontalLayout nameHeader = new HorizontalLayout();
        nameHeader.setPadding(false);
        nameHeader.setSpacing(false);
        nameHeader.setWidthFull();
        nameHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        nameHeader.getStyle().set("gap", "8px");
        Span nameHeaderLabel = new Span("Name");
        nameHeaderLabel.getStyle().set("font-weight", "600");
        Button addGroupButton = new Button("Add Group", VaadinIcon.PLUS.create(), event -> addCategoryAtEnd());
        addGroupButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        nameHeader.add(nameHeaderLabel, addGroupButton);

        Grid.Column<MenuTreeNode> nameCol = editorTree.addComponentHierarchyColumn(this::buildNameCell)
            .setHeader(nameHeader)
            .setResizable(true)
            .setFlexGrow(1);

        // Drag handle column — indent by hierarchy level for consistent visual nesting.
        Grid.Column<MenuTreeNode> handleCol = editorTree.addComponentColumn(node -> {
            Icon handle = VaadinIcon.MENU.create();
            handle.getStyle().set("cursor", "grab");
            handle.getStyle().set("color", "var(--lumo-secondary-text-color)");
            handle.setSize("16px");
            if (ITEM_TYPE.equals(node.type())) {
                handle.getStyle().set("margin-left", "20px");
            } else if (ITEM_SIZE_TYPE.equals(node.type())) {
                handle.getStyle().set("margin-left", "40px");
            }
            return handle;
        }).setWidth("72px").setFlexGrow(0).setResizable(false);

        // Display order column
        Grid.Column<MenuTreeNode> orderCol = editorTree.addColumn(node -> {
                    return switch (node.type()) {
                        case CATEGORY_TYPE -> ((RestaurantMenuCategory) node.data()).getDisplayOrder();
                        case ITEM_TYPE -> ((RestaurantMenuItem) node.data()).getDisplayOrder();
                        case ITEM_SIZE_TYPE -> ((RestaurantMenuItemSize) node.data()).getDisplayOrder();
                        default -> null;
                    };
                })
                .setHeader("Order")
                .setWidth("80px")
                .setFlexGrow(0);

        // Price column (items only) — formatted with explicit width
        Grid.Column<MenuTreeNode> priceCol = editorTree.addColumn(node -> {
                    if (ITEM_TYPE.equals(node.type())) {
                        Double price = ((RestaurantMenuItem) node.data()).getBasePrice();
                        return price != null ? String.format("$%.2f", price) : "";
                    } else if (ITEM_SIZE_TYPE.equals(node.type())) {
                        RestaurantMenuItemSize size = (RestaurantMenuItemSize) node.data();
                        return String.format("$%.2f", getDisplayPriceForSize(size));
                    }
                    return null;
                })
                .setHeader("Price")
                .setWidth("90px")
                .setFlexGrow(0);

        // Options column — shows compact status badges like Hidden, NoStock, and PreSelected.
        Grid.Column<MenuTreeNode> optionsCol = editorTree.addComponentColumn(this::buildOptionsBadges)
                .setHeader("Options")
                .setWidth("180px")
                .setFlexGrow(0);

            Grid.Column<MenuTreeNode> rowActionsCol = editorTree.addComponentColumn(this::buildRowActions)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        // Choices chip column — shows option group name chips on category and item rows
        Grid.Column<MenuTreeNode> choicesCol = editorTree.addComponentColumn(node -> {
                    List<RestaurantMenuOptionGroup> groups;
                    Long categoryId = null;
                    Long itemId = null;
                    if (CATEGORY_TYPE.equals(node.type())) {
                        RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
                        categoryId = category.getId();
                        groups = categoryOptionGroupsMap.get(categoryId);
                    } else if (ITEM_TYPE.equals(node.type())) {
                        RestaurantMenuItem item = (RestaurantMenuItem) node.data();
                        itemId = item.getId();
                        groups = itemOptionGroupsMap.get(itemId);
                    } else {
                        return new FlexLayout();
                    }
                    if (groups == null || groups.isEmpty()) return new FlexLayout();
                    FlexLayout chips = new FlexLayout();
                    chips.getStyle().set("flex-wrap", "wrap").set("gap", "4px");
                    final Long finalCategoryId = categoryId;
                    final Long finalItemId = itemId;
                    for (RestaurantMenuOptionGroup group : groups) {
                        HorizontalLayout chip = new HorizontalLayout();
                        chip.setSpacing(false);
                        chip.setPadding(false);
                        chip.setAlignItems(FlexComponent.Alignment.CENTER);
                        chip.getElement().getThemeList().add("badge pill");
                        chip.getStyle().set("gap", "4px").set("font-size", "var(--lumo-font-size-xxs)");

                        Span chipLabel = new Span(group.getName());
                        Button removeChip = new Button(VaadinIcon.CLOSE_SMALL.create());
                        removeChip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE,
                                ButtonVariant.LUMO_ICON,
                                ButtonVariant.LUMO_SMALL);
                        removeChip.getStyle()
                                .set("margin", "0")
                                .set("padding", "0")
                                .set("min-width", "var(--lumo-size-xs)")
                                .set("height", "var(--lumo-size-xs)");
                        removeChip.getElement().setAttribute("aria-label", "Remove choice group");
                        removeChip.addClickListener(event -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            try {
                                if (finalItemId != null) {
                                    restaurantMenuEditorService.removeOptionGroupFromItem(
                                            currentMenuVersion.getId(),
                                            finalItemId,
                                            group.getId());
                                    showSuccess("Choice removed from item");
                                } else if (finalCategoryId != null) {
                                    restaurantMenuEditorService.removeOptionGroupFromCategory(
                                            currentMenuVersion.getId(),
                                            finalCategoryId,
                                            group.getId());
                                    showSuccess("Choice removed from group");
                                }
                                refreshEditorData();
                            } catch (IllegalStateException ex) {
                                showError(ex.getMessage());
                            }
                        });

                        chip.add(chipLabel, removeChip);
                        chips.add(chip);
                    }
                    return chips;
                })
                .setHeader("Choices")
                .setFlexGrow(2);

        // Put handle column first visually
        @SuppressWarnings("unchecked")
        Grid.Column<MenuTreeNode>[] columnOrder = (Grid.Column<MenuTreeNode>[]) new Grid.Column[] {
            handleCol, nameCol, orderCol, priceCol, optionsCol, rowActionsCol, choicesCol
        };
        editorTree.setColumnOrder(columnOrder);

        // Enable drag/drop for reordering within the tree
        editorTree.setRowsDraggable(true);
        editorTree.setDropMode(GridDropMode.BETWEEN);
        editorTree.addDragStartListener(event ->
                draggedNode = event.getDraggedItems().stream().findFirst().orElse(null));
        editorTree.addDropListener(this::handleTreeDrop);
        editorTree.addExpandListener(event -> event.getItems().forEach(node -> {
            if (CATEGORY_TYPE.equals(node.type())) {
                expandedCategoryIds.add(((RestaurantMenuCategory) node.data()).getId());
            } else if (ITEM_TYPE.equals(node.type())) {
                expandedItemIds.add(((RestaurantMenuItem) node.data()).getId());
            }
        }));
        editorTree.addCollapseListener(event -> event.getItems().forEach(node -> {
            if (CATEGORY_TYPE.equals(node.type())) {
                expandedCategoryIds.remove(((RestaurantMenuCategory) node.data()).getId());
            } else if (ITEM_TYPE.equals(node.type())) {
                expandedItemIds.remove(((RestaurantMenuItem) node.data()).getId());
            }
        }));
    }

    private void configureChoiceLibrary() {
        choiceLibrary.setSizeFull();
        choiceLibrary.getStyle().set("overflow-y", "auto");
        // Content is populated in refreshChoiceLibrary() after data loads
    }

    private void refreshChoiceLibrary() {
        choiceLibrary.getChildren().toList().forEach(choiceLibrary::remove);

        if (currentMenuVersion == null) {
            AccordionPanel placeholder = choiceLibrary.add("No menu loaded", new Span("Load or create a draft to see choices"));
            placeholder.setEnabled(false);
            return;
        }

        List<RestaurantMenuOptionGroup> allGroups = restaurantMenuEditorService.listOptionGroupsForVersion(currentMenuVersion.getId());
        if (allGroups.isEmpty()) {
            AccordionPanel placeholder = choiceLibrary.add("No choice groups found", new Span("This menu has no option groups defined"));
            placeholder.setEnabled(false);
            return;
        }

        // Groups are persisted per linkage (often one row per item), so build a unique
        // "library" view to avoid repeated panels for the same source group.
        Map<String, RestaurantMenuOptionGroup> uniqueGroups = new LinkedHashMap<>();
        for (RestaurantMenuOptionGroup group : allGroups) {
            uniqueGroups.putIfAbsent(buildChoiceLibraryKey(group), group);
        }

        for (RestaurantMenuOptionGroup group : uniqueGroups.values()) {
            List<RestaurantMenuOption> options = groupOptionsMap.get(group.getId());
            VerticalLayout content = new VerticalLayout();
            content.setPadding(false);
            content.setSpacing(false);
            content.getStyle().set("gap", "4px");

            // Group meta info
            StringBuilder meta = new StringBuilder();
            if (Boolean.TRUE.equals(group.getRequiredSelection())) meta.append("Required");
            if (group.getForceMin() != null && group.getForceMin() > 0) {
                meta.append(meta.length() > 0 ? " · " : "").append("Min: ").append(group.getForceMin());
            }
            if (group.getForceMax() != null && group.getForceMax() > 0) {
                meta.append(meta.length() > 0 ? " · " : "").append("Max: ").append(group.getForceMax());
            }
            String majorGroup = trimToNull(restaurantMenuEditorService.getOptionGroupMajorGroup(group.getId()));
            if (majorGroup != null) {
                meta.append(meta.length() > 0 ? " · " : "").append("Major group: ").append(majorGroup);
            }
            String taxationCategory = trimToNull(restaurantMenuEditorService.getOptionGroupTaxationCategory(group.getId()));
            if (taxationCategory != null) {
                meta.append(meta.length() > 0 ? " · " : "").append("Tax: ").append(taxationCategory);
            }
            if (meta.length() > 0) {
                Span metaSpan = new Span(meta.toString());
                metaSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
                content.add(metaSpan);
            }

            if (options != null) {
                for (RestaurantMenuOption option : options) {
                    HorizontalLayout optRow = new HorizontalLayout();
                    optRow.setSpacing(false);
                    optRow.getStyle().set("gap", "8px").set("padding", "2px 0 2px 12px");
                    optRow.setWidthFull();
                    Span optName = new Span(option.getName());
                    optName.getStyle()
                            .set("font-size", "var(--lumo-font-size-s)")
                            .set("flex", "1")
                            .set("min-width", "0");
                    optRow.add(optName);
                    Double optionPriceValue = option.getPrice();
                    double optionPrice = optionPriceValue != null ? optionPriceValue : 0d;
                    Span price = new Span(String.format("+$%.2f", optionPrice));
                    price.getStyle()
                            .set("font-size", "var(--lumo-font-size-xs)")
                            .set("color", "var(--lumo-secondary-text-color)")
                            .set("width", "80px")
                            .set("text-align", "right")
                            .set("font-variant-numeric", "tabular-nums");
                    optRow.add(price);

                        FlexLayout optionBadges = buildChoiceOptionBadges(option);
                        optionBadges.getStyle().set("width", "140px");
                        optRow.add(optionBadges);

                        Button editOption = iconButton(VaadinIcon.PENCIL, "Edit choice", event -> openChoiceDialog(group, option));
                        Button duplicateOption = iconButton(VaadinIcon.COPY_O, "Duplicate choice", event -> confirmAction(
                            "Duplicate choice",
                            "Create a copy of choice '" + option.getName() + "'?",
                            () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.duplicateOption(currentMenuVersion.getId(), option.getId());
                            showSuccess("Choice duplicated");
                            refreshEditorData();
                            }));
                        Button removeOption = iconButton(VaadinIcon.TRASH, "Remove choice", event -> confirmAction(
                            "Remove choice",
                            "Delete choice '" + option.getName() + "' from group '" + group.getName() + "'?",
                            () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.removeOption(currentMenuVersion.getId(), option.getId());
                            showSuccess("Choice removed");
                            refreshEditorData();
                            }));
                        removeOption.getElement().setAttribute("theme", "error tertiary-inline icon small");
                        optRow.add(editOption, duplicateOption, removeOption);
                    content.add(optRow);
                }
            }

                    Button addChoiceButton = new Button("Add choice", event -> openChoiceDialog(group, null));
                    addChoiceButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    addChoiceButton.getStyle().set("align-self", "flex-start");
                    content.add(addChoiceButton);

            AccordionPanel panel = choiceLibrary.add("", content);
            panel.setSummary(buildChoiceGroupSummary(group));
            panel.getElement().getStyle().set("cursor", "grab");
            DragSource<AccordionPanel> dragSource = DragSource.create(panel);
            dragSource.setDraggable(true);
            dragSource.addDragStartListener(event -> {
                draggedChoiceGroupId = group.getId();
                editorTree.setDropMode(GridDropMode.ON_TOP);
            });
            dragSource.addDragEndListener(event -> {
                draggedChoiceGroupId = null;
                editorTree.setDropMode(GridDropMode.BETWEEN);
            });
        }
    }

    private HorizontalLayout buildChoiceGroupSummary(RestaurantMenuOptionGroup group) {
        HorizontalLayout summary = new HorizontalLayout();
        summary.setWidthFull();
        summary.setPadding(false);
        summary.setSpacing(false);
        summary.setAlignItems(FlexComponent.Alignment.CENTER);
        summary.getStyle().set("gap", "6px");

        Span name = new Span(group.getName() == null ? "Choice Group" : group.getName());
        name.getStyle().set("flex", "1");

        Button duplicate = iconButton(VaadinIcon.COPY_O, "Duplicate choice group", event -> confirmAction(
                "Duplicate choice group",
                "Create a copy of choice group '" + group.getName() + "'?",
                () -> {
                    if (currentMenuVersion == null) {
                        return;
                    }
                    restaurantMenuEditorService.duplicateOptionGroup(currentMenuVersion.getId(), group.getId());
                    showSuccess("Choice group duplicated");
                    refreshEditorData();
                }));
        Button remove = iconButton(VaadinIcon.TRASH, "Remove choice group", event -> confirmAction(
                "Remove choice group",
                "Delete choice group '" + group.getName() + "' and all choices in it?",
                () -> {
                    if (currentMenuVersion == null) {
                        return;
                    }
                    restaurantMenuEditorService.removeOptionGroup(currentMenuVersion.getId(), group.getId());
                    showSuccess("Choice group removed");
                    refreshEditorData();
                }));
        remove.getElement().setAttribute("theme", "error tertiary-inline icon small");
        Button more = iconButton(VaadinIcon.ELLIPSIS_DOTS_V, "More actions", event -> openChoiceGroupMoreDialog(group));
        Button edit = iconButton(VaadinIcon.PENCIL, "Edit choice group", event -> openChoiceGroupEditDialog(group));

        preventAccordionToggle(duplicate);
        preventAccordionToggle(remove);
        preventAccordionToggle(more);
        preventAccordionToggle(edit);

        summary.add(name, edit, duplicate, remove, more);
        return summary;
    }

    private void preventAccordionToggle(Button button) {
        button.getElement().executeJs(
                "this.addEventListener('click', function(e){ e.stopPropagation(); });"
                        + "this.addEventListener('mousedown', function(e){ e.stopPropagation(); });"
                        + "this.addEventListener('keydown', function(e){ e.stopPropagation(); });");
    }

    private String buildChoiceLibraryKey(RestaurantMenuOptionGroup group) {
        if (group.getSourceGroupId() != null) {
            return "src:" + group.getSourceGroupId();
        }
        return "name:"
                + (group.getName() == null ? "" : group.getName().trim().toLowerCase())
                + "|min:" + group.getForceMin()
                + "|max:" + group.getForceMax()
                + "|req:" + group.getRequiredSelection();
    }

    private FlexLayout buildChoiceOptionBadges(RestaurantMenuOption option) {
        FlexLayout badges = new FlexLayout();
        badges.getStyle().set("display", "inline-flex");
        badges.getStyle().set("flex-wrap", "wrap");
        badges.getStyle().set("gap", "4px");

        if (option == null) {
            return badges;
        }

        if (Boolean.TRUE.equals(option.getDefaultOption())) {
            badges.add(createOptionBadge("PreSelected", "success"));
        }
        if (Boolean.TRUE.equals(option.getOutOfStock())) {
            badges.add(createOptionBadge("NoStock", "error"));
        }

        return badges;
    }

    private void configureActions() {
        refreshButton.addClickListener(event -> refreshEditorData());

        previewButton.addClickListener(event -> {
            if (restaurantId == null) {
                showError("No restaurant selected");
                return;
            }
            UI.getCurrent().navigate(RestaurantMenuOrderPreviewView.class,
                    new com.vaadin.flow.router.QueryParameters(Map.of(RESTAURANT_ID_QP, List.of(String.valueOf(restaurantId)))));
        });

        createDraftButton.addClickListener(event -> {
            if (restaurantId == null) {
                return;
            }
            try {
                restaurantMenuEditorService.createDraftFromLatestPulledVersion(restaurantId);
                showSuccess("Draft created from latest pulled menu.");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });

        publishDraftButton.addClickListener(event -> {
            if (restaurantId == null || currentMenuVersion == null || currentMenuVersion.getWorkflowStatus() != WorkflowStatus.DRAFT) {
                return;
            }
            try {
                restaurantMenuEditorService.publishDraftVersion(restaurantId, currentMenuVersion.getId());
                showSuccess("Draft published and pulls paused.");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void refreshEditorData() {
        TreeUiState treeUiState = captureTreeUiState();
        refreshItemSettingsOptionLists();

        if (restaurantId == null) {
            clearEditor("No restaurant selected.");
            return;
        }

        currentMenuVersion = restaurantMenuEditorService.getEditorVersion(restaurantId);
        if (currentMenuVersion == null) {
            categoryItemsMap.clear();
            categoryOptionGroupsMap.clear();
            itemOptionGroupsMap.clear();
            groupOptionsMap.clear();
            itemSizesMap.clear();
            expandedCategoryIds.clear();
            expandedItemIds.clear();
            editorTree.getDataProvider().refreshAll();
            refreshChoiceLibrary();
            refreshMenuHeaderPreview();
            updateBanner();
            updateActionStates();
            return;
        }

        // Load categories and items
        List<RestaurantMenuCategory> categories = restaurantMenuEditorService.listCategories(currentMenuVersion.getId());
        categoryItemsMap.clear();
        categoryOptionGroupsMap.clear();
        itemOptionGroupsMap.clear();
        itemSizesMap.clear();
        groupOptionsMap.clear();

        for (RestaurantMenuCategory category : categories) {
            List<RestaurantMenuOptionGroup> categoryGroups = restaurantMenuEditorService
                    .listOptionGroupsForCategory(currentMenuVersion.getId(), category.getId())
                    .stream()
                    .filter(group -> group.getItemId() == null && group.getItemSizeId() == null)
                    .toList();
            if (!categoryGroups.isEmpty()) {
                categoryOptionGroupsMap.put(category.getId(), categoryGroups);
                for (RestaurantMenuOptionGroup group : categoryGroups) {
                    groupOptionsMap.put(group.getId(),
                            restaurantMenuEditorService.listOptionsForGroup(group.getId()));
                }
            }

            List<RestaurantMenuItem> items = restaurantMenuEditorService.listItemsForCategory(
                    currentMenuVersion.getId(), category.getId());
            categoryItemsMap.put(category, items);
            // Load option groups for each item
            for (RestaurantMenuItem item : items) {
                List<RestaurantMenuItemSize> sizes = restaurantMenuEditorService
                        .listSizesForItem(currentMenuVersion.getId(), item.getId());
                if (!sizes.isEmpty()) {
                    itemSizesMap.put(item.getId(), sizes);
                }

                List<RestaurantMenuOptionGroup> groups = restaurantMenuEditorService
                        .listOptionGroupsForItem(currentMenuVersion.getId(), item.getId());
                if (!groups.isEmpty()) {
                    itemOptionGroupsMap.put(item.getId(), groups);
                    for (RestaurantMenuOptionGroup group : groups) {
                        groupOptionsMap.put(group.getId(),
                                restaurantMenuEditorService.listOptionsForGroup(group.getId()));
                    }
                }
            }
        }

        // Also pre-load options for any groups in the right pane (version-level groups not yet in groupOptionsMap)
        List<RestaurantMenuOptionGroup> allGroups = restaurantMenuEditorService.listOptionGroupsForVersion(currentMenuVersion.getId());
        for (RestaurantMenuOptionGroup group : allGroups) {
            groupOptionsMap.computeIfAbsent(group.getId(),
                    id -> restaurantMenuEditorService.listOptionsForGroup(id));
        }

        // Refresh the tree
        editorTree.getDataProvider().refreshAll();
        restoreTreeUiState(treeUiState);
        refreshChoiceLibrary();
        refreshMenuHeaderPreview();
        
        updateBanner();
        updateActionStates();
    }

    private void clearEditor(String message) {
        title.setText("Restaurant Menu Editor");
        statusBanner.setText(message);
        currentMenuVersion = null;
        categoryItemsMap.clear();
        categoryOptionGroupsMap.clear();
        itemOptionGroupsMap.clear();
        itemSizesMap.clear();
        groupOptionsMap.clear();
        expandedCategoryIds.clear();
        expandedItemIds.clear();
        editorTree.getDataProvider().refreshAll();
        refreshChoiceLibrary();
        refreshMenuHeaderPreview();
        updateActionStates();
    }

    private void updateBanner() {
        String restaurantText = restaurant == null
                ? "Restaurant not loaded"
                : restaurant.getName() + " (" + restaurant.getRestaurantId() + ")";

        if (restaurant != null) {
            title.setText("Restaurant Menu Editor - " + restaurant.getName());
        } else {
            title.setText("Restaurant Menu Editor");
        }

        if (currentMenuVersion == null) {
            statusBanner.setText(restaurantText + " | No menu data yet. Create a draft when ready.");
            return;
        }

        if (currentMenuVersion.getWorkflowStatus() == WorkflowStatus.PUBLISHED) {
            statusBanner.setText(restaurantText + " | Published menu is active. Automated pulls are paused.");
            return;
        }

        if (currentMenuVersion.getWorkflowStatus() == WorkflowStatus.DRAFT) {
            statusBanner.setText(restaurantText + " | Draft in progress. Publish when ready.");
            return;
        }

        statusBanner.setText(restaurantText + " | Editing from latest pulled menu.");
    }

    private void updateActionStates() {
        boolean hasVersion = currentMenuVersion != null;
        boolean isDraft = hasVersion && currentMenuVersion.getWorkflowStatus() == WorkflowStatus.DRAFT;
        boolean isPublished = hasVersion && currentMenuVersion.getWorkflowStatus() == WorkflowStatus.PUBLISHED;

        createDraftButton.setEnabled(!isDraft && !isPublished && restaurantId != null);
        publishDraftButton.setEnabled(isDraft);
        previewButton.setEnabled(currentMenuVersion != null && restaurantId != null);
        dataTablesMenu.setEnabled(restaurantId != null);
        refreshButton.setEnabled(restaurantId != null);
        menuHeaderSelectButton.setEnabled(currentMenuVersion != null);
        restaurantSelector.setEnabled(!selectableRestaurants.isEmpty());
    }

    private Div buildNameCell(MenuTreeNode node) {
        Div cell = new Div();
        cell.getStyle().set("display", "flex");
        cell.getStyle().set("align-items", "flex-start");
        cell.getStyle().set("gap", "8px");

        Long imageAssetId = getTreeNodeImageAssetId(node);
        if (imageAssetId != null || CATEGORY_TYPE.equals(node.type()) || ITEM_TYPE.equals(node.type())) {
            cell.add(buildTreeImageButton(node, imageAssetId));
        }

        Div textWrap = new Div();
        textWrap.getStyle().set("display", "flex");
        textWrap.getStyle().set("flex-direction", "column");
        textWrap.getStyle().set("line-height", "1.15");
        textWrap.getStyle().set("min-width", "0");

        Span nameSpan = new Span(node.displayName());
        textWrap.add(nameSpan);

        String description = null;
        if (CATEGORY_TYPE.equals(node.type())) {
            RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
            description = category.getDescription();
        } else if (ITEM_TYPE.equals(node.type())) {
            RestaurantMenuItem item = (RestaurantMenuItem) node.data();
            description = item.getDescription();
        }

        if (description != null && !description.trim().isEmpty()) {
            Span subtitle = new Span(description.trim());
            subtitle.getStyle().set("font-size", "var(--lumo-font-size-xs)");
            subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
            textWrap.add(subtitle);
        }

        cell.add(textWrap);

        return cell;
    }

    private Long getTreeNodeImageAssetId(MenuTreeNode node) {
        if (CATEGORY_TYPE.equals(node.type())) {
            return ((RestaurantMenuCategory) node.data()).getImageAssetId();
        }
        if (ITEM_TYPE.equals(node.type())) {
            return ((RestaurantMenuItem) node.data()).getImageAssetId();
        }
        return null;
    }

    private Button buildTreeImageButton(MenuTreeNode node, Long imageAssetId) {
        Button button = new Button();
        button.addClickListener(event -> openImagePickerForNode(node));
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set("padding", "0");
        button.getStyle().set("min-width", "0");
        button.getStyle().set("border-radius", "6px");
        button.getElement().setAttribute("aria-label", "Edit image");
        button.getElement().setAttribute("title", imageAssetId == null ? "Select image" : "Edit image");

        Div thumb = new Div();
        thumb.getStyle().set("width", "42px");
        thumb.getStyle().set("height", "24px");
        thumb.getStyle().set("border-radius", "6px");
        thumb.getStyle().set("overflow", "hidden");
        thumb.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        thumb.getStyle().set("background", "var(--lumo-contrast-5pct)");
        thumb.getStyle().set("flex-shrink", "0");

        String imageUrl = menuImageAssetService.getImageUrl(imageAssetId);
        if (imageUrl != null && !imageUrl.isBlank()) {
            Image thumbImage = new Image(imageUrl, node.displayName() + " image thumbnail");
            thumbImage.setWidth("42px");
            thumbImage.getStyle().set("height", "24px");
            thumbImage.getStyle().set("object-fit", "cover");
            thumb.add(thumbImage);
        } else {
            thumb.getStyle().set("background-image",
                    "linear-gradient(135deg, var(--lumo-contrast-10pct) 25%, transparent 25%, transparent 50%, var(--lumo-contrast-10pct) 50%, var(--lumo-contrast-10pct) 75%, transparent 75%, transparent)");
            thumb.getStyle().set("background-size", "8px 8px");
        }

        button.setIcon(thumb);
        return button;
    }

    private void openImagePickerForNode(MenuTreeNode node) {
        if (CATEGORY_TYPE.equals(node.type())) {
            RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
            MenuImagePickerDialog picker = new MenuImagePickerDialog(
                    menuImageAssetService,
                    MenuImageSlot.MENU_GROUP,
                    category.getImageAssetId(),
                    selectedId -> {
                        category.setImageAssetId(selectedId);
                        restaurantMenuEditorService.saveCategory(category);
                        refreshEditorData();
                        showSuccess("Group image updated");
                    });
            picker.open();
            return;
        }

        if (ITEM_TYPE.equals(node.type())) {
            RestaurantMenuItem item = (RestaurantMenuItem) node.data();
            MenuImagePickerDialog picker = new MenuImagePickerDialog(
                    menuImageAssetService,
                    MenuImageSlot.MENU_ITEM,
                    item.getImageAssetId(),
                    selectedId -> {
                        item.setImageAssetId(selectedId);
                        restaurantMenuEditorService.saveItem(item);
                        refreshEditorData();
                        showSuccess("Item image updated");
                    });
            picker.open();
        }
    }

    private FlexLayout buildOptionsBadges(MenuTreeNode node) {
        FlexLayout badges = new FlexLayout();
        badges.getStyle().set("display", "inline-flex");
        badges.getStyle().set("flex-wrap", "wrap");
        badges.getStyle().set("gap", "4px");

        if (CATEGORY_TYPE.equals(node.type())) {
            RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
            if (Boolean.FALSE.equals(category.getActive())) {
                badges.add(createOptionBadge("Hidden", "contrast"));
            }
            return badges;
        }

        if (ITEM_TYPE.equals(node.type())) {
            RestaurantMenuItem item = (RestaurantMenuItem) node.data();
            if (Boolean.FALSE.equals(item.getActive())) {
                badges.add(createOptionBadge("Hidden", "contrast"));
            }
            if (Boolean.TRUE.equals(item.getOutOfStock())) {
                badges.add(createOptionBadge("NoStock", "error"));
            }
            return badges;
        }

        if (ITEM_SIZE_TYPE.equals(node.type())) {
            RestaurantMenuItemSize size = (RestaurantMenuItemSize) node.data();
            if (Boolean.TRUE.equals(size.getDefaultSize())) {
                badges.add(createOptionBadge("PreSelected", "success"));
            }
        }

        return badges;
    }

    private Span createOptionBadge(String label, String variant) {
        Span badge = new Span(label);
        String theme = "badge small";
        if (variant != null && !variant.isBlank()) {
            theme = theme + " " + variant;
        }
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    private HorizontalLayout buildRowActions(MenuTreeNode node) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(false);
        actions.getStyle().set("gap", "4px");

        if (CATEGORY_TYPE.equals(node.type())) {
            RestaurantMenuCategory category = (RestaurantMenuCategory) node.data();
            Button addItem = iconButton(VaadinIcon.PLUS, "Add item in group", event ->
                addItemToCategory(category));
            Button edit = iconButton(VaadinIcon.PENCIL, "Edit category", event -> openEditDialog(node));
            Button duplicate = iconButton(VaadinIcon.COPY_O, "Duplicate category", event -> {
                confirmAction(
                        "Duplicate category",
                        "Create a copy of category '" + category.getName() + "'?",
                        () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.duplicateCategory(currentMenuVersion.getId(), category.getId());
                            showSuccess("Category duplicated");
                            refreshEditorData();
                        });
            });
            Button remove = iconButton(VaadinIcon.TRASH, "Remove category", event -> {
                confirmAction(
                        "Remove category",
                        "Delete category '" + category.getName() + "' and all items/choices under it?",
                        () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.removeCategory(currentMenuVersion.getId(), category.getId());
                            showSuccess("Category removed");
                            refreshEditorData();
                        });
            });
            remove.getElement().setAttribute("theme", "error tertiary-inline icon small");
            actions.add(addItem, edit, duplicate, remove);
            return actions;
        }

        if (ITEM_TYPE.equals(node.type())) {
            RestaurantMenuItem item = (RestaurantMenuItem) node.data();
            Button addSize = iconButton(VaadinIcon.PLUS, "Add size", event ->
                addSizeToItem(item));
            Button edit = iconButton(VaadinIcon.PENCIL, "Edit item", event -> openEditDialog(node));
            Button duplicate = iconButton(VaadinIcon.COPY_O, "Duplicate item", event -> {
                confirmAction(
                        "Duplicate item",
                        "Create a copy of item '" + item.getName() + "'?",
                        () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.duplicateItem(currentMenuVersion.getId(), item.getId());
                            showSuccess("Item duplicated");
                            refreshEditorData();
                        });
            });
            Button remove = iconButton(VaadinIcon.TRASH, "Remove item", event -> {
                confirmAction(
                        "Remove item",
                        "Delete item '" + item.getName() + "' and its choices?",
                        () -> {
                            if (currentMenuVersion == null) {
                                return;
                            }
                            restaurantMenuEditorService.removeItem(currentMenuVersion.getId(), item.getId());
                            showSuccess("Item removed");
                            refreshEditorData();
                        });
            });
            remove.getElement().setAttribute("theme", "error tertiary-inline icon small");
            actions.add(addSize, edit, duplicate, remove);
            return actions;
        }

        if (ITEM_SIZE_TYPE.equals(node.type())) {
            RestaurantMenuItemSize size = (RestaurantMenuItemSize) node.data();
            RestaurantMenuItem parentItem = findParentItemForSize(size.getId());
            if (parentItem == null) {
                return actions;
            }

            Button edit = iconButton(VaadinIcon.PENCIL, "Edit size",
                    event -> openItemSizeDialog(parentItem, size, this::refreshEditorData));
            Button remove = iconButton(VaadinIcon.TRASH, "Remove size", event ->
                    confirmAction(
                            "Remove size",
                            "Delete size '" + (size.getName() == null ? "" : size.getName()) + "'?",
                            () -> {
                                if (currentMenuVersion == null) {
                                    return;
                                }
                                restaurantMenuEditorService.removeItemSize(currentMenuVersion.getId(), parentItem.getId(), size.getId());
                                showSuccess("Size removed");
                                refreshEditorData();
                            }));
            remove.getElement().setAttribute("theme", "error tertiary-inline icon small");
            actions.add(edit, remove);
        }
        return actions;
    }

    private Button iconButton(VaadinIcon icon, String ariaLabel,
            com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(icon.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE,
                ButtonVariant.LUMO_ICON,
                ButtonVariant.LUMO_SMALL);
        button.getElement().setAttribute("aria-label", ariaLabel);
        button.getElement().setAttribute("title", ariaLabel);
        button.addClickListener(listener);
        return button;
    }

    private void confirmAction(String title, String message, Runnable onConfirm) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle(title);
        confirm.add(new Span(message));

        Button cancel = new Button("Cancel", event -> confirm.close());
        Button proceed = new Button("Confirm", event -> {
            try {
                onConfirm.run();
                confirm.close();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        proceed.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        confirm.getFooter().add(cancel, proceed);
        confirm.open();
    }

    private void openEditDialog(MenuTreeNode selected) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(CATEGORY_TYPE.equals(selected.type()) ? "Edit Category" : "Edit Item");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.LARGE);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        content.add(nameField);

        Checkbox activeField = new Checkbox("Visible/Active");
        content.add(activeField);

        final Checkbox outOfStockField;
        final ItemSettingsModel itemSettingsModel;

        final TextArea categoryDescriptionField;
        final TextArea itemDescriptionField;

        final NumberField priceField;
        final ComboBox<String> itemTaxationCategoryField;
        if (CATEGORY_TYPE.equals(selected.type())) {
            RestaurantMenuCategory category = (RestaurantMenuCategory) selected.data();
            nameField.setValue(category.getName() == null ? "" : category.getName());
            activeField.setValue(!Boolean.FALSE.equals(category.getActive()));

            TextArea description = new TextArea("Description");
            description.setWidthFull();
            description.setPlaceholder("Category description");
            description.setValue(category.getDescription() == null ? "" : category.getDescription());
            content.addComponentAtIndex(1, description);
            categoryDescriptionField = description;
            itemDescriptionField = null;

            priceField = null;
            itemTaxationCategoryField = null;
            outOfStockField = null;
            itemSettingsModel = null;
        } else {
            RestaurantMenuItem item = (RestaurantMenuItem) selected.data();
            nameField.setValue(item.getName() == null ? "" : item.getName());
            activeField.setValue(!Boolean.FALSE.equals(item.getActive()));

            itemSettingsModel = readItemSettings(item);

            Checkbox itemOutOfStockField = new Checkbox("Out of stock");
            itemOutOfStockField.setValue(itemSettingsModel.outOfStock);
            outOfStockField = itemOutOfStockField;
            content.add(itemOutOfStockField);

            Button itemSettingsButton = new Button("Item Settings...", event -> openItemSettingsDialog(itemSettingsModel));
            itemSettingsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            content.add(itemSettingsButton);

            boolean hasSizesForItem = !itemSizesMap.getOrDefault(item.getId(), List.of()).isEmpty();

            TextArea description = new TextArea("Description");
            description.setWidthFull();
            description.setPlaceholder("Item description");
            description.setValue(item.getDescription() == null ? "" : item.getDescription());
            content.addComponentAtIndex(1, description);
            itemDescriptionField = description;

            NumberField itemPriceField = new NumberField("Base Price");
            itemPriceField.setStep(0.01d);
            itemPriceField.setMin(0d);
            itemPriceField.setWidthFull();
            Double itemBasePrice = item.getBasePrice();
            Double normalizedItemBasePrice = itemBasePrice;
            if (normalizedItemBasePrice == null) {
                normalizedItemBasePrice = 0d;
            }
            itemPriceField.setValue(normalizedItemBasePrice);
            if (hasSizesForItem) {
                itemPriceField.setReadOnly(true);
                itemPriceField.setLabel("Base Price (auto from sizes)");
                itemPriceField.setHelperText("Automatically set to the lowest size price");
            }
            content.add(itemPriceField);
            priceField = itemPriceField;

            ComboBox<String> taxationCategoryField = new ComboBox<>("Taxation category");
            taxationCategoryField.setWidthFull();
            taxationCategoryField.setItems(taxationCategoryOptions);
            taxationCategoryField.setClearButtonVisible(false);
            taxationCategoryField.setHelperText("Used for standalone item tax when no addon/choice tax applies");
            String selectedTaxationCategory = trimToNull(restaurantMenuEditorService.getItemTaxationCategory(item.getId()));
            if (selectedTaxationCategory != null && !taxationCategoryOptions.contains(selectedTaxationCategory)) {
                taxationCategoryField.setItems(Stream.concat(taxationCategoryOptions.stream(), Stream.of(selectedTaxationCategory)).toList());
            }
            taxationCategoryField.setValue(selectedTaxationCategory == null ? "Food" : selectedTaxationCategory);
            content.add(taxationCategoryField);
            itemTaxationCategoryField = taxationCategoryField;

            categoryDescriptionField = null;
        }

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button("Save", e -> {
            String newName = nameField.getValue() == null ? "" : nameField.getValue().trim();
            if (newName.isEmpty()) {
                showError("Name is required");
                return;
            }

            try {
                if (CATEGORY_TYPE.equals(selected.type())) {
                    RestaurantMenuCategory category = (RestaurantMenuCategory) selected.data();
                    category.setName(newName);
                    category.setActive(activeField.getValue());
                    if (categoryDescriptionField != null) {
                        String newDescription = categoryDescriptionField.getValue();
                        category.setDescription(newDescription == null || newDescription.trim().isEmpty() ? null : newDescription.trim());
                    }
                    restaurantMenuEditorService.saveCategory(category);
                } else {
                    RestaurantMenuItem item = (RestaurantMenuItem) selected.data();
                    item.setName(newName);
                    item.setActive(activeField.getValue());
                    if (outOfStockField != null && itemSettingsModel != null) {
                        itemSettingsModel.outOfStock = outOfStockField.getValue();
                        applyItemSettings(item, itemSettingsModel);
                    }
                    if (itemDescriptionField != null) {
                        String newDescription = itemDescriptionField.getValue();
                        item.setDescription(newDescription == null || newDescription.trim().isEmpty() ? null : newDescription.trim());
                    }
                    boolean hasSizesForItem = !itemSizesMap.getOrDefault(item.getId(), List.of()).isEmpty();
                    if (priceField != null && !hasSizesForItem) {
                        Double editedBasePrice = priceField.getValue();
                        Double normalizedEditedBasePrice = editedBasePrice;
                        if (normalizedEditedBasePrice == null) {
                            normalizedEditedBasePrice = 0d;
                        }
                        item.setBasePrice(normalizedEditedBasePrice);
                    }
                    if (itemSettingsModel != null) {
                        restaurantMenuEditorService.saveItemWithSettings(
                                item,
                                itemSettingsModel.tags,
                                itemSettingsModel.allergens,
                                itemSettingsModel.nutritionalSize,
                                itemSettingsModel.nutritionalValues);
                    } else {
                        restaurantMenuEditorService.saveItem(item);
                    }
                    if (itemTaxationCategoryField != null) {
                        restaurantMenuEditorService.saveItemTaxationCategory(item.getId(), itemTaxationCategoryField.getValue());
                    }
                }
                dialog.close();
                showSuccess("Saved");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void addCategoryAtEnd() {
        if (currentMenuVersion == null) {
            return;
        }
        List<RestaurantMenuCategory> categories = new ArrayList<>(categoryItemsMap.keySet());
        int nextOrder = categories.size() + 1;

        RestaurantMenuCategory category = new RestaurantMenuCategory();
        category.setMenuVersionId(currentMenuVersion.getId());
        category.setSourceMenuId(currentMenuVersion.getSourceMenuId());
        category.setName("New Group");
        category.setActive(Boolean.TRUE);
        category.setDisplayOrder(nextOrder);
        restaurantMenuEditorService.saveCategory(category);
        refreshEditorData();
    }

    private void addItemToCategory(RestaurantMenuCategory category) {
        if (currentMenuVersion == null || category == null) {
            return;
        }
        List<RestaurantMenuItem> items = new ArrayList<>(categoryItemsMap.getOrDefault(category, List.of()));
        int nextOrder = items.size() + 1;

        RestaurantMenuItem newItem = new RestaurantMenuItem();
        newItem.setMenuVersionId(currentMenuVersion.getId());
        newItem.setCategoryId(category.getId());
        newItem.setSourceCategoryId(category.getSourceCategoryId());
        newItem.setName("New Item");
        newItem.setActive(Boolean.TRUE);
        newItem.setBasePrice(0d);
        newItem.setDisplayOrder(nextOrder);
        RestaurantMenuItem savedItem = restaurantMenuEditorService.saveItem(newItem);
        restaurantMenuEditorService.saveItemTaxationCategory(savedItem.getId(), "Food");
        refreshEditorData();
    }

    private void addSizeToItem(RestaurantMenuItem item) {
        if (item == null) {
            return;
        }
        openItemSizeDialog(item, null, this::refreshEditorData);
    }

    private void handleTreeDrop(GridDropEvent<MenuTreeNode> event) {
        if (draggedNode == null) {
            Optional<Long> draggedChoiceGroupIdOpt = extractChoiceGroupDragData();
            if (draggedChoiceGroupIdOpt.isPresent()) {
                handleChoiceGroupDropToItem(event, draggedChoiceGroupIdOpt.get());
            }
            return;
        }
        Optional<MenuTreeNode> dropTargetOpt = event.getDropTargetItem();
        if (dropTargetOpt.isEmpty()) {
            draggedNode = null;
            return;
        }
        MenuTreeNode dropTarget = dropTargetOpt.get();
        if (draggedNode.equals(dropTarget)) {
            draggedNode = null;
            return;
        }
        GridDropLocation location = event.getDropLocation();
        if (CATEGORY_TYPE.equals(draggedNode.type()) && CATEGORY_TYPE.equals(dropTarget.type())) {
            reorderCategories(
                    (RestaurantMenuCategory) draggedNode.data(),
                    (RestaurantMenuCategory) dropTarget.data(),
                    location);
        } else if (ITEM_TYPE.equals(draggedNode.type()) && ITEM_TYPE.equals(dropTarget.type())) {
            reorderItems(
                    (RestaurantMenuItem) draggedNode.data(),
                    (RestaurantMenuItem) dropTarget.data(),
                    location);
        } else if (ITEM_SIZE_TYPE.equals(draggedNode.type()) && ITEM_SIZE_TYPE.equals(dropTarget.type())) {
            reorderSizes(
                    (RestaurantMenuItemSize) draggedNode.data(),
                    (RestaurantMenuItemSize) dropTarget.data(),
                    location);
        } else {
            showInfo("Items and categories cannot be mixed during reorder");
        }
        draggedNode = null;
    }

    private Optional<Long> extractChoiceGroupDragData() {
        return Optional.ofNullable(draggedChoiceGroupId);
    }

    private void handleChoiceGroupDropToItem(GridDropEvent<MenuTreeNode> event, Long choiceGroupId) {
        if (currentMenuVersion == null) {
            return;
        }
        if (event.getDropLocation() != GridDropLocation.ON_TOP) {
            showInfo("Drop directly on an item row");
            return;
        }
        Optional<MenuTreeNode> dropTargetOpt = event.getDropTargetItem();
        if (dropTargetOpt.isEmpty()) {
            showInfo("Drop a choice group on an item or group row");
            return;
        }
        MenuTreeNode dropTarget = dropTargetOpt.get();
        if (!ITEM_TYPE.equals(dropTarget.type()) && !CATEGORY_TYPE.equals(dropTarget.type())) {
            showInfo("Choice groups can only be dropped on item or group rows");
            return;
        }

        try {
            boolean added;
            String successMessage;
            if (ITEM_TYPE.equals(dropTarget.type())) {
                RestaurantMenuItem item = (RestaurantMenuItem) dropTarget.data();
                added = restaurantMenuEditorService.attachOptionGroupToItem(
                        currentMenuVersion.getId(),
                        item.getId(),
                        choiceGroupId);
                successMessage = "Choice linked to item";
            } else {
                RestaurantMenuCategory category = (RestaurantMenuCategory) dropTarget.data();
                added = restaurantMenuEditorService.attachOptionGroupToCategory(
                        currentMenuVersion.getId(),
                        category.getId(),
                        choiceGroupId);
                successMessage = "Choice linked to group";
            }
            if (!added) {
                showInfo("This choice group is already linked here");
                return;
            }
            showSuccess(successMessage);
            refreshEditorData();
        } catch (IllegalStateException ex) {
            showError(ex.getMessage());
        } finally {
            draggedChoiceGroupId = null;
            editorTree.setDropMode(GridDropMode.BETWEEN);
        }
    }

    private TreeUiState captureTreeUiState() {
        Set<Long> expandedCategoryIdsSnapshot = new HashSet<>(expandedCategoryIds);

        MenuTreeNode selectedNode = editorTree.asSingleSelect().getValue();
        Long selectedCategoryId = null;
        Long selectedItemId = null;
        if (selectedNode != null) {
            if (CATEGORY_TYPE.equals(selectedNode.type())) {
                selectedCategoryId = ((RestaurantMenuCategory) selectedNode.data()).getId();
            } else if (ITEM_TYPE.equals(selectedNode.type())) {
                selectedItemId = ((RestaurantMenuItem) selectedNode.data()).getId();
            }
        }
        Long selectedSizeId = null;
        if (selectedNode != null && ITEM_SIZE_TYPE.equals(selectedNode.type())) {
            selectedSizeId = ((RestaurantMenuItemSize) selectedNode.data()).getId();
            RestaurantMenuItem parentItem = findParentItemForSize(selectedSizeId);
            if (parentItem != null) {
                selectedItemId = parentItem.getId();
            }
        }
        return new TreeUiState(
                expandedCategoryIdsSnapshot,
                new HashSet<>(expandedItemIds),
                selectedCategoryId,
                selectedItemId,
                selectedSizeId);
    }

    private void openChoiceGroupMoreDialog(RestaurantMenuOptionGroup group) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(group.getName());

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        Button duplicate = new Button("Duplicate", VaadinIcon.COPY_O.create(), event -> {
            if (currentMenuVersion == null) {
                return;
            }
            try {
                restaurantMenuEditorService.duplicateOptionGroup(currentMenuVersion.getId(), group.getId());
                dialog.close();
                showSuccess("Choice group duplicated");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        duplicate.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);

        Button remove = new Button("Remove", VaadinIcon.TRASH.create(), event -> {
            dialog.close();
            confirmAction(
                    "Remove choice group",
                    "Delete choice group '" + group.getName() + "' and all choices in it?",
                    () -> {
                        if (currentMenuVersion == null) {
                            return;
                        }
                        restaurantMenuEditorService.removeOptionGroup(currentMenuVersion.getId(), group.getId());
                        showSuccess("Choice group removed");
                        refreshEditorData();
                    });
        });
        remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY_INLINE);

        ComboBox<String> majorGroup = new ComboBox<>("Major group");
        majorGroup.setWidthFull();
        majorGroup.setItems(majorGroupOptions);
        majorGroup.setClearButtonVisible(true);
        String selectedMajorGroup = trimToNull(restaurantMenuEditorService.getOptionGroupMajorGroup(group.getId()));
        if (selectedMajorGroup != null && !majorGroupOptions.contains(selectedMajorGroup)) {
            majorGroup.setItems(Stream.concat(majorGroupOptions.stream(), Stream.of(selectedMajorGroup)).toList());
        }
        if (selectedMajorGroup != null) {
            majorGroup.setValue(selectedMajorGroup);
        }

        ComboBox<String> taxationCategory = new ComboBox<>("Taxation Categories");
        taxationCategory.setWidthFull();
        taxationCategory.setItems(taxationCategoryOptions);
        taxationCategory.setClearButtonVisible(true);
        taxationCategory.setHelperText("You can add other tax rates under Data Tables / Taxation Categories List");
        String selectedTaxationCategory = trimToNull(restaurantMenuEditorService.getOptionGroupTaxationCategory(group.getId()));
        if (selectedTaxationCategory != null && !taxationCategoryOptions.contains(selectedTaxationCategory)) {
            taxationCategory.setItems(Stream.concat(taxationCategoryOptions.stream(), Stream.of(selectedTaxationCategory)).toList());
        }
        if (selectedTaxationCategory != null) {
            taxationCategory.setValue(selectedTaxationCategory);
        }

        content.add(duplicate, remove, majorGroup, taxationCategory);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            try {
                restaurantMenuEditorService.saveOptionGroupMajorGroup(group.getId(), majorGroup.getValue());
                restaurantMenuEditorService.saveOptionGroupTaxationCategory(group.getId(), taxationCategory.getValue());
                dialog.close();
                showSuccess("Choice group details updated");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openChoiceGroupEditDialog(RestaurantMenuOptionGroup group) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Choice Group");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setValue(group.getName() == null ? "" : group.getName());
        nameField.setHelperText("Eg. Extra toppings, Choose type");

        Checkbox mandatoryField = new Checkbox("Mandatory");
        mandatoryField.setValue(Boolean.TRUE.equals(group.getRequiredSelection()));

        NumberField forceMinField = new NumberField("Force minimum");
        forceMinField.setStep(1);
        forceMinField.setMin(0);
        forceMinField.setWidthFull();
        forceMinField.setValue(group.getForceMin() == null ? 0d : group.getForceMin().doubleValue());

        NumberField forceMaxField = new NumberField("Force maximum");
        forceMaxField.setStep(1);
        forceMaxField.setMin(0);
        forceMaxField.setWidthFull();
        forceMaxField.setValue(group.getForceMax() == null ? 0d : group.getForceMax().doubleValue());

        Checkbox allowMultipleSameChoice = new Checkbox("Allow adding same choice multiple times");
        allowMultipleSameChoice.setValue(Boolean.TRUE.equals(group.getAllowQuantity()));

        ComboBox<String> majorGroupField = new ComboBox<>("Major group");
        majorGroupField.setWidthFull();
        majorGroupField.setItems(majorGroupOptions);
        majorGroupField.setClearButtonVisible(true);
        String selectedMajorGroup = trimToNull(restaurantMenuEditorService.getOptionGroupMajorGroup(group.getId()));
        if (selectedMajorGroup != null && !majorGroupOptions.contains(selectedMajorGroup)) {
            majorGroupField.setItems(Stream.concat(majorGroupOptions.stream(), Stream.of(selectedMajorGroup)).toList());
        }
        if (selectedMajorGroup != null) {
            majorGroupField.setValue(selectedMajorGroup);
        }

        ComboBox<String> taxationCategoryField = new ComboBox<>("Taxation category");
        taxationCategoryField.setWidthFull();
        taxationCategoryField.setItems(taxationCategoryOptions);
        taxationCategoryField.setClearButtonVisible(true);
        taxationCategoryField.setHelperText("You can add other tax rates under Data Tables / Taxation Categories List");
        String selectedTaxationCategory = trimToNull(restaurantMenuEditorService.getOptionGroupTaxationCategory(group.getId()));
        if (selectedTaxationCategory != null && !taxationCategoryOptions.contains(selectedTaxationCategory)) {
            taxationCategoryField.setItems(Stream.concat(taxationCategoryOptions.stream(), Stream.of(selectedTaxationCategory)).toList());
        }
        if (selectedTaxationCategory != null) {
            taxationCategoryField.setValue(selectedTaxationCategory);
        }

        Runnable updateMandatoryFields = () -> {
            boolean mandatory = mandatoryField.getValue();
            forceMinField.setVisible(mandatory);
            forceMaxField.setVisible(mandatory);
        };

        Runnable updateAllowQuantityField = () -> {
            boolean mandatory = mandatoryField.getValue();
            int forceMax = toIntegerOrZero(forceMaxField.getValue());
            boolean showAllowQuantity = !mandatory || forceMax > 1;
            allowMultipleSameChoice.setVisible(showAllowQuantity);
            if (!showAllowQuantity) {
                allowMultipleSameChoice.setValue(false);
            }
        };
        mandatoryField.addValueChangeListener(event -> updateMandatoryFields.run());
        mandatoryField.addValueChangeListener(event -> updateAllowQuantityField.run());
        forceMaxField.addValueChangeListener(event -> updateAllowQuantityField.run());
        updateMandatoryFields.run();
        updateAllowQuantityField.run();

        content.add(
            nameField,
            mandatoryField,
            forceMinField,
            forceMaxField,
            allowMultipleSameChoice,
            majorGroupField,
            taxationCategoryField);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            String newName = trimToNull(nameField.getValue());
            if (newName == null) {
                showError("Name is required");
                return;
            }

            boolean mandatory = mandatoryField.getValue();
            int forceMin = mandatory ? toIntegerOrZero(forceMinField.getValue()) : 0;
            int forceMax = mandatory ? toIntegerOrZero(forceMaxField.getValue()) : 0;
            if (mandatory && forceMax < forceMin) {
                showError("Force maximum must be greater than or equal to force minimum");
                return;
            }

            try {
                group.setName(newName);
                group.setRequiredSelection(mandatory);
                group.setForceMin(forceMin);
                group.setForceMax(forceMax);
                boolean allowQuantity = allowMultipleSameChoice.isVisible() && allowMultipleSameChoice.getValue();
                group.setAllowQuantity(allowQuantity);
                restaurantMenuEditorService.saveOptionGroup(group);
                restaurantMenuEditorService.saveOptionGroupMajorGroup(group.getId(), majorGroupField.getValue());
                restaurantMenuEditorService.saveOptionGroupTaxationCategory(group.getId(), taxationCategoryField.getValue());

                dialog.close();
                showSuccess("Choice group updated");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void openChoiceDialog(RestaurantMenuOptionGroup group, RestaurantMenuOption existingOption) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existingOption == null ? "Add choice" : "Edit choice");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        TextField nameField = new TextField("Choice name");
        nameField.setWidthFull();
        nameField.setValue(existingOption == null || existingOption.getName() == null ? "" : existingOption.getName());

        NumberField priceField = new NumberField("Price");
        priceField.setStep(0.01d);
        priceField.setMin(0d);
        priceField.setWidthFull();
        Double existingPrice = existingOption == null ? null : existingOption.getPrice();
        priceField.setValue(existingPrice != null ? existingPrice : 0d);

        Checkbox outOfStockField = new Checkbox("Out of stock");
        outOfStockField.setValue(existingOption != null && Boolean.TRUE.equals(existingOption.getOutOfStock()));

        Checkbox preSelectedField = new Checkbox("Pre-selected option");
        preSelectedField.setValue(existingOption != null && Boolean.TRUE.equals(existingOption.getDefaultOption()));

        content.add(nameField, priceField, outOfStockField, preSelectedField);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            String name = trimToNull(nameField.getValue());
            if (name == null) {
                showError("Choice name is required");
                return;
            }
            try {
                RestaurantMenuOption option = existingOption == null ? new RestaurantMenuOption() : existingOption;
                option.setMenuVersionId(group.getMenuVersionId());
                option.setOptionGroupId(group.getId());
                option.setName(name);
                Double enteredPrice = priceField.getValue();
                option.setPrice(enteredPrice != null ? enteredPrice : 0d);
                option.setOutOfStock(outOfStockField.getValue());
                option.setDefaultOption(preSelectedField.getValue());
                if (existingOption == null) {
                    List<RestaurantMenuOption> existingOptions = groupOptionsMap.get(group.getId());
                    int nextOrder = existingOptions == null ? 1 : existingOptions.stream()
                            .map(RestaurantMenuOption::getDisplayOrder)
                            .filter(order -> order != null)
                            .max(Integer::compareTo)
                            .orElse(0) + 1;
                    option.setDisplayOrder(nextOrder);
                }

                restaurantMenuEditorService.saveOption(option);
                dialog.close();
                showSuccess(existingOption == null ? "Choice added" : "Choice updated");
                refreshEditorData();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void restoreTreeUiState(TreeUiState state) {
        if (state == null) {
            return;
        }

        for (RestaurantMenuCategory category : categoryItemsMap.keySet()) {
            if (state.expandedCategoryIds().contains(category.getId())) {
                editorTree.expand(new MenuTreeNode(CATEGORY_TYPE, category, category.getName()));
            }
        }

        for (Map.Entry<RestaurantMenuCategory, List<RestaurantMenuItem>> entry : categoryItemsMap.entrySet()) {
            for (RestaurantMenuItem item : entry.getValue()) {
                if (state.expandedItemIds().contains(item.getId())) {
                    editorTree.expand(new MenuTreeNode(ITEM_TYPE, item, item.getName()));
                }
            }
        }

        if (state.selectedSizeId() != null) {
            for (List<RestaurantMenuItemSize> sizes : itemSizesMap.values()) {
                for (RestaurantMenuItemSize size : sizes) {
                    if (state.selectedSizeId().equals(size.getId())) {
                        editorTree.asSingleSelect().setValue(new MenuTreeNode(ITEM_SIZE_TYPE, size, size.getName()));
                        return;
                    }
                }
            }
        }

        if (state.selectedItemId() != null) {
            for (Map.Entry<RestaurantMenuCategory, List<RestaurantMenuItem>> entry : categoryItemsMap.entrySet()) {
                for (RestaurantMenuItem item : entry.getValue()) {
                    if (state.selectedItemId().equals(item.getId())) {
                        editorTree.asSingleSelect().setValue(new MenuTreeNode(ITEM_TYPE, item, item.getName()));
                        return;
                    }
                }
            }
        }

        if (state.selectedCategoryId() != null) {
            for (RestaurantMenuCategory category : categoryItemsMap.keySet()) {
                if (state.selectedCategoryId().equals(category.getId())) {
                    editorTree.asSingleSelect().setValue(new MenuTreeNode(CATEGORY_TYPE, category, category.getName()));
                    return;
                }
            }
        }
    }

    private void reorderCategories(RestaurantMenuCategory dragged, RestaurantMenuCategory target,
            GridDropLocation location) {
        List<RestaurantMenuCategory> categories = new ArrayList<>(categoryItemsMap.keySet());
        categories.remove(dragged);
        int idx = categories.indexOf(target);
        if (location == GridDropLocation.BELOW) idx++;
        categories.add(Math.max(0, Math.min(idx, categories.size())), dragged);

        for (int i = 0; i < categories.size(); i++) {
            RestaurantMenuCategory cat = categories.get(i);
            cat.setDisplayOrder(i + 1);
            restaurantMenuEditorService.saveCategory(cat);
        }

        LinkedHashMap<RestaurantMenuCategory, List<RestaurantMenuItem>> newMap = new LinkedHashMap<>();
        for (RestaurantMenuCategory cat : categories) {
            newMap.put(cat, categoryItemsMap.get(cat));
        }
        categoryItemsMap.clear();
        categoryItemsMap.putAll(newMap);
        editorTree.getDataProvider().refreshAll();
    }

    private void reorderItems(RestaurantMenuItem dragged, RestaurantMenuItem target,
            GridDropLocation location) {
        if (!dragged.getCategoryId().equals(target.getCategoryId())) {
            showInfo("Items can only be reordered within the same category");
            return;
        }
        RestaurantMenuCategory parentCat = categoryItemsMap.keySet().stream()
                .filter(cat -> cat.getId().equals(dragged.getCategoryId()))
                .findFirst().orElse(null);
        if (parentCat == null) return;

        List<RestaurantMenuItem> items = new ArrayList<>(categoryItemsMap.get(parentCat));
        items.remove(dragged);
        int idx = items.indexOf(target);
        if (location == GridDropLocation.BELOW) idx++;
        items.add(Math.max(0, Math.min(idx, items.size())), dragged);

        for (int i = 0; i < items.size(); i++) {
            RestaurantMenuItem item = items.get(i);
            item.setDisplayOrder(i + 1);
            restaurantMenuEditorService.saveItem(item);
        }

        categoryItemsMap.put(parentCat, items);
        editorTree.getDataProvider().refreshAll();
    }

    private void reorderSizes(RestaurantMenuItemSize dragged, RestaurantMenuItemSize target,
            GridDropLocation location) {
        if (!dragged.getItemId().equals(target.getItemId())) {
            showInfo("Sizes can only be reordered within the same item");
            return;
        }

        List<RestaurantMenuItemSize> sizes = new ArrayList<>(itemSizesMap.getOrDefault(dragged.getItemId(), List.of()));
        if (sizes.isEmpty()) {
            return;
        }

        sizes.remove(dragged);
        int idx = sizes.indexOf(target);
        if (location == GridDropLocation.BELOW) {
            idx++;
        }
        sizes.add(Math.max(0, Math.min(idx, sizes.size())), dragged);

        for (int i = 0; i < sizes.size(); i++) {
            sizes.get(i).setDisplayOrder(i + 1);
        }

        if (currentMenuVersion != null) {
            restaurantMenuEditorService.saveItemSizeOrder(
                    currentMenuVersion.getId(),
                    dragged.getItemId(),
                    sizes.stream().map(RestaurantMenuItemSize::getId).toList());
        }
        itemSizesMap.put(dragged.getItemId(), sizes);
        editorTree.getDataProvider().refreshAll();
    }

    private ItemSettingsModel readItemSettings(RestaurantMenuItem item) {
        ItemSettingsModel settings = new ItemSettingsModel();
        RestaurantMenuEditorService.ItemSettingsSnapshot snapshot = restaurantMenuEditorService.loadItemSettings(item);
        settings.outOfStock = snapshot.outOfStock();
        settings.tags.addAll(snapshot.tags());
        settings.allergens.addAll(snapshot.allergens());
        settings.ingredients = snapshot.ingredients();
        settings.additives = snapshot.additives();
        settings.nutritionalSize = snapshot.nutritionalSize() == null
                ? NUTRITION_PER_100G
                : snapshot.nutritionalSize();
        settings.nutritionalValues.putAll(snapshot.nutritionalValues());

        return settings;
    }

    private void applyItemSettings(RestaurantMenuItem item, ItemSettingsModel settings) {
        item.setOutOfStock(settings.outOfStock);
        item.setIngredients(trimToNull(settings.ingredients));
        item.setAdditives(trimToNull(settings.additives));
        item.setNutritionalValuesSize(
                NUTRITION_PER_SERVING.equals(settings.nutritionalSize) ? NUTRITION_PER_SERVING : NUTRITION_PER_100G);
        item.setTagsJson(null);
        item.setExtrasJson(null);
    }

    private void openItemSettingsDialog(ItemSettingsModel settings) {
        Dialog settingsDialog = new Dialog();
        settingsDialog.setHeaderTitle("Item Settings");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(settingsDialog, content, UIUtilities.DialogWidthPreset.LARGE);

        // Mark items as
        VerticalLayout tagsLayout = new VerticalLayout();
        tagsLayout.setPadding(false);
        tagsLayout.setSpacing(false);
        tagsLayout.getStyle().set("gap", "6px");
        Map<String, Checkbox> tagCheckboxes = new LinkedHashMap<>();
        for (String tag : itemTagOptions) {
            Checkbox checkbox = new Checkbox(formatTagLabel(tag));
            checkbox.setValue(settings.tags.contains(tag));
            tagCheckboxes.put(tag, checkbox);
            tagsLayout.add(checkbox);
        }
        TextField customTagsField = new TextField("Custom tags (comma separated)");
        customTagsField.setWidthFull();
        customTagsField.setValue(buildCustomList(settings.tags, itemTagOptions));
        tagsLayout.add(customTagsField);

        // Ingredients
        TextArea ingredientsField = new TextArea("Ingredients");
        ingredientsField.setWidthFull();
        ingredientsField.setValue(settings.ingredients == null ? "" : settings.ingredients);

        // Allergens
        VerticalLayout allergensLayout = new VerticalLayout();
        allergensLayout.setPadding(false);
        allergensLayout.setSpacing(false);
        allergensLayout.getStyle().set("gap", "6px");
        Map<String, Checkbox> allergenCheckboxes = new LinkedHashMap<>();
        for (String allergen : itemAllergenOptions) {
            Checkbox checkbox = new Checkbox(allergen);
            checkbox.setValue(settings.allergens.contains(allergen));
            allergenCheckboxes.put(allergen, checkbox);
            allergensLayout.add(checkbox);
        }
        TextField customAllergensField = new TextField("Custom allergens (comma separated)");
        customAllergensField.setWidthFull();
        customAllergensField.setValue(buildCustomList(settings.allergens, itemAllergenOptions));
        allergensLayout.add(customAllergensField);

        // Additives
        TextArea additivesField = new TextArea("Additives");
        additivesField.setWidthFull();
        additivesField.setValue(settings.additives == null ? "" : settings.additives);

        // Nutritional values
        VerticalLayout nutritionLayout = new VerticalLayout();
        nutritionLayout.setPadding(false);
        nutritionLayout.setSpacing(false);
        nutritionLayout.getStyle().set("gap", "8px");
        Checkbox perServing = new Checkbox("Per serving");
        Checkbox per100g = new Checkbox("Per 100g");
        perServing.setValue(NUTRITION_PER_SERVING.equals(settings.nutritionalSize));
        per100g.setValue(!perServing.getValue());
        perServing.addValueChangeListener(event -> {
            if (event.getValue()) {
                per100g.setValue(false);
            } else if (!per100g.getValue()) {
                per100g.setValue(true);
            }
        });
        per100g.addValueChangeListener(event -> {
            if (event.getValue()) {
                perServing.setValue(false);
            } else if (!perServing.getValue()) {
                perServing.setValue(true);
            }
        });
        HorizontalLayout nutritionalSizeRow = new HorizontalLayout(perServing, per100g);
        nutritionalSizeRow.setSpacing(true);
        nutritionLayout.add(nutritionalSizeRow);

        Map<String, TextField> nutritionalFields = new LinkedHashMap<>();
        for (String fieldName : ITEM_NUTRITION_FIELDS) {
            TextField field = new TextField(fieldName);
            field.setWidthFull();
            field.setValue(settings.nutritionalValues.getOrDefault(fieldName, ""));
            nutritionalFields.put(fieldName, field);
            nutritionLayout.add(field);
        }

        Accordion accordion = new Accordion();
        accordion.setWidthFull();
        accordion.add("Mark items as", tagsLayout);
        accordion.add("Ingredients", ingredientsField);
        accordion.add("Allergens", allergensLayout);
        accordion.add("Additives", additivesField);
        accordion.add("Nutritional values", nutritionLayout);
        content.add(accordion);

        Button cancel = new Button("Cancel", event -> settingsDialog.close());
        Button save = new Button("Save", event -> {
            settings.tags.clear();
            tagCheckboxes.forEach((tag, checkbox) -> {
                if (checkbox.getValue()) {
                    settings.tags.add(tag);
                }
            });
            settings.tags.addAll(parseCsv(customTagsField.getValue()));

            settings.ingredients = trimToNull(ingredientsField.getValue());

            settings.allergens.clear();
            allergenCheckboxes.forEach((allergen, checkbox) -> {
                if (checkbox.getValue()) {
                    settings.allergens.add(allergen);
                }
            });
            settings.allergens.addAll(parseCsv(customAllergensField.getValue()));

            settings.additives = trimToNull(additivesField.getValue());
            settings.nutritionalSize = perServing.getValue() ? NUTRITION_PER_SERVING : NUTRITION_PER_100G;

            settings.nutritionalValues.clear();
            nutritionalFields.forEach((name, field) -> {
                String value = trimToNull(field.getValue());
                if (value != null) {
                    settings.nutritionalValues.put(name, value);
                }
            });

            settingsDialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        settingsDialog.add(content);
        settingsDialog.getFooter().add(cancel, save);
        settingsDialog.open();
    }

    private void openItemSizeDialog(RestaurantMenuItem item, RestaurantMenuItemSize existingSize, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existingSize == null ? "Add size" : "Edit size");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        UIUtilities.applyDialogWidth(dialog, content, UIUtilities.DialogWidthPreset.COMPACT);

        TextField nameField = new TextField("Size name");
        nameField.setWidthFull();
        nameField.setValue(existingSize == null || existingSize.getName() == null ? "" : existingSize.getName());

        NumberField priceField = new NumberField("Price");
        priceField.setStep(0.01d);
        priceField.setMin(0d);
        priceField.setWidthFull();
        priceField.setHelperText("Enter the full selling price for this size");
        Double existingSizePrice = existingSize == null ? null : getDisplayPriceForSize(existingSize, item);
        Double normalizedExistingSizePrice = existingSizePrice;
        if (normalizedExistingSizePrice == null) {
            normalizedExistingSizePrice = 0d;
        }
        priceField.setValue(normalizedExistingSizePrice);

        Checkbox defaultSizeField = new Checkbox("Pre-selected option");
        defaultSizeField.setValue(existingSize != null && Boolean.TRUE.equals(existingSize.getDefaultSize()));

        content.add(nameField, priceField, defaultSizeField);

        Button cancel = new Button("Cancel", event -> dialog.close());
        Button save = new Button("Save", event -> {
            if (currentMenuVersion == null) {
                showError("No active menu version");
                return;
            }

            String name = trimToNull(nameField.getValue());
            if (name == null) {
                showError("Size name is required");
                return;
            }

            try {
                RestaurantMenuItemSize itemSize = existingSize == null ? new RestaurantMenuItemSize() : existingSize;
                itemSize.setMenuVersionId(currentMenuVersion.getId());
                itemSize.setItemId(item.getId());
                itemSize.setName(name);
                Double editedSizePrice = priceField.getValue();
                Double normalizedEditedSizePrice = editedSizePrice;
                if (normalizedEditedSizePrice == null) {
                    normalizedEditedSizePrice = 0d;
                }
                itemSize.setPrice(normalizedEditedSizePrice);
                itemSize.setDefaultSize(defaultSizeField.getValue());

                restaurantMenuEditorService.saveItemSize(itemSize);
                dialog.close();
                showSuccess(existingSize == null ? "Size added" : "Size updated");
                onSaved.run();
            } catch (IllegalStateException ex) {
                showError(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private String trimToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Set<String> parseCsv(String input) {
        Set<String> values = new LinkedHashSet<>();
        if (input == null || input.trim().isEmpty()) {
            return values;
        }
        String[] split = input.split(",");
        for (String part : split) {
            String value = trimToNull(part);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private String buildCustomList(Set<String> values, List<String> builtIns) {
        List<String> customValues = new ArrayList<>();
        for (String value : values) {
            if (!builtIns.contains(value)) {
                customValues.add(value);
            }
        }
        return String.join(", ", customValues);
    }

    private String formatTagLabel(String tag) {
        String lower = tag.toLowerCase().replace('_', ' ');
        if (lower.isEmpty()) {
            return tag;
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = lower.split(" ");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private Optional<Long> parseLong(String input) {
        try {
            return Optional.of(Long.valueOf(input));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private int toIntegerOrZero(Double value) {
        if (value == null) {
            return 0;
        }
        return (int) Math.round(value);
    }

    private RestaurantMenuItem findParentItemForSize(Long sizeId) {
        if (sizeId == null) {
            return null;
        }
        for (Map.Entry<RestaurantMenuCategory, List<RestaurantMenuItem>> entry : categoryItemsMap.entrySet()) {
            for (RestaurantMenuItem item : entry.getValue()) {
                List<RestaurantMenuItemSize> sizes = itemSizesMap.get(item.getId());
                if (sizes == null) {
                    continue;
                }
                for (RestaurantMenuItemSize size : sizes) {
                    if (sizeId.equals(size.getId())) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private double getDisplayPriceForSize(RestaurantMenuItemSize size) {
        if (size == null) {
            return 0d;
        }
        RestaurantMenuItem parentItem = findParentItemForSize(size.getId());
        return getDisplayPriceForSize(size, parentItem);
    }

    private double getDisplayPriceForSize(RestaurantMenuItemSize size, RestaurantMenuItem parentItem) {
        if (size == null) {
            return 0d;
        }
        double basePrice = 0d;
        if (parentItem != null && parentItem.getBasePrice() != null) {
            basePrice = parentItem.getBasePrice();
        }
        Double sizePrice = size.getPrice();
        if (sizePrice != null) {
            return roundCurrency(basePrice + sizePrice);
        }
        return roundCurrency(basePrice);
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showInfo(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }
}
