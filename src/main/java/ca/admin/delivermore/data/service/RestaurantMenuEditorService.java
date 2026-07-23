package ca.admin.delivermore.data.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuCategory;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItem;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemAllergen;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemNutrition;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemSize;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItemTag;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOption;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionAllergen;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionGroup;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionNutrition;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuOptionTag;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuVersion;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuVersion.WorkflowStatus;
import ca.admin.delivermore.collector.data.entity.SettingEntity;
import ca.admin.delivermore.collector.data.service.RestaurantMenuImportService;
import ca.admin.delivermore.collector.data.service.RestaurantMenuItemAllergenRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuItemNutritionRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuItemTagRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuOptionAllergenRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuOptionNutritionRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuOptionTagRepository;
import ca.admin.delivermore.collector.data.service.RestaurantMenuVersionRepository;
import ca.admin.delivermore.collector.data.service.RestaurantRepository;
import ca.admin.delivermore.collector.data.service.SettingRepository;

@Service
public class RestaurantMenuEditorService {

    public record MenuVersionSummary(Long id, Integer versionNumber, Boolean active, WorkflowStatus workflowStatus, LocalDateTime fetchedAt) {
    }

    public record ItemSettingsSnapshot(
            boolean outOfStock,
            Set<String> tags,
            Set<String> allergens,
            String ingredients,
            String additives,
            String nutritionalSize,
            Map<String, String> nutritionalValues) {
    }

    public record OptionSettingsSnapshot(
            boolean outOfStock,
            Set<String> tags,
            Set<String> allergens,
            String ingredients,
            String additives,
            String nutritionalSize,
            Map<String, String> nutritionalValues) {
    }

    public record TaxationCategoryRate(String category, String taxName, Double percentage) {
    }

    public record NamedTaxRate(String name, Double percentage) {
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MENU_EDITOR_SETTINGS_SECTION = "menu_editor";
    private static final String CHECKOUT_SETTINGS_SECTION = "checkout";
    private static final String ITEM_TAG_OPTIONS_SETTING = "item_tag_options";
    private static final String ITEM_ALLERGEN_OPTIONS_SETTING = "item_allergen_options";
    private static final String MAJOR_GROUP_OPTIONS_SETTING = "major_group_options";
    private static final String TAXATION_CATEGORY_OPTIONS_SETTING = "taxation_category_options";
    private static final String SERVICE_FEE_TAX_SETTING = "service_fee_tax";
    private static final String DELIVERY_FEE_TAX_SETTING = "delivery_fee_tax";
    private static final String CHECKOUT_SERVICE_FEE_RATE_SETTING = "service_fee_rate";
    private static final String CHECKOUT_TIMEOUT_MINUTES_SETTING = "checkout_timeout_minutes";
    private static final String CHECKOUT_AUTO_APPROVE_MINUTES_SETTING = "checkout_auto_approve_minutes";
    private static final String CHECKOUT_DELIVERY_FEE_INFO_TEXT_SETTING = "delivery_fee_info_text";
    private static final String CHECKOUT_FEES_TAXES_INFO_TEXT_SETTING = "fees_taxes_info_text";
    private static final String ITEM_TAXATION_CATEGORY_SETTING_PREFIX = "item_taxation_category_";
    private static final String OPTION_GROUP_MAJOR_GROUP_SETTING_PREFIX = "option_group_major_group_";
    private static final String OPTION_GROUP_TAXATION_CATEGORY_SETTING_PREFIX = "option_group_taxation_category_";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMenuVersionRepository restaurantMenuVersionRepository;
    private final RestaurantMenuCategoryEditorRepository restaurantMenuCategoryEditorRepository;
    private final RestaurantMenuItemEditorRepository restaurantMenuItemEditorRepository;
    private final RestaurantMenuItemSizeEditorRepository restaurantMenuItemSizeEditorRepository;
    private final RestaurantMenuItemTagRepository restaurantMenuItemTagRepository;
    private final RestaurantMenuItemAllergenRepository restaurantMenuItemAllergenRepository;
    private final RestaurantMenuItemNutritionRepository restaurantMenuItemNutritionRepository;
    private final RestaurantMenuOptionGroupEditorRepository restaurantMenuOptionGroupEditorRepository;
    private final RestaurantMenuOptionEditorRepository restaurantMenuOptionEditorRepository;
    private final RestaurantMenuOptionTagRepository restaurantMenuOptionTagRepository;
    private final RestaurantMenuOptionAllergenRepository restaurantMenuOptionAllergenRepository;
    private final RestaurantMenuOptionNutritionRepository restaurantMenuOptionNutritionRepository;
    private final SettingRepository settingRepository;
    private final RestaurantMenuImportService restaurantMenuImportService;

    public RestaurantMenuEditorService(
            RestaurantRepository restaurantRepository,
            RestaurantMenuVersionRepository restaurantMenuVersionRepository,
            RestaurantMenuCategoryEditorRepository restaurantMenuCategoryEditorRepository,
            RestaurantMenuItemEditorRepository restaurantMenuItemEditorRepository,
            RestaurantMenuItemSizeEditorRepository restaurantMenuItemSizeEditorRepository,
            RestaurantMenuItemTagRepository restaurantMenuItemTagRepository,
            RestaurantMenuItemAllergenRepository restaurantMenuItemAllergenRepository,
            RestaurantMenuItemNutritionRepository restaurantMenuItemNutritionRepository,
            RestaurantMenuOptionGroupEditorRepository restaurantMenuOptionGroupEditorRepository,
            RestaurantMenuOptionEditorRepository restaurantMenuOptionEditorRepository,
            RestaurantMenuOptionTagRepository restaurantMenuOptionTagRepository,
            RestaurantMenuOptionAllergenRepository restaurantMenuOptionAllergenRepository,
            RestaurantMenuOptionNutritionRepository restaurantMenuOptionNutritionRepository,
            SettingRepository settingRepository,
            RestaurantMenuImportService restaurantMenuImportService) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantMenuVersionRepository = restaurantMenuVersionRepository;
        this.restaurantMenuCategoryEditorRepository = restaurantMenuCategoryEditorRepository;
        this.restaurantMenuItemEditorRepository = restaurantMenuItemEditorRepository;
        this.restaurantMenuItemSizeEditorRepository = restaurantMenuItemSizeEditorRepository;
        this.restaurantMenuItemTagRepository = restaurantMenuItemTagRepository;
        this.restaurantMenuItemAllergenRepository = restaurantMenuItemAllergenRepository;
        this.restaurantMenuItemNutritionRepository = restaurantMenuItemNutritionRepository;
        this.restaurantMenuOptionGroupEditorRepository = restaurantMenuOptionGroupEditorRepository;
        this.restaurantMenuOptionEditorRepository = restaurantMenuOptionEditorRepository;
        this.restaurantMenuOptionTagRepository = restaurantMenuOptionTagRepository;
        this.restaurantMenuOptionAllergenRepository = restaurantMenuOptionAllergenRepository;
        this.restaurantMenuOptionNutritionRepository = restaurantMenuOptionNutritionRepository;
        this.settingRepository = settingRepository;
        this.restaurantMenuImportService = restaurantMenuImportService;
    }

    public List<String> getItemTagOptions(List<String> defaults) {
        return getListSetting(ITEM_TAG_OPTIONS_SETTING, defaults);
    }

    public List<String> getItemAllergenOptions(List<String> defaults) {
        return getListSetting(ITEM_ALLERGEN_OPTIONS_SETTING, defaults);
    }

    public List<String> getMajorGroupOptions(List<String> defaults) {
        return getListSetting(MAJOR_GROUP_OPTIONS_SETTING, defaults);
    }

    public List<String> getTaxationCategoryOptions(List<String> defaults) {
        return getTaxationCategoryRates(defaults).stream()
                .map(TaxationCategoryRate::category)
                .toList();
    }

    public NamedTaxRate getServiceFeeTaxRate() {
        return getNamedTaxRateSetting(SERVICE_FEE_TAX_SETTING);
    }

    public NamedTaxRate getDeliveryFeeTaxRate() {
        return getNamedTaxRateSetting(DELIVERY_FEE_TAX_SETTING);
    }

    public double getCheckoutServiceFeeRate() {
        double rate = getDoubleSetting(CHECKOUT_SETTINGS_SECTION, CHECKOUT_SERVICE_FEE_RATE_SETTING, 0d);
        double normalized = rate > 1d ? rate / 100d : rate;
        return normalizeDecimal(normalized, 6);
    }

    public int getCheckoutTimeoutMinutes() {
        return Math.max(1, getIntegerSetting(CHECKOUT_SETTINGS_SECTION, CHECKOUT_TIMEOUT_MINUTES_SETTING, 10));
    }

    public int getCheckoutAutoApproveMinutes() {
        return Math.max(1, getIntegerSetting(CHECKOUT_SETTINGS_SECTION, CHECKOUT_AUTO_APPROVE_MINUTES_SETTING, 45));
    }

    public String getCheckoutDeliveryFeeInfoText() {
        return getStringSetting(CHECKOUT_SETTINGS_SECTION, CHECKOUT_DELIVERY_FEE_INFO_TEXT_SETTING);
    }

    public String getCheckoutFeesTaxesInfoText() {
        return getStringSetting(CHECKOUT_SETTINGS_SECTION, CHECKOUT_FEES_TAXES_INFO_TEXT_SETTING);
    }

