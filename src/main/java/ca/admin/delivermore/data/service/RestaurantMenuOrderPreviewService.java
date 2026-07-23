package ca.admin.delivermore.data.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuCategory;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItem;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemSize;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionGroup;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuVersion;

@Service
public class RestaurantMenuOrderPreviewService {

    private static final List<String> DEFAULT_TAXATION_CATEGORY_OPTIONS = List.of("Food", "Alcohol");

    public record TaxRateConfig(
        String taxName,
        double percentage) {
    }

    public record PreviewData(
            Restaurant restaurant,
            RestaurantMenuVersion menuVersion,
            String restaurantLogoImageUrl,
            String menuHeaderImageUrl,
            List<CategoryData> categories,
            Map<String, TaxRateConfig> taxationRates,
            TaxRateConfig serviceFeeTax,
            TaxRateConfig deliveryFeeTax,
            String deliveryFeeInfoText,
            String feesTaxesInfoText,
            double serviceFeeRate,
            int checkoutTimeoutMinutes) {
    }

    public record CategoryData(
            Long id,
            String name,
            String description,
            String imageUrl,
            List<ItemData> items) {
    }

    public record ItemData(
            Long id,
            String name,
            String description,
            String imageUrl,
            double basePrice,
            String taxationCategory,
            boolean outOfStock,
            Set<String> tags,
            List<SizeData> sizes,
            List<OptionGroupData> optionGroups) {
    }

    public record SizeData(
            Long id,
            String name,
            double absolutePrice,
            boolean defaultSize,
            List<OptionGroupData> optionGroups) {
    }

    public record OptionGroupData(
            Long id,
            String name,
            boolean required,
            boolean allowQuantity,
            int forceMin,
            int forceMax,
            String majorGroup,
            String taxationCategory,
            List<OptionData> options) {
    }

    public record OptionData(
            Long id,
            String name,
            double price,
            boolean defaultOption,
            boolean outOfStock) {
    }

    private final RestaurantMenuEditorService restaurantMenuEditorService;
    private final MenuImageAssetService menuImageAssetService;

    public RestaurantMenuOrderPreviewService(
            RestaurantMenuEditorService restaurantMenuEditorService,
            MenuImageAssetService menuImageAssetService) {
        this.restaurantMenuEditorService = restaurantMenuEditorService;
        this.menuImageAssetService = menuImageAssetService;
    }

    public PreviewData loadPreviewData(Long restaurantId) {
        Restaurant restaurant = restaurantMenuEditorService.getRestaurant(restaurantId);
        if (restaurant == null) {
            throw new IllegalStateException("Restaurant not found for id " + restaurantId);
        }

        RestaurantMenuVersion menuVersion = restaurantMenuEditorService.getEditorVersion(restaurantId);
        if (menuVersion == null) {
            throw new IllegalStateException("No menu version available for restaurant id " + restaurantId);
        }

        List<CategoryData> categories = new ArrayList<>();
        for (RestaurantMenuCategory category : restaurantMenuEditorService.listCategories(menuVersion.getId())) {
            List<ItemData> items = restaurantMenuEditorService.listItemsForCategory(menuVersion.getId(), category.getId())
                    .stream()
                    .map(item -> toItemData(menuVersion, category, item))
                    .toList();
                categories.add(new CategoryData(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    menuImageAssetService.getImageUrl(category.getImageAssetId()),
                    items));
        }

        Map<String, TaxRateConfig> taxationRates = new LinkedHashMap<>();
        for (RestaurantMenuEditorService.TaxationCategoryRate rate : restaurantMenuEditorService
                .getTaxationCategoryRates(DEFAULT_TAXATION_CATEGORY_OPTIONS)) {
            if (rate != null && rate.category() != null) {
                Double percentage = rate.percentage();
            String taxName = rate.taxName() == null ? "" : rate.taxName();
            taxationRates.put(rate.category(), new TaxRateConfig(taxName, percentage == null ? 0d : percentage));
            }
        }

        RestaurantMenuEditorService.NamedTaxRate serviceFeeTax = restaurantMenuEditorService.getServiceFeeTaxRate();
        RestaurantMenuEditorService.NamedTaxRate deliveryFeeTax = restaurantMenuEditorService.getDeliveryFeeTaxRate();
        String serviceFeeTaxName = "";
        double serviceFeeTaxPct = 0d;
        if (serviceFeeTax != null) {
            if (serviceFeeTax.name() != null) {
                serviceFeeTaxName = serviceFeeTax.name();
            }
            Double rawPct = serviceFeeTax.percentage();
            if (rawPct != null) {
                serviceFeeTaxPct = rawPct.doubleValue();
            }
        }

        String deliveryFeeTaxName = "";
        double deliveryFeeTaxPct = 0d;
        if (deliveryFeeTax != null) {
            if (deliveryFeeTax.name() != null) {
                deliveryFeeTaxName = deliveryFeeTax.name();
            }
            Double rawPct = deliveryFeeTax.percentage();
            if (rawPct != null) {
                deliveryFeeTaxPct = rawPct.doubleValue();
            }
        }

        return new PreviewData(
                restaurant,
                menuVersion,
            menuImageAssetService.getImageUrl(restaurant.getLogoImageAssetId()),
            menuImageAssetService.getImageUrl(menuVersion.getHeaderImageAssetId()),
                categories,
                taxationRates,
            new TaxRateConfig(
                serviceFeeTaxName,
                serviceFeeTaxPct),
            new TaxRateConfig(
                deliveryFeeTaxName,
                deliveryFeeTaxPct),
                restaurantMenuEditorService.getCheckoutDeliveryFeeInfoText(),
                restaurantMenuEditorService.getCheckoutFeesTaxesInfoText(),
                restaurantMenuEditorService.getCheckoutServiceFeeRate(),
                restaurantMenuEditorService.getCheckoutTimeoutMinutes());
    }

