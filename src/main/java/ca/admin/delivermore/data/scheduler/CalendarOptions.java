
package ca.admin.delivermore.data.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import elemental.json.Json;
import elemental.json.JsonObject;
import jakarta.annotation.Generated;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "eventTimeFormat",
    "slotDuration"
})
@Generated("jsonschema2pojo")
public class CalendarOptions {

    @JsonProperty("eventTimeFormat")
    private EventTimeFormat eventTimeFormat;
    @JsonProperty("slotDuration")
    private String slotDuration;

    @JsonProperty("eventMinWidth")
    private String eventMinWidth;

    @JsonProperty("slotLabelInterval")
    private String slotLabelInterval;

    @JsonProperty("initialView")
    private String initialView;

    @JsonProperty("headerToolbar")
    private String headerToolbar;

    @JsonProperty("slotLabelFormat")
    private SlotLabelFormat slotLabelFormat;

    @JsonProperty("resourceGroupField")
    private String resourceGroupField;

    @JsonProperty("resourceAreaWidth")
    private String resourceAreaWidth;

    @JsonProperty("resources")
    private List<SchedulerResource> resources;

    @JsonProperty("businessHours")
    private boolean businessHours = false;

    private Logger log = LoggerFactory.getLogger(CalendarOptions.class);

    @JsonProperty("eventTimeFormat")
    public EventTimeFormat getEventTimeFormat() {
        return eventTimeFormat;
    }

    @JsonProperty("eventTimeFormat")
    public void setEventTimeFormat(EventTimeFormat eventTimeFormat) {
        this.eventTimeFormat = eventTimeFormat;
    }

    @JsonProperty("slotDuration")
    public String getSlotDuration() {
        return slotDuration;
    }

    @JsonProperty("slotDuration")
    public void setSlotDuration(String slotDuration) {
        this.slotDuration = slotDuration;
    }

    public String getEventMinWidth() {
        return eventMinWidth;
    }

    public void setEventMinWidth(String eventMinWidth) {
        this.eventMinWidth = eventMinWidth;
    }

    public String getSlotLabelInterval() {
        return slotLabelInterval;
    }

    public void setSlotLabelInterval(String slotLabelInterval) {
        this.slotLabelInterval = slotLabelInterval;
    }

    public String getInitialView() {
        return initialView;
    }

    public void setInitialView(String initialView) {
        this.initialView = initialView;
    }

    public String getHeaderToolbar() {
        return headerToolbar;
    }

    public void setHeaderToolbar(String headerToolbar) {
        this.headerToolbar = headerToolbar;
    }

    public SlotLabelFormat getSlotLabelFormat() {
        return slotLabelFormat;
    }

    public void setSlotLabelFormat(SlotLabelFormat slotLabelFormat) {
        this.slotLabelFormat = slotLabelFormat;
    }

    public String getResourceGroupField() {
        return resourceGroupField;
    }

    public void setResourceGroupField(String resourceGroupField) {
        this.resourceGroupField = resourceGroupField;
    }

    public List<SchedulerResource> getResources() {
        return resources;
    }

    public void setResources(List<SchedulerResource> resources) {
        this.resources = resources;
    }

    public boolean isBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(boolean businessHours) {
        this.businessHours = businessHours;
    }

    public String getResourceAreaWidth() {
        return resourceAreaWidth;
    }

    public void setResourceAreaWidth(String resourceAreaWidth) {
        this.resourceAreaWidth = resourceAreaWidth;
    }

    @JsonIgnore
    public JsonObject getJsonObject(){
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String jsonString = objectMapper.writeValueAsString(this);
            log.info("createDefaultInitialOptions: json from object:" + jsonString);
            if(jsonString!=null){
                return Json.parse(jsonString);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @JsonIgnore
    public tools.jackson.databind.node.ObjectNode getObjectNode() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonString = objectMapper.writeValueAsString(this);
            return (tools.jackson.databind.node.ObjectNode) new tools.jackson.databind.ObjectMapper().readTree(jsonString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "CalendarOptions{" +
                "eventTimeFormat=" + eventTimeFormat +
                ", slotDuration='" + slotDuration + '\'' +
                ", eventMinWidth='" + eventMinWidth + '\'' +
                ", slotLabelInterval='" + slotLabelInterval + '\'' +
                ", initialView='" + initialView + '\'' +
                ", headerToolbar='" + headerToolbar + '\'' +
                ", slotLabelFormat=" + slotLabelFormat +
                '}';
    }
}
