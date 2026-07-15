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

    public static final double GST_RATE = 0.05d;
    private static final List<String> DEFAULT_TAXATION_CATEGORY_OPTIONS = List.of("Food", "Alcohol");

    public record PreviewData(
            Restaurant restaurant,
            RestaurantMenuVersion menuVersion,
            List<CategoryData> categories,
            Map<String, Double> taxationRates,
            double serviceFeeRate,
            double deliveryFee) {
    }

    public record CategoryData(
            Long id,
            String name,
            String description,
            List<ItemData> items) {
    }

    public record ItemData(
            Long id,
            String name,
            String description,
            double basePrice,
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

    public RestaurantMenuOrderPreviewService(RestaurantMenuEditorService restaurantMenuEditorService) {
        this.restaurantMenuEditorService = restaurantMenuEditorService;
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
                    .map(item -> toItemData(menuVersion, item))
                    .toList();
            categories.add(new CategoryData(category.getId(), category.getName(), category.getDescription(), items));
        }

        Map<String, Double> taxationRates = new LinkedHashMap<>();
        for (RestaurantMenuEditorService.TaxationCategoryRate rate : restaurantMenuEditorService
                .getTaxationCategoryRates(DEFAULT_TAXATION_CATEGORY_OPTIONS)) {
            if (rate != null && rate.name() != null) {
                Double percentage = rate.percentage();
                taxationRates.put(rate.name(), percentage == null ? 0d : percentage);
            }
        }

        return new PreviewData(
                restaurant,
                menuVersion,
                categories,
                taxationRates,
                resolveServiceFeeRate(restaurant),
                resolveDeliveryFee(restaurant));
    }

    private ItemData toItemData(RestaurantMenuVersion menuVersion, RestaurantMenuItem item) {
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
            basePrice == null ? 0d : basePrice,
                settings.outOfStock(),
                new LinkedHashSet<>(settings.tags()),
                sizes,
                loadOptionGroupsForItem(menuVersion.getId(), item.getId()));
    }

    private List<OptionGroupData> loadOptionGroupsForItem(Long menuVersionId, Long itemId) {
        return restaurantMenuEditorService.listOptionGroupsForItem(menuVersionId, itemId).stream()
                .sorted(Comparator.comparing(RestaurantMenuOptionGroup::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(RestaurantMenuOptionGroup::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toOptionGroupData)
                .toList();
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

    private double resolveServiceFeeRate(Restaurant restaurant) {
        Double value = restaurant.getCommissionRate();
        if (value == null) {
            return 0d;
        }
        return value > 1d ? value / 100d : value;
    }

    private double resolveDeliveryFee(Restaurant restaurant) {
        Double value = restaurant.getDeliveryFee();
        return value == null ? 0d : value;
    }
}