    private ItemData toItemData(RestaurantMenuVersion menuVersion, RestaurantMenuCategory category, RestaurantMenuItem item) {
        RestaurantMenuEditorService.ItemSettingsSnapshot settings = restaurantMenuEditorService.loadItemSettings(item);

        List<SizeData> sizes = restaurantMenuEditorService.listSizesForItem(menuVersion.getId(), item.getId())
                .stream()
                .map(size -> new SizeData(
                        size.getId(),
                        size.getName(),
                        calculateAbsoluteSizePrice(item, size),
                        Boolean.TRUE.equals(size.getDefaultSize()),
                        loadOptionGroupsForItemSize(menuVersion.getId(), size.getId())))
                .toList();

        Double basePrice = item.getBasePrice();
        return new ItemData(
                item.getId(),
                item.getName(),
                item.getDescription(),
                menuImageAssetService.getImageUrl(item.getImageAssetId()),
                basePrice == null ? 0d : basePrice,
                defaultTaxationCategory(restaurantMenuEditorService.getItemTaxationCategory(item.getId())),
                settings.outOfStock(),
                new LinkedHashSet<>(settings.tags()),
                sizes,
                loadOptionGroupsForItem(menuVersion.getId(), category.getId(), item.getId()));
    }

    private String defaultTaxationCategory(String value) {
        if (value == null || value.isBlank()) {
            return "Food";
        }
        return value.trim();
    }

        private List<OptionGroupData> loadOptionGroupsForItem(Long menuVersionId, Long categoryId, Long itemId) {
        List<RestaurantMenuOptionGroup> inheritedGroups = restaurantMenuEditorService
            .listOptionGroupsForCategory(menuVersionId, categoryId)
            .stream()
            .filter(group -> group.getItemId() == null && group.getItemSizeId() == null)
            .toList();

        List<RestaurantMenuOptionGroup> itemGroups = restaurantMenuEditorService
            .listOptionGroupsForItem(menuVersionId, itemId);

        List<OptionGroupData> merged = new ArrayList<>();
        Set<String> seenGroupKeys = new java.util.HashSet<>();

        for (RestaurantMenuOptionGroup group : inheritedGroups) {
            String identityKey = buildGroupIdentityKey(group);
            if (seenGroupKeys.add(identityKey)) {
                merged.add(toOptionGroupData(group));
            }
        }

        for (RestaurantMenuOptionGroup group : itemGroups) {
            String identityKey = buildGroupIdentityKey(group);
            if (seenGroupKeys.add(identityKey)) {
                merged.add(toOptionGroupData(group));
            }
        }

        return merged;
    }

        private String buildGroupIdentityKey(RestaurantMenuOptionGroup group) {
        if (group.getSourceGroupId() != null) {
            return "src:" + group.getSourceGroupId();
        }
        return "name:"
            + (group.getName() == null ? "" : group.getName().trim().toLowerCase())
            + "|min:" + group.getForceMin()
            + "|max:" + group.getForceMax()
            + "|req:" + group.getRequiredSelection();
        }

    private List<OptionGroupData> loadOptionGroupsForItemSize(Long menuVersionId, Long itemSizeId) {
        return restaurantMenuEditorService.listOptionGroupsForItemSize(menuVersionId, itemSizeId).stream()
                .sorted(Comparator.comparing(RestaurantMenuOptionGroup::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(RestaurantMenuOptionGroup::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toOptionGroupData)
                .toList();
    }

    private OptionGroupData toOptionGroupData(RestaurantMenuOptionGroup group) {
        List<OptionData> options = restaurantMenuEditorService.listOptionsForGroup(group.getId()).stream()
            .map(option -> {
                Double optionPrice = option.getPrice();
                return new OptionData(
                    option.getId(),
                    option.getName(),
                    optionPrice == null ? 0d : optionPrice,
                    Boolean.TRUE.equals(option.getDefaultOption()),
                    Boolean.TRUE.equals(option.getOutOfStock()));
            })
                .toList();

        Integer forceMin = group.getForceMin();
        Integer forceMax = group.getForceMax();

        return new OptionGroupData(
                group.getId(),
                group.getName(),
                Boolean.TRUE.equals(group.getRequiredSelection()),
                Boolean.TRUE.equals(group.getAllowQuantity()),
            forceMin == null ? 0 : forceMin,
            forceMax == null ? 0 : forceMax,
                restaurantMenuEditorService.getOptionGroupMajorGroup(group.getId()),
                restaurantMenuEditorService.getOptionGroupTaxationCategory(group.getId()),
                options);
    }

    private double calculateAbsoluteSizePrice(RestaurantMenuItem item, RestaurantMenuItemSize size) {
        Double baseValue = item.getBasePrice();
        Double deltaValue = size.getPrice();
        double basePrice = baseValue == null ? 0d : baseValue;
        double delta = deltaValue == null ? 0d : deltaValue;
        return basePrice + delta;
    }

}