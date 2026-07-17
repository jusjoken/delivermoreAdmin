package ca.admin.delivermore.data.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLine;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLineOption;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderTaxRate;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineOptionRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderTaxRateRepository;

@Service
public class StagedRestaurantOrderPayloadProjectionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StagedRestaurantOrderRepository stagedRestaurantOrderRepository;
    private final StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository;
    private final StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository;
    private final StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository;

    public StagedRestaurantOrderPayloadProjectionService(
            StagedRestaurantOrderRepository stagedRestaurantOrderRepository,
            StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository,
            StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository,
            StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository) {
        this.stagedRestaurantOrderRepository = stagedRestaurantOrderRepository;
        this.stagedRestaurantOrderLineRepository = stagedRestaurantOrderLineRepository;
        this.stagedRestaurantOrderLineOptionRepository = stagedRestaurantOrderLineOptionRepository;
        this.stagedRestaurantOrderTaxRateRepository = stagedRestaurantOrderTaxRateRepository;
    }

    public String buildPayloadJson(Long stagedOrderId) {
        StagedRestaurantOrder stagedOrder = stagedRestaurantOrderRepository.findById(stagedOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Staged order not found: " + stagedOrderId));

        List<StagedRestaurantOrderLine> lines = stagedRestaurantOrderLineRepository
                .findByStagedOrderIdOrderByLineNumberAsc(stagedOrderId);
        List<StagedRestaurantOrderLineOption> options = stagedRestaurantOrderLineOptionRepository
                .findByStagedOrderIdOrderByLineNumberAscIdAsc(stagedOrderId);
        List<StagedRestaurantOrderTaxRate> taxRates = stagedRestaurantOrderTaxRateRepository
                .findByStagedOrderIdOrderByTaxationCategoryAsc(stagedOrderId);

        Map<Long, List<StagedRestaurantOrderLineOption>> optionsByLineId = new LinkedHashMap<>();
        for (StagedRestaurantOrderLineOption option : options) {
            optionsByLineId.computeIfAbsent(option.getStagedOrderLineId(), unused -> new ArrayList<>())
                    .add(option);
        }

        List<LineItemPayload> linePayloads = new ArrayList<>();
        for (StagedRestaurantOrderLine line : lines) {
            List<SelectedOptionPayload> optionPayloads = new ArrayList<>();
            for (StagedRestaurantOrderLineOption option : optionsByLineId.getOrDefault(line.getId(), List.of())) {
                optionPayloads.add(new SelectedOptionPayload(
                        option.getGroupName(),
                        option.getOptionName(),
                        defaultDouble(option.getUnitPrice()),
                        defaultInt(option.getQuantity()),
                        trimToEmpty(option.getTaxationCategory())));
            }

            linePayloads.add(new LineItemPayload(
                    line.getItemId(),
                    line.getItemName(),
                    trimToEmpty(line.getSelectedSizeName()),
                    defaultInt(line.getQuantity()),
                    defaultDouble(line.getUnitPrice()),
                    trimToEmpty(line.getInstructions()),
                    trimToEmpty(line.getPrimaryTaxationCategory()),
                    optionPayloads));
        }

        Map<String, Double> taxationRates = new LinkedHashMap<>();
        Map<String, String> taxationRateNames = new LinkedHashMap<>();
        Map<String, String> categoryKeyByTaxIdentity = new LinkedHashMap<>();
        String serviceFeeTaxName = "";
        double serviceFeeTaxRate = 0d;
        String deliveryFeeTaxName = "";
        double deliveryFeeTaxRate = 0d;
        for (StagedRestaurantOrderTaxRate taxRate : taxRates) {
            String scope = trimToEmpty(taxRate.getTaxScope());
            if ("CATEGORY".equalsIgnoreCase(scope)) {
                String taxName = trimToEmpty(taxRate.getTaxName());
                double ratePercent = defaultDouble(taxRate.getRatePercent());
                String identity = buildTaxIdentityKey(taxName, ratePercent);
                String categoryKey = categoryKeyByTaxIdentity.get(identity);
                if (categoryKey == null) {
                    categoryKey = taxName.isEmpty() ? trimToEmpty(taxRate.getTaxationCategory()) : taxName;
                    categoryKeyByTaxIdentity.put(identity, categoryKey);
                }
                taxationRates.put(categoryKey, ratePercent);
                taxationRateNames.put(categoryKey, taxName);
            } else if ("SERVICE_FEE".equalsIgnoreCase(scope)) {
                serviceFeeTaxName = trimToEmpty(taxRate.getTaxName());
                serviceFeeTaxRate = defaultDouble(taxRate.getRatePercent());
            } else if ("DELIVERY_FEE".equalsIgnoreCase(scope)) {
                deliveryFeeTaxName = trimToEmpty(taxRate.getTaxName());
                deliveryFeeTaxRate = defaultDouble(taxRate.getRatePercent());
            }
        }

        StoredOrderPayload payload = new StoredOrderPayload(
                stagedOrder.getRestaurantId(),
                stagedOrder.getRestaurantName(),
                stagedOrder.getMenuVersionId(),
                stagedOrder.getContactName(),
                trimToEmpty(stagedOrder.getContactEmail()),
                stagedOrder.getContactPhone(),
                stagedOrder.getStreetAddress(),
                stagedOrder.getCity(),
                trimToEmpty(stagedOrder.getPostalCode()),
                stagedOrder.getPaymentMethod(),
                stagedOrder.getInPersonDelivery(),
                trimToEmpty(stagedOrder.getCustomerComments()),
                linePayloads,
                defaultDouble(stagedOrder.getSubtotal()),
                defaultDouble(stagedOrder.getServiceFee()),
                defaultDouble(stagedOrder.getServiceFeeTax()),
                defaultDouble(stagedOrder.getItemTax()),
                defaultDouble(stagedOrder.getDeliveryFee()),
                defaultDouble(stagedOrder.getDeliveryFeeTax()),
                defaultDouble(stagedOrder.getTip()),
                defaultDouble(stagedOrder.getTotal()),
                taxationRates,
                taxationRateNames,
                serviceFeeTaxName,
                serviceFeeTaxRate,
                deliveryFeeTaxName,
                deliveryFeeTaxRate);

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build staged order payload JSON", ex);
        }
    }

    private double defaultDouble(Double value) {
        return value == null ? 0d : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildTaxIdentityKey(String taxName, double ratePercent) {
        return trimToEmpty(taxName).toLowerCase(java.util.Locale.CANADA)
                + "|"
                + String.format(java.util.Locale.CANADA, "%.4f", ratePercent);
    }

    private record StoredOrderPayload(
            Long restaurantId,
            String restaurantName,
            Long menuVersionId,
            String contactName,
            String contactEmail,
            String contactPhone,
            String street,
            String city,
            String postalCode,
            String paymentMethod,
            boolean inPersonDelivery,
            String comments,
            List<LineItemPayload> lines,
            double subtotal,
            double serviceFee,
            double serviceFeeTax,
            double itemTax,
            double deliveryFee,
            double deliveryFeeTax,
                double tip,
            double total,
            Map<String, Double> taxationRates,
            Map<String, String> taxationRateNames,
            String serviceFeeTaxName,
            double serviceFeeTaxRate,
            String deliveryFeeTaxName,
            double deliveryFeeTaxRate) {
    }

    private record LineItemPayload(
            Long itemId,
            String itemName,
            String selectedSizeName,
            int quantity,
            double unitPrice,
            String instructions,
            String primaryTaxationCategory,
            List<SelectedOptionPayload> selections) {
    }

    private record SelectedOptionPayload(
            String groupName,
            String optionName,
            double unitPrice,
            int quantity,
            String taxationCategory) {
    }
}