    public List<TaxationCategoryRate> getTaxationCategoryRates(List<String> defaults) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, TAXATION_CATEGORY_OPTIONS_SETTING);
        if (setting == null || setting.getValueAsList().isEmpty()) {
            return defaults.stream()
                    .map(value -> new TaxationCategoryRate(value, "", 0d))
                    .toList();
        }

        LinkedHashMap<String, Double> byName = new LinkedHashMap<>();
        LinkedHashMap<String, String> taxNameByCategory = new LinkedHashMap<>();
        for (String raw : setting.getValueAsList()) {
            TaxationCategoryRate parsed = parseTaxationCategoryRate(raw);
            if (parsed == null || parsed.category() == null) {
                continue;
            }
            Double pct = parsed.percentage();
            String taxName = parsed.taxName() == null ? "" : parsed.taxName();
            if (pct == null) {
                byName.putIfAbsent(parsed.category(), 0d);
            } else {
                byName.putIfAbsent(parsed.category(), pct);
            }
            taxNameByCategory.putIfAbsent(parsed.category(), taxName);
        }

        if (byName.isEmpty()) {
            return defaults.stream()
                    .map(value -> new TaxationCategoryRate(value, "", 0d))
                    .toList();
        }

        return byName.entrySet().stream()
                .map(entry -> new TaxationCategoryRate(
                        entry.getKey(),
                        taxNameByCategory.getOrDefault(entry.getKey(), ""),
                        entry.getValue()))
                .toList();
    }

    public String getOptionGroupMajorGroup(Long optionGroupId) {
        Long canonicalGroupId = resolveCanonicalOptionGroupId(optionGroupId);
        return getStringSetting(optionGroupMajorGroupSettingName(canonicalGroupId));
    }

    public String getOptionGroupTaxationCategory(Long optionGroupId) {
        Long canonicalGroupId = resolveCanonicalOptionGroupId(optionGroupId);
        return getStringSetting(optionGroupTaxationCategorySettingName(canonicalGroupId));
    }

    public String getItemTaxationCategory(Long itemId) {
        return getStringSetting(itemTaxationCategorySettingName(itemId));
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveItemTagOptions(List<String> values) {
        saveListSetting(
                ITEM_TAG_OPTIONS_SETTING,
                "Menu editor master list for 'Mark items as' checkboxes",
                values);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveItemAllergenOptions(List<String> values) {
        saveListSetting(
                ITEM_ALLERGEN_OPTIONS_SETTING,
                "Menu editor master list for item allergen checkboxes",
                values);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveOptionGroupMajorGroup(Long optionGroupId, String value) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId).orElse(null);
        Long canonicalGroupId = resolveCanonicalOptionGroupId(optionGroupId);
        saveStringSetting(
                optionGroupMajorGroupSettingName(canonicalGroupId),
                "Menu editor choice-group major group value",
                value);

        if (group == null || group.getMenuVersionId() == null || group.getSourceGroupId() == null) {
            return;
        }

        List<RestaurantMenuOptionGroup> siblings = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndSourceGroupId(group.getMenuVersionId(), group.getSourceGroupId());
        for (RestaurantMenuOptionGroup sibling : siblings) {
            if (sibling.getId() == null || sibling.getId().equals(canonicalGroupId)) {
                continue;
            }
            saveStringSetting(
                    optionGroupMajorGroupSettingName(sibling.getId()),
                    "Menu editor choice-group major group value",
                    value);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveOptionGroupTaxationCategory(Long optionGroupId, String value) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId).orElse(null);
        Long canonicalGroupId = resolveCanonicalOptionGroupId(optionGroupId);
        saveStringSetting(
            optionGroupTaxationCategorySettingName(canonicalGroupId),
                "Menu editor choice-group taxation category value",
                value);

        if (group == null || group.getMenuVersionId() == null || group.getSourceGroupId() == null) {
            return;
        }

        List<RestaurantMenuOptionGroup> siblings = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndSourceGroupId(group.getMenuVersionId(), group.getSourceGroupId());
        for (RestaurantMenuOptionGroup sibling : siblings) {
            if (sibling.getId() == null || sibling.getId().equals(canonicalGroupId)) {
                continue;
            }
            saveStringSetting(
                    optionGroupTaxationCategorySettingName(sibling.getId()),
                    "Menu editor choice-group taxation category value",
                    value);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveItemTaxationCategory(Long itemId, String value) {
        saveStringSetting(
                itemTaxationCategorySettingName(itemId),
                "Menu editor standalone-item taxation category value",
                value);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveMajorGroupOptions(List<String> values) {
        saveListSetting(
                MAJOR_GROUP_OPTIONS_SETTING,
                "Menu editor master list for choice group major-group options",
                values);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveTaxationCategoryOptions(List<String> values) {
        List<TaxationCategoryRate> mapped = values == null
                ? List.of()
                : values.stream()
                        .map(value -> new TaxationCategoryRate(value, "", 0d))
                        .toList();
        saveTaxationCategoryRates(mapped);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveServiceFeeTaxRate(NamedTaxRate value) {
        saveNamedTaxRateSetting(
                SERVICE_FEE_TAX_SETTING,
                "Tax configuration for service fee",
                value);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveDeliveryFeeTaxRate(NamedTaxRate value) {
        saveNamedTaxRateSetting(
                DELIVERY_FEE_TAX_SETTING,
                "Tax configuration for delivery fee",
                value);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCheckoutServiceFeeRate(double value) {
        double normalized = Math.max(0d, value);
        saveDoubleSetting(
                CHECKOUT_SETTINGS_SECTION,
                CHECKOUT_SERVICE_FEE_RATE_SETTING,
                "Global checkout service fee rate as decimal (0.055 = 5.5%)",
                normalizeDecimal(normalized, 6));
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCheckoutTimeoutMinutes(int value) {
        saveIntegerSetting(
                CHECKOUT_SETTINGS_SECTION,
                CHECKOUT_TIMEOUT_MINUTES_SETTING,
                "Global checkout timeout in minutes",
                Math.max(1, value));
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCheckoutAutoApproveMinutes(int value) {
        saveIntegerSetting(
                CHECKOUT_SETTINGS_SECTION,
                CHECKOUT_AUTO_APPROVE_MINUTES_SETTING,
                "Default customer-facing ETA minutes used when a restaurant auto-approves checkout orders",
                Math.max(1, value));
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCheckoutDeliveryFeeInfoText(String value) {
        saveStringSetting(
                CHECKOUT_SETTINGS_SECTION,
                CHECKOUT_DELIVERY_FEE_INFO_TEXT_SETTING,
                "Checkout delivery fee information popup text",
                value);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCheckoutFeesTaxesInfoText(String value) {
        saveStringSetting(
                CHECKOUT_SETTINGS_SECTION,
                CHECKOUT_FEES_TAXES_INFO_TEXT_SETTING,
                "Checkout fees and taxes information popup text",
                value);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveTaxationCategoryRates(List<TaxationCategoryRate> values) {
        List<String> encoded = new ArrayList<>();
        if (values != null) {
            LinkedHashMap<String, TaxationCategoryRate> deduped = new LinkedHashMap<>();
            for (TaxationCategoryRate value : values) {
                if (value == null) {
                    continue;
                }
                String category = trimToNull(value.category());
                if (category == null) {
                    continue;
                }
                String taxName = trimToNull(value.taxName());
                Double pct = value.percentage();
                double normalizedPct = pct == null ? 0d : pct;
                deduped.putIfAbsent(
                        category,
                        new TaxationCategoryRate(category, taxName == null ? "" : taxName, normalizedPct));
            }
            for (TaxationCategoryRate entry : deduped.values()) {
                encoded.add(entry.category() + "|" + entry.taxName() + "|" + entry.percentage());
            }
        }

        saveListSetting(
                TAXATION_CATEGORY_OPTIONS_SETTING,
                "Menu editor master list for choice group taxation category options",
                encoded);
    }

    private TaxationCategoryRate parseTaxationCategoryRate(String raw) {
        String cleaned = trimToNull(raw);
        if (cleaned == null) {
            return null;
        }

        String[] tokens = cleaned.split("\\|", -1);
        if (tokens.length == 3) {
            String category = trimToNull(tokens[0]);
            String taxName = trimToNull(tokens[1]);
            String rateText = trimToNull(tokens[2]);
            if (category == null) {
                return null;
            }
            return new TaxationCategoryRate(category, taxName == null ? "" : taxName, parseRate(rateText));
        }

        if (tokens.length == 2) {
            String category = trimToNull(tokens[0]);
            String rateText = trimToNull(tokens[1]);
            if (category == null) {
                return null;
            }
            return new TaxationCategoryRate(category, "", parseRate(rateText));
        }

        return new TaxationCategoryRate(cleaned, "", 0d);
    }

    private double parseRate(String rateText) {
        if (rateText == null) {
            return 0d;
        }

        try {
            return Double.parseDouble(rateText);
        } catch (NumberFormatException ignored) {
            return 0d;
        }
    }

    private NamedTaxRate getNamedTaxRateSetting(String settingName) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        String encoded = setting == null ? null : trimToNull(setting.getValue());
        if (encoded == null) {
            return new NamedTaxRate("", 0d);
        }

        String[] tokens = encoded.split("\\|", -1);
        if (tokens.length != 2) {
            return new NamedTaxRate("", 0d);
        }

        String name = trimToNull(tokens[0]);
        double percentage = parseRate(trimToNull(tokens[1]));
        return new NamedTaxRate(name == null ? "" : name, percentage);
    }

    private void saveNamedTaxRateSetting(String settingName, String description, NamedTaxRate value) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(MENU_EDITOR_SETTINGS_SECTION);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.STRING);
        }

        String taxName = value == null ? "" : trimToNull(value.name());
        double percentage = 0d;
        if (value != null) {
            Double percentValue = value.percentage();
            if (percentValue != null) {
                percentage = percentValue;
            }
        }
        setting.setValue((taxName == null ? "" : taxName) + "|" + percentage);
        settingRepository.save(setting);
    }

    private List<String> getListSetting(String settingName, List<String> defaults) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting == null || setting.getValueAsList().isEmpty()) {
            return defaults;
        }
        return setting.getValueAsList().stream()
                .map(value -> value == null ? null : value.trim())
                .filter(value -> value != null && !value.isEmpty())
                .distinct()
                .toList();
    }

    private double getDoubleSetting(String section, String settingName, double defaultValue) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            return defaultValue;
        }
        String raw = trimToNull(setting.getValue());
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int getIntegerSetting(String section, String settingName, int defaultValue) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            return defaultValue;
        }

        String raw = trimToNull(setting.getValue());
        if (raw == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private void saveDoubleSetting(String section, String settingName, String description, double value) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(section);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.DOUBLE);
        }
        setting.setValue(value);
        settingRepository.save(setting);
    }

    private void saveIntegerSetting(String section, String settingName, String description, int value) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(section);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.INTEGER);
        }
        setting.setValue(value);
        settingRepository.save(setting);
    }

    private double normalizeDecimal(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void saveListSetting(String settingName, String description, List<String> values) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(MENU_EDITOR_SETTINGS_SECTION);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.LIST);
        }

        List<String> cleaned = values == null
                ? List.of()
                : values.stream()
                        .map(value -> value == null ? null : value.trim())
                        .filter(value -> value != null && !value.isEmpty())
                        .distinct()
                        .toList();
        setting.setValue(cleaned);
        settingRepository.save(setting);
    }

    private String getStringSetting(String settingName) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting == null) {
            return null;
        }
        return trimToNull(setting.getValue());
    }

    private String getStringSetting(String section, String settingName) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            return null;
        }
        return trimToNull(setting.getValue());
    }

    private void saveStringSetting(String settingName, String description, String value) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(MENU_EDITOR_SETTINGS_SECTION);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.STRING);
        }

        String cleaned = trimToNull(value);
        if (cleaned == null) {
            settingRepository.delete(setting);
            return;
        }

        setting.setValue(cleaned);
        settingRepository.save(setting);
    }

    private void saveStringSetting(String section, String settingName, String description, String value) {
        SettingEntity setting = settingRepository.findBySectionAndName(section, settingName);
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(section);
            setting.setName(settingName);
            setting.setDescription(description);
            setting.setValueType(SettingEntity.ValueType.STRING);
        }

        String cleaned = trimToNull(value);
        if (cleaned == null) {
            settingRepository.delete(setting);
            return;
        }

        setting.setValue(cleaned);
        settingRepository.save(setting);
    }

    private void deleteSetting(String settingName) {
        SettingEntity setting = settingRepository.findBySectionAndName(MENU_EDITOR_SETTINGS_SECTION, settingName);
        if (setting != null) {
            settingRepository.delete(setting);
        }
    }

    private String optionGroupMajorGroupSettingName(Long optionGroupId) {
        return OPTION_GROUP_MAJOR_GROUP_SETTING_PREFIX + optionGroupId;
    }

    private String itemTaxationCategorySettingName(Long itemId) {
        return ITEM_TAXATION_CATEGORY_SETTING_PREFIX + itemId;
    }

    private String optionGroupTaxationCategorySettingName(Long optionGroupId) {
        return OPTION_GROUP_TAXATION_CATEGORY_SETTING_PREFIX + optionGroupId;
    }

    private Long resolveCanonicalOptionGroupId(Long optionGroupId) {
        if (optionGroupId == null) {
            return null;
        }

        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId).orElse(null);
        if (group == null || group.getMenuVersionId() == null || group.getSourceGroupId() == null) {
            return optionGroupId;
        }

        List<RestaurantMenuOptionGroup> siblings = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndSourceGroupId(group.getMenuVersionId(), group.getSourceGroupId());

        return siblings.stream()
                .filter(sibling -> sibling.getId() != null)
                .sorted(Comparator
                        .comparing((RestaurantMenuOptionGroup sibling) -> sibling.getItemId() != null || sibling.getItemSizeId() != null)
                        .thenComparing(RestaurantMenuOptionGroup::getId))
                .map(RestaurantMenuOptionGroup::getId)
                .findFirst()
                .orElse(optionGroupId);
    }

    public Restaurant getRestaurant(Long restaurantId) {
        List<Restaurant> restaurants = restaurantRepository.findEffectiveByRestaurantId(restaurantId, LocalDate.now());
        if (!restaurants.isEmpty()) {
            return restaurants.getFirst();
        }
        restaurants = restaurantRepository.findByRestaurantId(restaurantId);
        if (!restaurants.isEmpty()) {
            return restaurants.getFirst();
        }
        return null;
    }

    public List<Restaurant> listEffectiveRestaurants() {
        List<Restaurant> restaurants = restaurantRepository.getEffectiveRestaurants(LocalDate.now());
        if (restaurants.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, Restaurant> byRestaurantId = new LinkedHashMap<>();
        for (Restaurant value : restaurants) {
            if (value == null || value.getRestaurantId() == null) {
                continue;
            }
            byRestaurantId.putIfAbsent(value.getRestaurantId(), value);
        }
        return new ArrayList<>(byRestaurantId.values());
    }

    public List<MenuVersionSummary> listVersionSummaries(Long restaurantId) {
        return restaurantMenuVersionRepository.findByRestaurantIdOrderByVersionNumberDesc(restaurantId).stream()
                .map(version -> new MenuVersionSummary(
                        version.getId(),
                        version.getVersionNumber(),
                        version.getActive(),
                        version.getWorkflowStatus(),
                        version.getFetchedAt()))
                .toList();
    }

    public RestaurantMenuVersion getEditorVersion(Long restaurantId) {
        RestaurantMenuVersion draftVersion = restaurantMenuVersionRepository.findTopByRestaurantIdAndWorkflowStatusOrderByVersionNumberDesc(restaurantId, WorkflowStatus.DRAFT);
        if (draftVersion != null) {
            return draftVersion;
        }

        RestaurantMenuVersion activeVersion = restaurantMenuVersionRepository.findByRestaurantIdAndActiveTrue(restaurantId);
        if (activeVersion != null) {
            return activeVersion;
        }

        return restaurantMenuVersionRepository.findTopByRestaurantIdOrderByVersionNumberDesc(restaurantId);
    }

    public List<RestaurantMenuCategory> listCategories(Long menuVersionId) {
        return restaurantMenuCategoryEditorRepository.findByMenuVersionIdOrderByDisplayOrderAscNameAsc(menuVersionId);
    }

    public List<RestaurantMenuItem> listItems(Long menuVersionId) {
        return restaurantMenuItemEditorRepository.findByMenuVersionIdOrderByDisplayOrderAscNameAsc(menuVersionId);
    }

    public List<RestaurantMenuItem> listItemsForCategory(Long menuVersionId, Long categoryId) {
        return restaurantMenuItemEditorRepository.findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, categoryId);
    }

    public List<RestaurantMenuItemSize> listSizesForItem(Long menuVersionId, Long itemId) {
        return restaurantMenuItemSizeEditorRepository.findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, itemId);
    }

    public RestaurantMenuVersion createDraftFromLatestPulledVersion(Long restaurantId) {
        return restaurantMenuImportService.createDraftFromLatestPulledVersion(restaurantId);
    }

    public RestaurantMenuVersion publishDraftVersion(Long restaurantId, Long draftVersionId) {
        return restaurantMenuImportService.publishDraftVersion(restaurantId, draftVersionId);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuVersion saveMenuVersion(RestaurantMenuVersion menuVersion) {
        return restaurantMenuVersionRepository.save(menuVersion);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuCategory duplicateCategory(Long menuVersionId, Long categoryId) {
        RestaurantMenuCategory source = restaurantMenuCategoryEditorRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException("Category not found: " + categoryId));
        if (!menuVersionId.equals(source.getMenuVersionId())) {
            throw new IllegalStateException("Category belongs to a different menu version");
        }

        int nextDisplayOrder = restaurantMenuCategoryEditorRepository
                .findByMenuVersionIdOrderByDisplayOrderAscNameAsc(menuVersionId)
                .stream()
                .map(RestaurantMenuCategory::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        RestaurantMenuCategory copy = new RestaurantMenuCategory();
        copy.setMenuVersionId(source.getMenuVersionId());
        copy.setSourceCategoryId(source.getSourceCategoryId());
        copy.setSourceMenuId(source.getSourceMenuId());
        copy.setName((source.getName() == null ? "Category" : source.getName()) + " (Copy)");
        copy.setDescription(source.getDescription());
        copy.setActive(source.getActive());
        copy.setActiveBegin(source.getActiveBegin());
        copy.setActiveEnd(source.getActiveEnd());
        copy.setActiveDays(source.getActiveDays());
        copy.setPictureId(source.getPictureId());
        copy.setDisplayOrder(nextDisplayOrder);

        return restaurantMenuCategoryEditorRepository.save(copy);
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeCategory(Long menuVersionId, Long categoryId) {
        RestaurantMenuCategory category = restaurantMenuCategoryEditorRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException("Category not found: " + categoryId));
        if (!menuVersionId.equals(category.getMenuVersionId())) {
            throw new IllegalStateException("Category belongs to a different menu version");
        }

        List<RestaurantMenuItem> items = restaurantMenuItemEditorRepository
                .findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, categoryId);
        for (RestaurantMenuItem item : items) {
            List<RestaurantMenuOptionGroup> groups = restaurantMenuOptionGroupEditorRepository
                    .findByMenuVersionIdAndItemId(menuVersionId, item.getId());
            for (RestaurantMenuOptionGroup group : groups) {
                deleteOptionGroupWithOptions(group);
            }

            List<RestaurantMenuItemSize> sizes = restaurantMenuItemSizeEditorRepository
                    .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, item.getId());
            for (RestaurantMenuItemSize size : sizes) {
                List<RestaurantMenuOptionGroup> sizeGroups = restaurantMenuOptionGroupEditorRepository
                        .findByMenuVersionIdAndItemSizeId(menuVersionId, size.getId());
                for (RestaurantMenuOptionGroup sizeGroup : sizeGroups) {
                    deleteOptionGroupWithOptions(sizeGroup);
                }
            }
            restaurantMenuItemSizeEditorRepository.deleteAll(sizes);
            deleteItemSettings(item.getId());
            restaurantMenuItemEditorRepository.delete(item);
        }

        restaurantMenuCategoryEditorRepository.delete(category);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuItem duplicateItem(Long menuVersionId, Long itemId) {
        RestaurantMenuItem source = restaurantMenuItemEditorRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Item not found: " + itemId));
        if (!menuVersionId.equals(source.getMenuVersionId())) {
            throw new IllegalStateException("Item belongs to a different menu version");
        }
        ItemSettingsSnapshot sourceSettings = loadItemSettings(source);

        int nextDisplayOrder = restaurantMenuItemEditorRepository
                .findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, source.getCategoryId())
                .stream()
                .map(RestaurantMenuItem::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        RestaurantMenuItem copy = new RestaurantMenuItem();
        copy.setMenuVersionId(source.getMenuVersionId());
        copy.setCategoryId(source.getCategoryId());
        copy.setSourceItemId(source.getSourceItemId());
        copy.setSourceCategoryId(source.getSourceCategoryId());
        copy.setName((source.getName() == null ? "Item" : source.getName()) + " (Copy)");
        copy.setDescription(source.getDescription());
        copy.setBasePrice(source.getBasePrice());
        copy.setActive(source.getActive());
        copy.setActiveBegin(source.getActiveBegin());
        copy.setActiveEnd(source.getActiveEnd());
        copy.setActiveDays(source.getActiveDays());
        copy.setOutOfStock(sourceSettings.outOfStock());
        copy.setIngredients(sourceSettings.ingredients());
        copy.setAdditives(sourceSettings.additives());
        copy.setNutritionalValuesSize(sourceSettings.nutritionalSize());
        copy.setTagsJson(null);
        copy.setExtrasJson(null);
        copy.setDisplayOrder(nextDisplayOrder);

        RestaurantMenuItem duplicated = saveItemWithSettings(
            copy,
            sourceSettings.tags(),
            sourceSettings.allergens(),
            sourceSettings.nutritionalSize(),
            sourceSettings.nutritionalValues());

        saveItemTaxationCategory(duplicated.getId(), getItemTaxationCategory(source.getId()));
        return duplicated;
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeItem(Long menuVersionId, Long itemId) {
        RestaurantMenuItem item = restaurantMenuItemEditorRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Item not found: " + itemId));
        if (!menuVersionId.equals(item.getMenuVersionId())) {
            throw new IllegalStateException("Item belongs to a different menu version");
        }

        List<RestaurantMenuOptionGroup> groups = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndItemId(menuVersionId, item.getId());
        for (RestaurantMenuOptionGroup group : groups) {
            deleteOptionGroupWithOptions(group);
        }

        List<RestaurantMenuItemSize> sizes = restaurantMenuItemSizeEditorRepository
            .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, itemId);
        for (RestaurantMenuItemSize size : sizes) {
            List<RestaurantMenuOptionGroup> sizeGroups = restaurantMenuOptionGroupEditorRepository
                    .findByMenuVersionIdAndItemSizeId(menuVersionId, size.getId());
            for (RestaurantMenuOptionGroup sizeGroup : sizeGroups) {
                deleteOptionGroupWithOptions(sizeGroup);
            }
        }
        restaurantMenuItemSizeEditorRepository.deleteAll(sizes);

        deleteItemSettings(item.getId());

        restaurantMenuItemEditorRepository.delete(item);
    }

    public List<RestaurantMenuOptionGroup> listOptionGroupsForVersion(Long menuVersionId) {
        return restaurantMenuOptionGroupEditorRepository.findByMenuVersionIdOrderByDisplayOrderAscNameAsc(menuVersionId);
    }

    public List<RestaurantMenuOptionGroup> listOptionGroupsForItem(Long menuVersionId, Long itemId) {
        return restaurantMenuOptionGroupEditorRepository.findByMenuVersionIdAndItemIdOrderByDisplayOrderAscNameAsc(menuVersionId, itemId);
    }

    public List<RestaurantMenuOptionGroup> listOptionGroupsForCategory(Long menuVersionId, Long categoryId) {
        return restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, categoryId);
    }

    public List<RestaurantMenuOptionGroup> listOptionGroupsForItemSize(Long menuVersionId, Long itemSizeId) {
        return restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndItemSizeIdOrderByDisplayOrderAscNameAsc(menuVersionId, itemSizeId);
    }

    public List<RestaurantMenuOption> listOptionsForGroup(Long optionGroupId) {
        return restaurantMenuOptionEditorRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuOptionGroup saveOptionGroup(RestaurantMenuOptionGroup optionGroup) {
        return restaurantMenuOptionGroupEditorRepository.save(optionGroup);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuOption saveOption(RestaurantMenuOption option) {
        RestaurantMenuOption saved = restaurantMenuOptionEditorRepository.save(option);
        if (Boolean.TRUE.equals(saved.getDefaultOption())) {
            clearDefaultOptionForGroup(saved.getOptionGroupId(), saved.getId());
        }
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveItemOptionGroupOrder(Long menuVersionId, Long itemId, List<Long> orderedGroupIds) {
        List<RestaurantMenuOptionGroup> currentGroups = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscNameAsc(menuVersionId, itemId);
        if (currentGroups.isEmpty()) {
            return;
        }

        Map<Long, RestaurantMenuOptionGroup> byId = new LinkedHashMap<>();
        for (RestaurantMenuOptionGroup group : currentGroups) {
            if (group.getId() != null) {
                byId.put(group.getId(), group);
            }
        }

        List<RestaurantMenuOptionGroup> reordered = new ArrayList<>();
        if (orderedGroupIds != null) {
            for (Long groupId : orderedGroupIds) {
                RestaurantMenuOptionGroup group = byId.remove(groupId);
                if (group != null) {
                    reordered.add(group);
                }
            }
        }
        reordered.addAll(byId.values());

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setDisplayOrder(i + 1);
        }
        restaurantMenuOptionGroupEditorRepository.saveAll(reordered);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveCategoryOptionGroupOrder(Long menuVersionId, Long categoryId, List<Long> orderedGroupIds) {
        List<RestaurantMenuOptionGroup> currentGroups = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, categoryId)
                .stream()
                .filter(group -> group.getItemId() == null && group.getItemSizeId() == null)
                .toList();
        if (currentGroups.isEmpty()) {
            return;
        }

        Map<Long, RestaurantMenuOptionGroup> byId = new LinkedHashMap<>();
        for (RestaurantMenuOptionGroup group : currentGroups) {
            if (group.getId() != null) {
                byId.put(group.getId(), group);
            }
        }

        List<RestaurantMenuOptionGroup> reordered = new ArrayList<>();
        if (orderedGroupIds != null) {
            for (Long groupId : orderedGroupIds) {
                RestaurantMenuOptionGroup group = byId.remove(groupId);
                if (group != null) {
                    reordered.add(group);
                }
            }
        }
        reordered.addAll(byId.values());

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setDisplayOrder(i + 1);
        }
        restaurantMenuOptionGroupEditorRepository.saveAll(reordered);
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveOptionOrder(Long menuVersionId, Long optionGroupId, List<Long> orderedOptionIds) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId)
                .orElseThrow(() -> new IllegalStateException("Choice group not found: " + optionGroupId));
        if (!menuVersionId.equals(group.getMenuVersionId())) {
            throw new IllegalStateException("Choice group belongs to a different menu version");
        }

        List<RestaurantMenuOption> currentOptions = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
        if (currentOptions.isEmpty()) {
            return;
        }

        Map<Long, RestaurantMenuOption> byId = new LinkedHashMap<>();
        for (RestaurantMenuOption option : currentOptions) {
            if (option.getId() != null) {
                byId.put(option.getId(), option);
            }
        }

        List<RestaurantMenuOption> reordered = new ArrayList<>();
        if (orderedOptionIds != null) {
            for (Long optionId : orderedOptionIds) {
                RestaurantMenuOption option = byId.remove(optionId);
                if (option != null) {
                    reordered.add(option);
                }
            }
        }
        reordered.addAll(byId.values());

        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).setDisplayOrder(i + 1);
        }
        restaurantMenuOptionEditorRepository.saveAll(reordered);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuOptionGroup duplicateOptionGroup(Long menuVersionId, Long optionGroupId) {
        RestaurantMenuOptionGroup source = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId)
                .orElseThrow(() -> new IllegalStateException("Choice group not found: " + optionGroupId));
        if (!menuVersionId.equals(source.getMenuVersionId())) {
            throw new IllegalStateException("Choice group belongs to a different menu version");
        }

        int nextDisplayOrder = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdOrderByDisplayOrderAscNameAsc(menuVersionId)
                .stream()
                .map(RestaurantMenuOptionGroup::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        RestaurantMenuOptionGroup copy = new RestaurantMenuOptionGroup();
        copy.setMenuVersionId(source.getMenuVersionId());
        copy.setCategoryId(source.getCategoryId());
        copy.setItemId(source.getItemId());
        copy.setItemSizeId(source.getItemSizeId());
        copy.setSourceGroupId(source.getSourceGroupId());
        copy.setSourceMenuId(source.getSourceMenuId());
        copy.setName((source.getName() == null ? "Choice Group" : source.getName()) + " (Copy)");
        copy.setRequiredSelection(source.getRequiredSelection());
        copy.setAllowQuantity(source.getAllowQuantity());
        copy.setForceMin(source.getForceMin());
        copy.setForceMax(source.getForceMax());
        copy.setDisplayOrder(nextDisplayOrder);

        RestaurantMenuOptionGroup savedGroup = restaurantMenuOptionGroupEditorRepository.save(copy);

        List<RestaurantMenuOption> sourceOptions = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(source.getId());
        for (RestaurantMenuOption sourceOption : sourceOptions) {
            OptionSettingsSnapshot optionSettings = loadOptionSettings(sourceOption);

            RestaurantMenuOption copiedOption = new RestaurantMenuOption();
            copiedOption.setMenuVersionId(savedGroup.getMenuVersionId());
            copiedOption.setOptionGroupId(savedGroup.getId());
            copiedOption.setSourceOptionId(sourceOption.getSourceOptionId());
            copiedOption.setSourceGroupId(sourceOption.getSourceGroupId());
            copiedOption.setName(sourceOption.getName());
            copiedOption.setPrice(sourceOption.getPrice());
            copiedOption.setDefaultOption(sourceOption.getDefaultOption());
            copiedOption.setOutOfStock(optionSettings.outOfStock());
            copiedOption.setIngredients(optionSettings.ingredients());
            copiedOption.setAdditives(optionSettings.additives());
            copiedOption.setNutritionalValuesSize(optionSettings.nutritionalSize());
            copiedOption.setExtrasJson(null);
            copiedOption.setDisplayOrder(sourceOption.getDisplayOrder());
            copiedOption = restaurantMenuOptionEditorRepository.save(copiedOption);

            replaceOptionTags(copiedOption, optionSettings.tags());
            replaceOptionAllergens(copiedOption, optionSettings.allergens());
            replaceOptionNutrition(copiedOption, optionSettings.nutritionalValues());
        }

        saveOptionGroupMajorGroup(savedGroup.getId(), getOptionGroupMajorGroup(source.getId()));
        saveOptionGroupTaxationCategory(savedGroup.getId(), getOptionGroupTaxationCategory(source.getId()));

        return savedGroup;
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeOptionGroup(Long menuVersionId, Long optionGroupId) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId)
                .orElseThrow(() -> new IllegalStateException("Choice group not found: " + optionGroupId));
        if (!menuVersionId.equals(group.getMenuVersionId())) {
            throw new IllegalStateException("Choice group belongs to a different menu version");
        }

        List<RestaurantMenuOption> options = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
        for (RestaurantMenuOption option : options) {
            deleteOptionSettings(option.getId());
        }
        restaurantMenuOptionEditorRepository.deleteAll(options);
        restaurantMenuOptionGroupEditorRepository.delete(group);
        deleteSetting(optionGroupMajorGroupSettingName(optionGroupId));
        deleteSetting(optionGroupTaxationCategorySettingName(optionGroupId));
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeOption(Long menuVersionId, Long optionId) {
        RestaurantMenuOption option = restaurantMenuOptionEditorRepository.findById(optionId)
                .orElseThrow(() -> new IllegalStateException("Choice not found: " + optionId));
        if (!menuVersionId.equals(option.getMenuVersionId())) {
            throw new IllegalStateException("Choice belongs to a different menu version");
        }

        deleteOptionSettings(optionId);
        restaurantMenuOptionEditorRepository.delete(option);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuOption duplicateOption(Long menuVersionId, Long optionId) {
        RestaurantMenuOption source = restaurantMenuOptionEditorRepository.findById(optionId)
                .orElseThrow(() -> new IllegalStateException("Choice not found: " + optionId));
        if (!menuVersionId.equals(source.getMenuVersionId())) {
            throw new IllegalStateException("Choice belongs to a different menu version");
        }

        OptionSettingsSnapshot optionSettings = loadOptionSettings(source);
        int nextDisplayOrder = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(source.getOptionGroupId())
                .stream()
                .map(RestaurantMenuOption::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        RestaurantMenuOption copy = new RestaurantMenuOption();
        copy.setMenuVersionId(source.getMenuVersionId());
        copy.setOptionGroupId(source.getOptionGroupId());
        copy.setSourceOptionId(source.getSourceOptionId());
        copy.setSourceGroupId(source.getSourceGroupId());
        copy.setName((source.getName() == null ? "Choice" : source.getName()) + " (Copy)");
        copy.setPrice(source.getPrice());
        copy.setDefaultOption(source.getDefaultOption());
        copy.setOutOfStock(optionSettings.outOfStock());
        copy.setIngredients(optionSettings.ingredients());
        copy.setAdditives(optionSettings.additives());
        copy.setNutritionalValuesSize(optionSettings.nutritionalSize());
        copy.setExtrasJson(null);
        copy.setDisplayOrder(nextDisplayOrder);

        RestaurantMenuOption saved = restaurantMenuOptionEditorRepository.save(copy);
        replaceOptionTags(saved, optionSettings.tags());
        replaceOptionAllergens(saved, optionSettings.allergens());
        replaceOptionNutrition(saved, optionSettings.nutritionalValues());
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean attachOptionGroupToItem(Long menuVersionId, Long itemId, Long templateGroupId) {
        RestaurantMenuOptionGroup templateGroup = restaurantMenuOptionGroupEditorRepository.findById(templateGroupId)
                .orElseThrow(() -> new IllegalStateException("Choice group not found: " + templateGroupId));

        if (!menuVersionId.equals(templateGroup.getMenuVersionId())) {
            throw new IllegalStateException("Choice group belongs to a different menu version");
        }

        RestaurantMenuItem item = restaurantMenuItemEditorRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Menu item not found: " + itemId));

        if (!menuVersionId.equals(item.getMenuVersionId())) {
            throw new IllegalStateException("Item belongs to a different menu version");
        }

        List<RestaurantMenuOptionGroup> existingGroups =
                restaurantMenuOptionGroupEditorRepository.findByMenuVersionIdAndItemIdOrderByDisplayOrderAscNameAsc(menuVersionId, itemId);
        String templateKey = buildGroupUniqKey(templateGroup);
        boolean alreadyLinked = existingGroups.stream()
                .anyMatch(group -> buildGroupUniqKey(group).equals(templateKey));
        if (alreadyLinked) {
            return false;
        }

        int nextDisplayOrder = existingGroups.stream()
                .map(RestaurantMenuOptionGroup::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        RestaurantMenuOptionGroup newGroup = new RestaurantMenuOptionGroup();
        newGroup.setMenuVersionId(menuVersionId);
        newGroup.setCategoryId(item.getCategoryId());
        newGroup.setItemId(itemId);
        newGroup.setItemSizeId(null);
        newGroup.setSourceGroupId(templateGroup.getSourceGroupId() != null
                ? templateGroup.getSourceGroupId()
                : templateGroup.getId());
        newGroup.setSourceMenuId(templateGroup.getSourceMenuId());
        newGroup.setName(templateGroup.getName());
        newGroup.setRequiredSelection(templateGroup.getRequiredSelection());
        newGroup.setAllowQuantity(templateGroup.getAllowQuantity());
        newGroup.setForceMin(templateGroup.getForceMin());
        newGroup.setForceMax(templateGroup.getForceMax());
        newGroup.setDisplayOrder(nextDisplayOrder);

        RestaurantMenuOptionGroup savedGroup = restaurantMenuOptionGroupEditorRepository.save(newGroup);

        List<RestaurantMenuOption> templateOptions =
                restaurantMenuOptionEditorRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(templateGroupId);
        for (RestaurantMenuOption templateOption : templateOptions) {
            OptionSettingsSnapshot optionSettings = loadOptionSettings(templateOption);
            RestaurantMenuOption newOption = new RestaurantMenuOption();
            newOption.setMenuVersionId(menuVersionId);
            newOption.setOptionGroupId(savedGroup.getId());
            newOption.setSourceOptionId(templateOption.getSourceOptionId() != null
                    ? templateOption.getSourceOptionId()
                    : templateOption.getId());
            newOption.setSourceGroupId(savedGroup.getSourceGroupId());
            newOption.setName(templateOption.getName());
            newOption.setPrice(templateOption.getPrice());
            newOption.setDefaultOption(templateOption.getDefaultOption());
            newOption.setOutOfStock(optionSettings.outOfStock());
            newOption.setIngredients(optionSettings.ingredients());
            newOption.setAdditives(optionSettings.additives());
            newOption.setNutritionalValuesSize(optionSettings.nutritionalSize());
            newOption.setExtrasJson(null);
            newOption.setDisplayOrder(templateOption.getDisplayOrder());
            RestaurantMenuOption savedOption = restaurantMenuOptionEditorRepository.save(newOption);
            replaceOptionTags(savedOption, optionSettings.tags());
            replaceOptionAllergens(savedOption, optionSettings.allergens());
            replaceOptionNutrition(savedOption, optionSettings.nutritionalValues());
        }

        saveOptionGroupMajorGroup(savedGroup.getId(), getOptionGroupMajorGroup(templateGroupId));
        saveOptionGroupTaxationCategory(savedGroup.getId(), getOptionGroupTaxationCategory(templateGroupId));

        return true;
    }

        @org.springframework.transaction.annotation.Transactional
        public boolean attachOptionGroupToCategory(Long menuVersionId, Long categoryId, Long templateGroupId) {
        RestaurantMenuOptionGroup templateGroup = restaurantMenuOptionGroupEditorRepository.findById(templateGroupId)
            .orElseThrow(() -> new IllegalStateException("Choice group not found: " + templateGroupId));

        if (!menuVersionId.equals(templateGroup.getMenuVersionId())) {
            throw new IllegalStateException("Choice group belongs to a different menu version");
        }

        RestaurantMenuCategory category = restaurantMenuCategoryEditorRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalStateException("Category not found: " + categoryId));

        if (!menuVersionId.equals(category.getMenuVersionId())) {
            throw new IllegalStateException("Category belongs to a different menu version");
        }

        List<RestaurantMenuOptionGroup> existingGroups =
            restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndCategoryIdOrderByDisplayOrderAscNameAsc(menuVersionId, categoryId)
                .stream()
                .filter(group -> group.getItemId() == null && group.getItemSizeId() == null)
                .toList();
        String templateKey = buildGroupUniqKey(templateGroup);
        boolean alreadyLinked = existingGroups.stream()
            .anyMatch(group -> buildGroupUniqKey(group).equals(templateKey));
        if (alreadyLinked) {
            return false;
        }

        int nextDisplayOrder = existingGroups.stream()
            .map(RestaurantMenuOptionGroup::getDisplayOrder)
            .filter(order -> order != null)
            .max(Integer::compareTo)
            .orElse(0) + 1;

        RestaurantMenuOptionGroup newGroup = new RestaurantMenuOptionGroup();
        newGroup.setMenuVersionId(menuVersionId);
        newGroup.setCategoryId(categoryId);
        newGroup.setItemId(null);
        newGroup.setItemSizeId(null);
        newGroup.setSourceGroupId(templateGroup.getSourceGroupId() != null
            ? templateGroup.getSourceGroupId()
            : templateGroup.getId());
        newGroup.setSourceMenuId(templateGroup.getSourceMenuId());
        newGroup.setName(templateGroup.getName());
        newGroup.setRequiredSelection(templateGroup.getRequiredSelection());
        newGroup.setAllowQuantity(templateGroup.getAllowQuantity());
        newGroup.setForceMin(templateGroup.getForceMin());
        newGroup.setForceMax(templateGroup.getForceMax());
        newGroup.setDisplayOrder(nextDisplayOrder);

        RestaurantMenuOptionGroup savedGroup = restaurantMenuOptionGroupEditorRepository.save(newGroup);

        List<RestaurantMenuOption> templateOptions =
            restaurantMenuOptionEditorRepository.findByOptionGroupIdOrderByDisplayOrderAscNameAsc(templateGroupId);
        for (RestaurantMenuOption templateOption : templateOptions) {
            OptionSettingsSnapshot optionSettings = loadOptionSettings(templateOption);
            RestaurantMenuOption newOption = new RestaurantMenuOption();
            newOption.setMenuVersionId(menuVersionId);
            newOption.setOptionGroupId(savedGroup.getId());
            newOption.setSourceOptionId(templateOption.getSourceOptionId() != null
                ? templateOption.getSourceOptionId()
                : templateOption.getId());
            newOption.setSourceGroupId(savedGroup.getSourceGroupId());
            newOption.setName(templateOption.getName());
            newOption.setPrice(templateOption.getPrice());
            newOption.setDefaultOption(templateOption.getDefaultOption());
            newOption.setOutOfStock(optionSettings.outOfStock());
            newOption.setIngredients(optionSettings.ingredients());
            newOption.setAdditives(optionSettings.additives());
            newOption.setNutritionalValuesSize(optionSettings.nutritionalSize());
            newOption.setExtrasJson(null);
            newOption.setDisplayOrder(templateOption.getDisplayOrder());
            RestaurantMenuOption savedOption = restaurantMenuOptionEditorRepository.save(newOption);
            replaceOptionTags(savedOption, optionSettings.tags());
            replaceOptionAllergens(savedOption, optionSettings.allergens());
            replaceOptionNutrition(savedOption, optionSettings.nutritionalValues());
        }

        saveOptionGroupMajorGroup(savedGroup.getId(), getOptionGroupMajorGroup(templateGroupId));
        saveOptionGroupTaxationCategory(savedGroup.getId(), getOptionGroupTaxationCategory(templateGroupId));

        return true;
        }

    @org.springframework.transaction.annotation.Transactional
    public boolean removeOptionGroupFromItem(Long menuVersionId, Long itemId, Long optionGroupId) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId)
                .orElse(null);
        if (group == null) {
            return false;
        }
        if (!menuVersionId.equals(group.getMenuVersionId()) || !itemId.equals(group.getItemId())) {
            throw new IllegalStateException("Choice group is not linked to this item");
        }

        List<RestaurantMenuOption> options = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
        for (RestaurantMenuOption option : options) {
            deleteOptionSettings(option.getId());
        }
        restaurantMenuOptionEditorRepository.deleteAll(options);
        restaurantMenuOptionGroupEditorRepository.delete(group);
        return true;
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean removeOptionGroupFromCategory(Long menuVersionId, Long categoryId, Long optionGroupId) {
        RestaurantMenuOptionGroup group = restaurantMenuOptionGroupEditorRepository.findById(optionGroupId)
                .orElse(null);
        if (group == null) {
            return false;
        }
        if (!menuVersionId.equals(group.getMenuVersionId())
                || !categoryId.equals(group.getCategoryId())
                || group.getItemId() != null
                || group.getItemSizeId() != null) {
            throw new IllegalStateException("Choice group is not linked to this category");
        }

        List<RestaurantMenuOption> options = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
        for (RestaurantMenuOption option : options) {
            deleteOptionSettings(option.getId());
        }
        restaurantMenuOptionEditorRepository.deleteAll(options);
        restaurantMenuOptionGroupEditorRepository.delete(group);
        return true;
    }

    private String buildGroupUniqKey(RestaurantMenuOptionGroup group) {
        if (group.getSourceGroupId() != null) {
            return "src:" + group.getSourceGroupId();
        }
        return "name:"
                + (group.getName() == null ? "" : group.getName().trim().toLowerCase())
                + "|min:" + group.getForceMin()
                + "|max:" + group.getForceMax()
                + "|req:" + group.getRequiredSelection();
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuCategory saveCategory(RestaurantMenuCategory category) {
        return restaurantMenuCategoryEditorRepository.save(category);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuItemSize saveItemSize(RestaurantMenuItemSize itemSize) {
        Long targetItemId = itemSize.getItemId();
        RestaurantMenuItem item = restaurantMenuItemEditorRepository.findById(targetItemId)
            .orElseThrow(() -> new IllegalStateException("Item not found: " + targetItemId));
        if (!item.getMenuVersionId().equals(itemSize.getMenuVersionId())) {
            throw new IllegalStateException("Size belongs to a different menu version");
        }

        Double inputAbsolutePrice = itemSize.getPrice();
        double absolutePrice = roundCurrency(inputAbsolutePrice != null ? inputAbsolutePrice : 0d);

        if (Boolean.TRUE.equals(itemSize.getDefaultSize())) {
            clearDefaultSizeForItem(itemSize.getMenuVersionId(), itemSize.getItemId(), itemSize.getId());
        }

        if (itemSize.getDisplayOrder() == null || itemSize.getDisplayOrder() <= 0) {
            int nextDisplayOrder = restaurantMenuItemSizeEditorRepository
                    .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(itemSize.getMenuVersionId(), itemSize.getItemId())
                    .stream()
                    .map(RestaurantMenuItemSize::getDisplayOrder)
                    .filter(order -> order != null)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
            itemSize.setDisplayOrder(nextDisplayOrder);
        }

        if (itemSize.getId() == null) {
            // Temporary value; it will be replaced by rebased delta after save.
            itemSize.setPrice(0d);
            itemSize = restaurantMenuItemSizeEditorRepository.save(itemSize);
        } else {
            // Persist non-price edits (name, pre-selected flag, etc.) before rebase updates deltas.
            itemSize = restaurantMenuItemSizeEditorRepository.save(itemSize);
        }

        List<RestaurantMenuItemSize> currentSizes = restaurantMenuItemSizeEditorRepository
                .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(itemSize.getMenuVersionId(), itemSize.getItemId());

        Double itemBasePrice = item.getBasePrice();
        double currentBasePrice = roundCurrency(itemBasePrice != null ? itemBasePrice : 0d);
        Map<Long, Double> effectiveAbsolutePrices = new LinkedHashMap<>();
        for (RestaurantMenuItemSize size : currentSizes) {
            Double sizePrice = size.getPrice();
            double sizeDelta = roundCurrency(sizePrice != null ? sizePrice : 0d);
            effectiveAbsolutePrices.put(size.getId(), roundCurrency(currentBasePrice + sizeDelta));
        }
        effectiveAbsolutePrices.put(itemSize.getId(), absolutePrice);

        applyRebasedSizePricing(item, currentSizes, effectiveAbsolutePrices);

        Long savedSizeId = itemSize.getId();
        RestaurantMenuItemSize saved = restaurantMenuItemSizeEditorRepository.findById(savedSizeId)
            .orElseThrow(() -> new IllegalStateException("Saved size not found: " + savedSizeId));
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeItemSize(Long menuVersionId, Long itemId, Long sizeId) {
        RestaurantMenuItemSize size = restaurantMenuItemSizeEditorRepository.findById(sizeId)
                .orElseThrow(() -> new IllegalStateException("Size not found: " + sizeId));
        if (!menuVersionId.equals(size.getMenuVersionId()) || !itemId.equals(size.getItemId())) {
            throw new IllegalStateException("Size belongs to a different item or menu version");
        }

        List<RestaurantMenuOptionGroup> sizeGroups = restaurantMenuOptionGroupEditorRepository
                .findByMenuVersionIdAndItemSizeId(menuVersionId, sizeId);
        for (RestaurantMenuOptionGroup group : sizeGroups) {
            deleteOptionGroupWithOptions(group);
        }

        restaurantMenuItemSizeEditorRepository.delete(size);

        RestaurantMenuItem item = restaurantMenuItemEditorRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Item not found: " + itemId));
        List<RestaurantMenuItemSize> remainingSizes = restaurantMenuItemSizeEditorRepository
                .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, itemId);
        if (remainingSizes.isEmpty()) {
            return;
        }

        Double itemBasePrice = item.getBasePrice();
        double currentBasePrice = roundCurrency(itemBasePrice != null ? itemBasePrice : 0d);
        Map<Long, Double> effectiveAbsolutePrices = new LinkedHashMap<>();
        for (RestaurantMenuItemSize remainingSize : remainingSizes) {
            Double sizePrice = remainingSize.getPrice();
            double sizeDelta = roundCurrency(sizePrice != null ? sizePrice : 0d);
            effectiveAbsolutePrices.put(remainingSize.getId(), roundCurrency(currentBasePrice + sizeDelta));
        }
        applyRebasedSizePricing(item, remainingSizes, effectiveAbsolutePrices);
    }

    private void applyRebasedSizePricing(
            RestaurantMenuItem item,
            List<RestaurantMenuItemSize> sizes,
            Map<Long, Double> effectiveAbsolutePrices) {
        if (sizes == null || sizes.isEmpty() || effectiveAbsolutePrices == null || effectiveAbsolutePrices.isEmpty()) {
            return;
        }

        double newBasePrice = effectiveAbsolutePrices.values().stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0d);
        newBasePrice = roundCurrency(newBasePrice);

        item.setBasePrice(newBasePrice);
        restaurantMenuItemEditorRepository.save(item);

        for (RestaurantMenuItemSize size : sizes) {
            double absolutePrice = roundCurrency(effectiveAbsolutePrices.getOrDefault(size.getId(), newBasePrice));
            size.setPrice(roundCurrency(absolutePrice - newBasePrice));
        }
        restaurantMenuItemSizeEditorRepository.saveAll(sizes);
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveItemSizeOrder(Long menuVersionId, Long itemId, List<Long> orderedSizeIds) {
        List<RestaurantMenuItemSize> currentSizes = restaurantMenuItemSizeEditorRepository
            .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, itemId);
        if (currentSizes.isEmpty()) {
            return;
        }

        Map<Long, RestaurantMenuItemSize> byId = new LinkedHashMap<>();
        for (RestaurantMenuItemSize size : currentSizes) {
            byId.put(size.getId(), size);
        }

        List<RestaurantMenuItemSize> reordered = new ArrayList<>();
        if (orderedSizeIds != null) {
            for (Long id : orderedSizeIds) {
                RestaurantMenuItemSize size = byId.remove(id);
                if (size != null) {
                    reordered.add(size);
                }
            }
        }

        reordered.addAll(byId.values());

        for (int i = 0; i < reordered.size(); i++) {
            RestaurantMenuItemSize size = reordered.get(i);
            size.setDisplayOrder(i + 1);
        }
        restaurantMenuItemSizeEditorRepository.saveAll(reordered);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuItem saveItem(RestaurantMenuItem item) {
        item.setTagsJson(null);
        item.setExtrasJson(null);
        return restaurantMenuItemEditorRepository.save(item);
    }

    public ItemSettingsSnapshot loadItemSettings(RestaurantMenuItem item) {
        Set<String> tags = loadTags(item);
        Set<String> allergens = loadAllergens(item);
        Map<String, String> nutritionalValues = loadNutritionValues(item);

        Boolean itemOutOfStock = item.getOutOfStock();
        boolean outOfStock;
        if (itemOutOfStock == null) {
            outOfStock = readLegacyBoolean(item.getExtrasJson(), "is_out_of_stock", false);
        } else {
            outOfStock = Boolean.TRUE.equals(itemOutOfStock);
        }

        String ingredients = firstNonBlank(
                item.getIngredients(),
                readLegacyText(item.getExtrasJson(), "ingredients"),
                readLegacyText(item.getExtrasJson(), "menu_item_ingredients"));

        String additives = firstNonBlank(
                item.getAdditives(),
                readLegacyText(item.getExtrasJson(), "additives"),
                readLegacyText(item.getExtrasJson(), "menu_item_additives"));

        String nutritionalSize = firstNonBlank(
                item.getNutritionalValuesSize(),
                readLegacyText(item.getExtrasJson(), "menu_item_nutritional_values_size"),
                "per_100g");

        return new ItemSettingsSnapshot(
                outOfStock,
                tags,
                allergens,
                ingredients,
                additives,
                nutritionalSize,
                nutritionalValues);
    }

    @org.springframework.transaction.annotation.Transactional
    public RestaurantMenuItem saveItemWithSettings(
            RestaurantMenuItem item,
            Set<String> tags,
            Set<String> allergens,
            String nutritionalSize,
            Map<String, String> nutritionalValues) {
        item.setTagsJson(null);
        item.setExtrasJson(null);
        item.setNutritionalValuesSize(trimToNull(nutritionalSize));
        RestaurantMenuItem savedItem = restaurantMenuItemEditorRepository.save(item);

        replaceItemTags(savedItem, tags);
        replaceItemAllergens(savedItem, allergens);
        replaceItemNutrition(savedItem, nutritionalValues);

        return savedItem;
    }

    private Set<String> loadTags(RestaurantMenuItem item) {
        Set<String> tags = new LinkedHashSet<>();
        List<RestaurantMenuItemTag> persisted = restaurantMenuItemTagRepository.findByItemIdOrderByDisplayOrderAscIdAsc(item.getId());
        if (!persisted.isEmpty()) {
            persisted.stream()
                    .map(RestaurantMenuItemTag::getTag)
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(tags::add);
            return tags;
        }
        tags.addAll(readLegacyStringArray(item.getTagsJson()));
        return tags;
    }

    private Set<String> loadAllergens(RestaurantMenuItem item) {
        Set<String> allergens = new LinkedHashSet<>();
        List<RestaurantMenuItemAllergen> persisted = restaurantMenuItemAllergenRepository.findByItemIdOrderByDisplayOrderAscIdAsc(item.getId());
        if (!persisted.isEmpty()) {
            persisted.stream()
                    .map(RestaurantMenuItemAllergen::getAllergen)
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(allergens::add);
            return allergens;
        }
        allergens.addAll(readLegacyStringArrayFromObjectField(item.getExtrasJson(), "menu_item_allergens_values"));
        return allergens;
    }

    private Map<String, String> loadNutritionValues(RestaurantMenuItem item) {
        Map<String, String> values = new LinkedHashMap<>();
        List<RestaurantMenuItemNutrition> persisted = restaurantMenuItemNutritionRepository.findByItemIdOrderByDisplayOrderAscIdAsc(item.getId());
        if (!persisted.isEmpty()) {
            for (RestaurantMenuItemNutrition nutrition : persisted) {
                String key = trimToNull(nutrition.getNutritionName());
                String value = trimToNull(nutrition.getNutritionValue());
                if (key != null && value != null) {
                    values.put(key, value);
                }
            }
            return values;
        }

        JsonNode root = readLegacyObject(item.getExtrasJson());
        JsonNode valuesNode = root.path("menu_item_nutritional_values");
        if (!valuesNode.isArray()) {
            return values;
        }
        for (JsonNode node : valuesNode) {
            String key = firstNonBlank(
                    trimToNull(node.path("name").asText(null)),
                    trimToNull(node.path("label").asText(null)),
                    trimToNull(node.path("key").asText(null)));
            String value = trimToNull(node.path("value").asText(null));
            if (key != null && value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private void deleteItemSettings(Long itemId) {
        restaurantMenuItemNutritionRepository.deleteByItemId(itemId);
        restaurantMenuItemAllergenRepository.deleteByItemId(itemId);
        restaurantMenuItemTagRepository.deleteByItemId(itemId);
        deleteSetting(itemTaxationCategorySettingName(itemId));
    }

    private void clearDefaultSizeForItem(Long menuVersionId, Long itemId, Long excludedSizeId) {
        List<RestaurantMenuItemSize> sizes = restaurantMenuItemSizeEditorRepository
            .findByMenuVersionIdAndItemIdOrderByDisplayOrderAscIdAsc(menuVersionId, itemId);
        for (RestaurantMenuItemSize size : sizes) {
            if (excludedSizeId != null && excludedSizeId.equals(size.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(size.getDefaultSize())) {
                size.setDefaultSize(false);
                restaurantMenuItemSizeEditorRepository.save(size);
            }
        }
    }

    private void clearDefaultOptionForGroup(Long optionGroupId, Long excludedOptionId) {
        if (optionGroupId == null) {
            return;
        }
        List<RestaurantMenuOption> options = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(optionGroupId);
        List<RestaurantMenuOption> toUpdate = new ArrayList<>();
        for (RestaurantMenuOption option : options) {
            if (excludedOptionId != null && excludedOptionId.equals(option.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(option.getDefaultOption())) {
                option.setDefaultOption(false);
                toUpdate.add(option);
            }
        }
        if (!toUpdate.isEmpty()) {
            restaurantMenuOptionEditorRepository.saveAll(toUpdate);
        }
    }

    private void deleteOptionGroupWithOptions(RestaurantMenuOptionGroup group) {
        List<RestaurantMenuOption> options = restaurantMenuOptionEditorRepository
                .findByOptionGroupIdOrderByDisplayOrderAscNameAsc(group.getId());
        for (RestaurantMenuOption option : options) {
            deleteOptionSettings(option.getId());
        }
        restaurantMenuOptionEditorRepository.deleteAll(options);
        restaurantMenuOptionGroupEditorRepository.delete(group);
    }

    public OptionSettingsSnapshot loadOptionSettings(RestaurantMenuOption option) {
        Set<String> tags = loadOptionTags(option);
        Set<String> allergens = loadOptionAllergens(option);
        Map<String, String> nutritionalValues = loadOptionNutritionValues(option);

        Boolean optionOutOfStock = option.getOutOfStock();
        boolean outOfStock;
        if (optionOutOfStock == null) {
            outOfStock = readLegacyBoolean(option.getExtrasJson(), "is_out_of_stock", false);
        } else {
            outOfStock = Boolean.TRUE.equals(optionOutOfStock);
        }

        String ingredients = firstNonBlank(
                option.getIngredients(),
                readLegacyText(option.getExtrasJson(), "ingredients"),
                readLegacyText(option.getExtrasJson(), "menu_item_ingredients"));

        String additives = firstNonBlank(
                option.getAdditives(),
                readLegacyText(option.getExtrasJson(), "additives"),
                readLegacyText(option.getExtrasJson(), "menu_item_additives"));

        String nutritionalSize = firstNonBlank(
                option.getNutritionalValuesSize(),
                readLegacyText(option.getExtrasJson(), "menu_item_nutritional_values_size"),
                "per_100g");

        return new OptionSettingsSnapshot(
                outOfStock,
                tags,
                allergens,
                ingredients,
                additives,
                nutritionalSize,
                nutritionalValues);
    }

    private Set<String> loadOptionTags(RestaurantMenuOption option) {
        Set<String> tags = new LinkedHashSet<>();
        List<RestaurantMenuOptionTag> persisted = restaurantMenuOptionTagRepository.findByOptionIdOrderByDisplayOrderAscIdAsc(option.getId());
        if (!persisted.isEmpty()) {
            persisted.stream()
                    .map(RestaurantMenuOptionTag::getTag)
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(tags::add);
            return tags;
        }
        tags.addAll(readLegacyStringArrayFromObjectField(option.getExtrasJson(), "tags"));
        return tags;
    }

    private Set<String> loadOptionAllergens(RestaurantMenuOption option) {
        Set<String> allergens = new LinkedHashSet<>();
        List<RestaurantMenuOptionAllergen> persisted = restaurantMenuOptionAllergenRepository.findByOptionIdOrderByDisplayOrderAscIdAsc(option.getId());
        if (!persisted.isEmpty()) {
            persisted.stream()
                    .map(RestaurantMenuOptionAllergen::getAllergen)
                    .map(this::trimToNull)
                    .filter(value -> value != null)
                    .forEach(allergens::add);
            return allergens;
        }
        allergens.addAll(readLegacyStringArrayFromObjectField(option.getExtrasJson(), "menu_item_allergens_values"));
        return allergens;
    }

    private Map<String, String> loadOptionNutritionValues(RestaurantMenuOption option) {
        Map<String, String> values = new LinkedHashMap<>();
        List<RestaurantMenuOptionNutrition> persisted = restaurantMenuOptionNutritionRepository.findByOptionIdOrderByDisplayOrderAscIdAsc(option.getId());
        if (!persisted.isEmpty()) {
            for (RestaurantMenuOptionNutrition nutrition : persisted) {
                String key = trimToNull(nutrition.getNutritionName());
                String value = trimToNull(nutrition.getNutritionValue());
                if (key != null && value != null) {
                    values.put(key, value);
                }
            }
            return values;
        }

        JsonNode root = readLegacyObject(option.getExtrasJson());
        JsonNode valuesNode = root.path("menu_item_nutritional_values");
        if (!valuesNode.isArray()) {
            return values;
        }
        for (JsonNode node : valuesNode) {
            String key = firstNonBlank(
                    trimToNull(node.path("name").asText(null)),
                    trimToNull(node.path("label").asText(null)),
                    trimToNull(node.path("key").asText(null)));
            String value = trimToNull(node.path("value").asText(null));
            if (key != null && value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private void deleteOptionSettings(Long optionId) {
        restaurantMenuOptionNutritionRepository.deleteByOptionId(optionId);
        restaurantMenuOptionAllergenRepository.deleteByOptionId(optionId);
        restaurantMenuOptionTagRepository.deleteByOptionId(optionId);
    }

    private void replaceOptionTags(RestaurantMenuOption option, Set<String> tags) {
        restaurantMenuOptionTagRepository.deleteByOptionId(option.getId());
        if (tags == null || tags.isEmpty()) {
            return;
        }
        int order = 0;
        for (String value : tags) {
            String tag = trimToNull(value);
            if (tag == null) {
                continue;
            }
            RestaurantMenuOptionTag optionTag = new RestaurantMenuOptionTag();
            optionTag.setMenuVersionId(option.getMenuVersionId());
            optionTag.setOptionId(option.getId());
            optionTag.setTag(tag);
            optionTag.setDisplayOrder(order++);
            restaurantMenuOptionTagRepository.save(optionTag);
        }
    }

    private void replaceOptionAllergens(RestaurantMenuOption option, Set<String> allergens) {
        restaurantMenuOptionAllergenRepository.deleteByOptionId(option.getId());
        if (allergens == null || allergens.isEmpty()) {
            return;
        }
        int order = 0;
        for (String value : allergens) {
            String allergen = trimToNull(value);
            if (allergen == null) {
                continue;
            }
            RestaurantMenuOptionAllergen optionAllergen = new RestaurantMenuOptionAllergen();
            optionAllergen.setMenuVersionId(option.getMenuVersionId());
            optionAllergen.setOptionId(option.getId());
            optionAllergen.setAllergen(allergen);
            optionAllergen.setDisplayOrder(order++);
            restaurantMenuOptionAllergenRepository.save(optionAllergen);
        }
    }

    private void replaceOptionNutrition(RestaurantMenuOption option, Map<String, String> nutritionalValues) {
        restaurantMenuOptionNutritionRepository.deleteByOptionId(option.getId());
        if (nutritionalValues == null || nutritionalValues.isEmpty()) {
            return;
        }
        int order = 0;
        for (Map.Entry<String, String> entry : nutritionalValues.entrySet()) {
            String name = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (name == null || value == null) {
                continue;
            }
            RestaurantMenuOptionNutrition optionNutrition = new RestaurantMenuOptionNutrition();
            optionNutrition.setMenuVersionId(option.getMenuVersionId());
            optionNutrition.setOptionId(option.getId());
            optionNutrition.setNutritionName(name);
            optionNutrition.setNutritionValue(value);
            optionNutrition.setDisplayOrder(order++);
            restaurantMenuOptionNutritionRepository.save(optionNutrition);
        }
    }

    private void replaceItemTags(RestaurantMenuItem item, Set<String> tags) {
        restaurantMenuItemTagRepository.deleteByItemId(item.getId());
        if (tags == null || tags.isEmpty()) {
            return;
        }
        int order = 0;
        for (String value : tags) {
            String tag = trimToNull(value);
            if (tag == null) {
                continue;
            }
            RestaurantMenuItemTag itemTag = new RestaurantMenuItemTag();
            itemTag.setMenuVersionId(item.getMenuVersionId());
            itemTag.setItemId(item.getId());
            itemTag.setTag(tag);
            itemTag.setDisplayOrder(order++);
            restaurantMenuItemTagRepository.save(itemTag);
        }
    }

    private void replaceItemAllergens(RestaurantMenuItem item, Set<String> allergens) {
        restaurantMenuItemAllergenRepository.deleteByItemId(item.getId());
        if (allergens == null || allergens.isEmpty()) {
            return;
        }
        int order = 0;
        for (String value : allergens) {
            String allergen = trimToNull(value);
            if (allergen == null) {
                continue;
            }
            RestaurantMenuItemAllergen itemAllergen = new RestaurantMenuItemAllergen();
            itemAllergen.setMenuVersionId(item.getMenuVersionId());
            itemAllergen.setItemId(item.getId());
            itemAllergen.setAllergen(allergen);
            itemAllergen.setDisplayOrder(order++);
            restaurantMenuItemAllergenRepository.save(itemAllergen);
        }
    }

    private void replaceItemNutrition(RestaurantMenuItem item, Map<String, String> nutritionalValues) {
        restaurantMenuItemNutritionRepository.deleteByItemId(item.getId());
        if (nutritionalValues == null || nutritionalValues.isEmpty()) {
            return;
        }
        int order = 0;
        for (Map.Entry<String, String> entry : nutritionalValues.entrySet()) {
            String name = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (name == null || value == null) {
                continue;
            }
            RestaurantMenuItemNutrition itemNutrition = new RestaurantMenuItemNutrition();
            itemNutrition.setMenuVersionId(item.getMenuVersionId());
            itemNutrition.setItemId(item.getId());
            itemNutrition.setNutritionName(name);
            itemNutrition.setNutritionValue(value);
            itemNutrition.setDisplayOrder(order++);
            restaurantMenuItemNutritionRepository.save(itemNutrition);
        }
    }

    private Set<String> readLegacyStringArray(String jsonArrayText) {
        Set<String> values = new LinkedHashSet<>();
        if (jsonArrayText == null || jsonArrayText.trim().isEmpty()) {
            return values;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(jsonArrayText);
            if (node.isArray()) {
                for (JsonNode valueNode : node) {
                    String value = trimToNull(valueNode.asText());
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
        } catch (JsonProcessingException ignored) {
            // Ignore malformed legacy values to keep editing available.
        }
        return values;
    }

    private Set<String> readLegacyStringArrayFromObjectField(String jsonObjectText, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        JsonNode root = readLegacyObject(jsonObjectText);
        JsonNode node = root.path(fieldName);
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode valueNode : node) {
            String value = trimToNull(valueNode.asText());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean readLegacyBoolean(String jsonObjectText, String fieldName, boolean defaultValue) {
        JsonNode root = readLegacyObject(jsonObjectText);
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        return node.asBoolean(defaultValue);
    }

    private String readLegacyText(String jsonObjectText, String fieldName) {
        JsonNode root = readLegacyObject(jsonObjectText);
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return trimToNull(node.asText());
    }

    private JsonNode readLegacyObject(String jsonObjectText) {
        if (jsonObjectText == null || jsonObjectText.trim().isEmpty()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(jsonObjectText);
            if (node.isObject()) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // Ignore malformed legacy values to keep editing available.
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private String firstNonBlank(String... values) {
        for (String candidate : values) {
            String value = trimToNull(candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isPullLocked(Long restaurantId) {
        return restaurantMenuImportService.isPullLocked(restaurantId);
    }

    public int resetMenuVersions(Long restaurantId) {
        return restaurantMenuImportService.resetMenuVersions(restaurantId);
    }
}
