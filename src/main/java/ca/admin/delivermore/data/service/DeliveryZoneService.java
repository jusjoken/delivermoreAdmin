package ca.admin.delivermore.data.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.entity.SettingEntity;
import ca.admin.delivermore.collector.data.service.SettingRepository;
import ca.admin.delivermore.collector.data.service.TeamsRepository;
import ca.admin.delivermore.collector.data.tookan.Team;

@Service
public class DeliveryZoneService {

    public record DeliveryZoneConfig(String name, Double maxDistanceKm, Double fee, boolean active, String color) {
    }

    public record BaseLocation(Long teamId, String teamName, String address, Double latitude, Double longitude) {
        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }

        public String summary() {
            StringBuilder text = new StringBuilder(teamName == null ? "Base location" : teamName);
            if (address != null && !address.isBlank()) {
                text.append(" | ").append(address.trim());
            }
            if (hasCoordinates()) {
                text.append(" | ")
                        .append(String.format(Locale.CANADA, "%.5f, %.5f", latitude, longitude));
            }
            return text.toString();
        }
    }

    public record DeliveryQuote(
            boolean zonesConfigured,
            boolean matched,
            double deliveryFee,
            Double distanceKm,
            Integer driveMinutesToCustomer,
            String zoneName,
            String message) {
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DELIVERY_ZONE_SECTION = "delivery_zones";
    private static final String SETTING_PREFIX = "team_";

    private final SettingRepository settingRepository;
    private final TeamsRepository teamsRepository;

    public DeliveryZoneService(SettingRepository settingRepository, TeamsRepository teamsRepository) {
        this.settingRepository = settingRepository;
        this.teamsRepository = teamsRepository;
    }

    public List<DeliveryZoneConfig> listZonesForTeam(Long teamId) {
        if (teamId == null) {
            return List.of();
        }

        SettingEntity setting = settingRepository.findBySectionAndName(DELIVERY_ZONE_SECTION, settingName(teamId));
        if (setting == null || setting.getValue() == null || setting.getValue().isBlank()) {
            return List.of();
        }

        try {
            List<DeliveryZoneConfig> parsed = OBJECT_MAPPER.readValue(
                    setting.getValue(),
                    new TypeReference<List<DeliveryZoneConfig>>() {
                    });
            return parsed == null
                    ? List.of()
                    : parsed.stream()
                            .filter(zone -> zone != null)
                            .sorted(Comparator.comparing(zone -> normalizedPositive(zone.maxDistanceKm())))
                            .toList();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse delivery zones for team " + teamId, ex);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void saveZonesForTeam(Long teamId, List<DeliveryZoneConfig> zones) {
        if (teamId == null) {
            throw new IllegalArgumentException("Team is required");
        }

        List<DeliveryZoneConfig> normalized = normalizeZones(zones);
        SettingEntity setting = settingRepository.findBySectionAndName(DELIVERY_ZONE_SECTION, settingName(teamId));
        if (setting == null) {
            setting = new SettingEntity();
            setting.setSection(DELIVERY_ZONE_SECTION);
            setting.setName(settingName(teamId));
            setting.setDescription("Delivery zones for team/base location " + teamId);
            setting.setValueType(SettingEntity.ValueType.STRING);
        }

        try {
            setting.setValue(OBJECT_MAPPER.writeValueAsString(normalized));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to save delivery zones", ex);
        }
        settingRepository.save(setting);
    }

    public Optional<BaseLocation> getBaseLocation(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }

        Team team = teamsRepository.findByTeamId(teamId);
        if (team == null) {
            return Optional.empty();
        }

        return Optional.of(new BaseLocation(
                team.getTeamId(),
                team.getTeamName(),
                team.getAddress(),
                parseDouble(team.getLatitude()),
                parseDouble(team.getLongitude())));
    }

    public DeliveryQuote quoteForRestaurant(Restaurant restaurant, Double customerLatitude, Double customerLongitude) {
        if (restaurant == null) {
            return new DeliveryQuote(false, false, 0d, null, null, null, "Restaurant was not found");
        }

        List<DeliveryZoneConfig> activeZones = listZonesForTeam(restaurant.getTeamId()).stream()
                .filter(DeliveryZoneConfig::active)
                .sorted(Comparator.comparing(zone -> normalizedPositive(zone.maxDistanceKm())))
                .toList();

        if (activeZones.isEmpty()) {
            return new DeliveryQuote(false, false, 0d, null, null, null, "Delivery zones must be configured before checkout can work.");
        }

        BaseLocation baseLocation = getBaseLocation(restaurant.getTeamId())
                .orElse(null);
        if (baseLocation == null || !baseLocation.hasCoordinates()) {
            return new DeliveryQuote(true, false, 0d, null, null, null, "The base location is missing map coordinates.");
        }
        if (customerLatitude == null || customerLongitude == null) {
            return new DeliveryQuote(true, false, 0d, null, null, null, "Confirm the delivery location on the map first.");
        }

        double distanceKm = round2(distanceKm(baseLocation.latitude(), baseLocation.longitude(), customerLatitude, customerLongitude));
        Integer estimatedMinutes = estimateDeliveryMinutes(distanceKm);
        for (DeliveryZoneConfig zone : activeZones) {
            if (distanceKm <= normalizedPositive(zone.maxDistanceKm()) + 0.0001d) {
                return new DeliveryQuote(
                        true,
                        true,
                        round2(normalizedNonNegative(zone.fee())),
                        distanceKm,
                        estimatedMinutes,
                        normalizedName(zone.name()),
                        "Matched delivery zone " + normalizedName(zone.name()) + ".");
            }
        }

        return new DeliveryQuote(
                true,
                false,
                0d,
                distanceKm,
                estimatedMinutes,
                null,
                "This address is outside all configured delivery zones.");
    }

    public Double distanceToRestaurantKm(Restaurant restaurant, Double customerLatitude, Double customerLongitude) {
        if (restaurant == null
                || restaurant.getLocationLatitude() == null
                || restaurant.getLocationLongitude() == null
                || customerLatitude == null
                || customerLongitude == null) {
            return null;
        }

        return round2(distanceKm(
                restaurant.getLocationLatitude(),
                restaurant.getLocationLongitude(),
                customerLatitude,
                customerLongitude));
    }

    private List<DeliveryZoneConfig> normalizeZones(List<DeliveryZoneConfig> zones) {
        if (zones == null) {
            return List.of();
        }

        List<DeliveryZoneConfig> normalized = new ArrayList<>();
        for (DeliveryZoneConfig zone : zones) {
            if (zone == null) {
                continue;
            }
            String name = normalizedName(zone.name());
            Double maxDistanceKm = normalizedPositive(zone.maxDistanceKm());
            Double fee = normalizedNonNegative(zone.fee());
            if (name.isBlank() || maxDistanceKm <= 0d) {
                continue;
            }
            normalized.add(new DeliveryZoneConfig(name, round2(maxDistanceKm), round2(fee), zone.active(), normalizedColor(zone.color())));
        }

        normalized.sort(Comparator.comparing(DeliveryZoneConfig::maxDistanceKm));
        return normalized;
    }

    private String settingName(Long teamId) {
        return SETTING_PREFIX + teamId;
    }

    private String normalizedName(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizedColor(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private double normalizedPositive(Double value) {
        if (value == null) {
            return 0d;
        }
        return Math.max(0d, value);
    }

    private double normalizedNonNegative(Double value) {
        if (value == null) {
            return 0d;
        }
        return Math.max(0d, value);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0088d;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2d) * Math.sin(latDistance / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2d) * Math.sin(lonDistance / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthRadiusKm * c;
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private Integer estimateDeliveryMinutes(double distanceKm) {
        return Math.max(1, (int) Math.round(distanceKm * 3d));
    }
}