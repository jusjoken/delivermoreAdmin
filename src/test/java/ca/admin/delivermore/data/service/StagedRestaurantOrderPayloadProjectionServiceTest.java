package ca.admin.delivermore.data.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrder;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLine;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderLineOption;
import ca.admin.delivermore.collector.data.entity.StagedRestaurantOrderTaxRate;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineOptionRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderLineRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderRepository;
import ca.admin.delivermore.collector.data.service.StagedRestaurantOrderTaxRateRepository;

@ExtendWith(MockitoExtension.class)
class StagedRestaurantOrderPayloadProjectionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Mock
    private StagedRestaurantOrderRepository stagedRestaurantOrderRepository;

    @Mock
    private StagedRestaurantOrderLineRepository stagedRestaurantOrderLineRepository;

    @Mock
    private StagedRestaurantOrderLineOptionRepository stagedRestaurantOrderLineOptionRepository;

    @Mock
    private StagedRestaurantOrderTaxRateRepository stagedRestaurantOrderTaxRateRepository;

    @InjectMocks
    private StagedRestaurantOrderPayloadProjectionService service;

    @Test
    void buildPayloadJson_projectsStructuredSnapshotData() throws Exception {
        Long stagedOrderId = 101L;

        StagedRestaurantOrder order = new StagedRestaurantOrder();
        order.setId(stagedOrderId);
        order.setRestaurantId(77L);
        order.setRestaurantName("Demo Restaurant");
        order.setMenuVersionId(5L);
        order.setContactName("Taylor");
        order.setContactEmail("taylor@example.com");
        order.setContactPhone("555-1111");
        order.setStreetAddress("123 Main");
        order.setCity("Smalltown");
        order.setPostalCode("A1A1A1");
        order.setPaymentMethod("cash");
        order.setInPersonDelivery(false);
        order.setCustomerComments("Leave at door");
        order.setSubtotal(10.0);
        order.setServiceFee(1.0);
        order.setGst(0.5);
        order.setItemTax(0.4);
        order.setDeliveryFee(2.0);
        order.setDeliveryFeeTax(0.1);
        order.setTotal(14.0);

        StagedRestaurantOrderLine line = new StagedRestaurantOrderLine();
        line.setId(1001L);
        line.setStagedOrderId(stagedOrderId);
        line.setLineNumber(1);
        line.setItemId(9001L);
        line.setItemName("Burger");
        line.setSelectedSizeName("Large");
        line.setQuantity(2);
        line.setUnitPrice(5.0);
        line.setInstructions("No onions");
        line.setPrimaryTaxationCategory("Food");

        StagedRestaurantOrderLineOption option = new StagedRestaurantOrderLineOption();
        option.setStagedOrderId(stagedOrderId);
        option.setStagedOrderLineId(1001L);
        option.setLineNumber(1);
        option.setGroupName("Add-ons");
        option.setOptionName("Cheese");
        option.setUnitPrice(1.25);
        option.setQuantity(1);
        option.setTaxationCategory("Food");

        StagedRestaurantOrderTaxRate foodRate = new StagedRestaurantOrderTaxRate();
        foodRate.setStagedOrderId(stagedOrderId);
        foodRate.setTaxationCategory("Food");
        foodRate.setRatePercent(5.0);

        when(stagedRestaurantOrderRepository.findById(stagedOrderId)).thenReturn(java.util.Optional.of(order));
        when(stagedRestaurantOrderLineRepository.findByStagedOrderIdOrderByLineNumberAsc(stagedOrderId))
                .thenReturn(List.of(line));
        when(stagedRestaurantOrderLineOptionRepository.findByStagedOrderIdOrderByLineNumberAscIdAsc(stagedOrderId))
                .thenReturn(List.of(option));
        when(stagedRestaurantOrderTaxRateRepository.findByStagedOrderIdOrderByTaxationCategoryAsc(stagedOrderId))
                .thenReturn(List.of(foodRate));

        String payloadJson = service.buildPayloadJson(stagedOrderId);
        JsonNode root = OBJECT_MAPPER.readTree(payloadJson);

        assertEquals(77L, root.get("restaurantId").asLong());
        assertEquals("Demo Restaurant", root.get("restaurantName").asText());
        assertEquals("Taylor", root.get("contactName").asText());
        assertEquals(14.0, root.get("total").asDouble(), 0.0001);

        JsonNode lines = root.get("lines");
        assertEquals(1, lines.size());
        assertEquals("Burger", lines.get(0).get("itemName").asText());
        assertEquals("No onions", lines.get(0).get("instructions").asText());
        assertEquals(2, lines.get(0).get("quantity").asInt());

        JsonNode selections = lines.get(0).get("selections");
        assertEquals(1, selections.size());
        assertEquals("Add-ons", selections.get(0).get("groupName").asText());
        assertEquals("Cheese", selections.get(0).get("optionName").asText());

        assertTrue(root.get("taxationRates").has("Food"));
        assertEquals(5.0, root.get("taxationRates").get("Food").asDouble(), 0.0001);
    }
}