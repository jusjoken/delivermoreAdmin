package ca.admin.delivermore.data.service;

import java.time.Duration;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LocationLookupService {

    public record LookupResult(String displayName, Double latitude, Double longitude) {
    }

    private static final Logger log = LoggerFactory.getLogger(LocationLookupService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String searchUrl;

    public LocationLookupService(
            WebClient.Builder webClientBuilder,
            @Value("${app.map.geocoding.search-url}") String searchUrl,
            @Value("${app.map.geocoding.user-agent}") String userAgent) {
        this.webClient = webClientBuilder
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.searchUrl = searchUrl;
    }

    public Optional<LookupResult> geocodeAddress(String address) {
        String normalized = address == null ? "" : address.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        try {
            for (String query : buildCandidateQueries(normalized)) {
                Optional<LookupResult> result = geocodeSingleQuery(query);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("Geocoding failed for address '{}'", normalized, ex);
            return Optional.empty();
        }
    }

    private Optional<LookupResult> geocodeSingleQuery(String query) throws JsonProcessingException {
        String response = webClient.get()
                .uri(buildSearchUri(query))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(8));
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }

        JsonNode root = OBJECT_MAPPER.readTree(response);
        if (!root.isArray() || root.isEmpty()) {
            return Optional.empty();
        }

        JsonNode first = root.get(0);
        Double latitude = parseDouble(first.path("lat").asText(null));
        Double longitude = parseDouble(first.path("lon").asText(null));
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }

        String displayName = first.path("display_name").asText(query);
        return Optional.of(new LookupResult(displayName, latitude, longitude));
    }

    private List<String> buildCandidateQueries(String normalized) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);

        String centreVariant = normalized
                .replace(" center ", " centre ")
                .replace(" Center ", " Centre ")
                .replace(" CENTER ", " CENTRE ");
        variants.add(centreVariant);

        String withoutLeadingNumber = normalized.replaceFirst("^\\s*\\d+[A-Za-z-]*\\s+", "").trim();
        if (!withoutLeadingNumber.isBlank() && !withoutLeadingNumber.equalsIgnoreCase(normalized)) {
            variants.add(withoutLeadingNumber);
            variants.add(withoutLeadingNumber
                    .replace(" center ", " centre ")
                    .replace(" Center ", " Centre ")
                    .replace(" CENTER ", " CENTRE "));
        }

        return new ArrayList<>(variants);
    }

    private java.net.URI buildSearchUri(String address) {
        return UriComponentsBuilder
                .fromUriString(searchUrl)
                .queryParam("q", address)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("countrycodes", "ca")
            .build()
                .toUri();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            log.debug("Could not parse geocoding coordinate '{}'", value.toLowerCase(Locale.CANADA), ex);
            return null;
        }
    }
}