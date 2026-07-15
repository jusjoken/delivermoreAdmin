package ca.admin.delivermore.views.utility;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.admin.delivermore.data.service.StagedRestaurantOrderPayloadProjectionService;

@RestController
@RequestMapping("/api/staged-orders")
public class StagedOrderPayloadController {

    private final StagedRestaurantOrderPayloadProjectionService payloadProjectionService;

    public StagedOrderPayloadController(StagedRestaurantOrderPayloadProjectionService payloadProjectionService) {
        this.payloadProjectionService = payloadProjectionService;
    }

    @GetMapping(value = "/{stagedOrderId}/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getProjectedPayload(@PathVariable Long stagedOrderId) {
        try {
            String payload = payloadProjectionService.buildPayloadJson(stagedOrderId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}