package ca.admin.delivermore.data.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.admin.delivermore.collector.data.entity.CustomerAddress;
import ca.admin.delivermore.collector.data.entity.CustomerProfile;
import ca.admin.delivermore.collector.data.entity.CustomerRestaurantLink;
import ca.admin.delivermore.collector.data.entity.CustomerSourceRecord;
import ca.admin.delivermore.collector.data.entity.Restaurant;
import ca.admin.delivermore.collector.data.service.CustomerAddressRepository;
import ca.admin.delivermore.collector.data.service.CustomerProfileRepository;
import ca.admin.delivermore.collector.data.service.CustomerRestaurantLinkRepository;
import ca.admin.delivermore.collector.data.service.CustomerSourceRecordRepository;

@Service
public class CustomerProfileService {

    private static final String IMPORT_SOURCE = "CSV_IMPORT";
    private static final String CHECKOUT_SOURCE = "CHECKOUT";
    private static final String LOCATION_SOURCE_MAP_CONFIRMED = "MAP_CONFIRMED";
    private static final String LOCATION_SOURCE_PROFILE_SNAPSHOT = "PROFILE_SNAPSHOT";

    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final CustomerSourceRecordRepository customerSourceRecordRepository;
    private final CustomerRestaurantLinkRepository customerRestaurantLinkRepository;

    public CustomerProfileService(
            CustomerAddressRepository customerAddressRepository,
            CustomerProfileRepository customerProfileRepository,
            CustomerSourceRecordRepository customerSourceRecordRepository,
            CustomerRestaurantLinkRepository customerRestaurantLinkRepository) {
        this.customerAddressRepository = customerAddressRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.customerSourceRecordRepository = customerSourceRecordRepository;
        this.customerRestaurantLinkRepository = customerRestaurantLinkRepository;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerPrefill> findPrefillCandidate(String email, String phone) {
        Optional<CustomerProfile> candidate = findProfileByBestMatch(email, phone);
        return candidate.map(profile -> {
            List<CustomerAddressChoice> addresses = customerAddressRepository
                .findByCustomerProfileIdOrderByLastUsedAtDescUpdatedAtDescIdDesc(profile.getId())
                .stream()
                .map(address -> new CustomerAddressChoice(
                    address.getId(),
                    trimToEmpty(address.getLabel()),
                    trimToEmpty(address.getStreetAddress()),
                    trimToEmpty(address.getCity()),
                    trimToEmpty(address.getPostalCode()),
                    address.getLatitude(),
                    address.getLongitude(),
                    address.getLocationConfirmedAt(),
                    trimToEmpty(address.getLocationSource())))
                .collect(Collectors.toList());

            return new CustomerPrefill(
                profile.getId(),
                trimToEmpty(profile.getFirstName()),
                trimToEmpty(profile.getLastName()),
                trimToEmpty(profile.getFullName()),
                trimToEmpty(profile.getEmail()),
                trimToEmpty(profile.getPhone()),
                trimToEmpty(profile.getStreetAddress()),
                trimToEmpty(profile.getCity()),
                trimToEmpty(profile.getPostalCode()),
                addresses);
        });
    }

    @Transactional
    public Long recordCheckoutOrder(
            Long restaurantId,
            String restaurantName,
            String contactName,
            String contactEmail,
            String contactPhone,
            String streetAddress,
            String city,
            String postalCode,
            Double customerLatitude,
            Double customerLongitude,
            double orderTotal,
            LocalDateTime orderAt) {
        LocalDateTime now = orderAt == null ? LocalDateTime.now() : orderAt;
        CustomerProfile profile = findProfileByBestMatch(contactEmail, contactPhone).orElseGet(CustomerProfile::new);

        String previousStreetAddress = trimToEmpty(profile.getStreetAddress());
        String previousCity = trimToEmpty(profile.getCity());
        String previousPostalCode = trimToEmpty(profile.getPostalCode());

        boolean newProfile = profile.getId() == null;
        if (newProfile) {
            profile.setCreatedAt(now);
        }

        profile.setFirstName(deriveFirstName(contactName, profile.getFirstName()));
        profile.setLastName(deriveLastName(contactName, profile.getLastName()));
        profile.setFullName(choosePreferred(contactName, profile.getFullName()));
        profile.setEmail(choosePreferred(contactEmail, profile.getEmail()));
        profile.setNormalizedEmail(normalizeEmail(choosePreferred(contactEmail, profile.getEmail())));
        profile.setPhone(choosePreferred(contactPhone, profile.getPhone()));
        profile.setNormalizedPhone(normalizePhone(choosePreferred(contactPhone, profile.getPhone())));
        profile.setStreetAddress(choosePreferred(streetAddress, profile.getStreetAddress()));
        profile.setCity(choosePreferred(city, profile.getCity()));
        profile.setPostalCode(choosePreferred(postalCode, profile.getPostalCode()));
        profile.setLastRestaurantId(restaurantId);
        profile.setLastRestaurantName(trimToEmpty(restaurantName));
        profile.setUpdatedAt(now);

        profile = customerProfileRepository.save(profile);

        CustomerRestaurantLink link = customerRestaurantLinkRepository
                .findByCustomerProfileIdAndRestaurantId(profile.getId(), restaurantId)
                .orElseGet(CustomerRestaurantLink::new);

        if (link.getId() == null) {
            link.setCustomerProfileId(profile.getId());
            link.setRestaurantId(restaurantId);
            link.setCreatedAt(now);
            link.setOrderCount(0L);
            link.setTotalSpent(0.0);
        }

        link.setRestaurantName(trimToEmpty(restaurantName));
        link.setOrderCount((link.getOrderCount() == null ? 0L : link.getOrderCount()) + 1L);
        link.setTotalSpent(roundCurrency((link.getTotalSpent() == null ? 0.0 : link.getTotalSpent()) + Math.max(0.0, orderTotal)));
        link.setLastOrderAt(now);
        if (link.getFirstOrderAt() == null || now.isBefore(link.getFirstOrderAt())) {
            link.setFirstOrderAt(now);
        }
        link.setLastSource(CHECKOUT_SOURCE);
        link.setUpdatedAt(now);
        customerRestaurantLinkRepository.save(link);

        // Preserve the historical profile address as an address choice before the profile is updated to a new one.
        upsertCustomerAddress(
            profile.getId(),
            previousStreetAddress,
            previousCity,
            previousPostalCode,
            null,
            null,
            LOCATION_SOURCE_PROFILE_SNAPSHOT,
            now);

        upsertCustomerAddress(
            profile.getId(),
            streetAddress,
            city,
            postalCode,
            customerLatitude,
            customerLongitude,
            LOCATION_SOURCE_MAP_CONFIRMED,
            now);

        recomputeProfileAggregates(profile.getId(), now);
        return profile.getId();
    }

    @Transactional
    public ImportResult importCustomerRecord(Restaurant restaurant, ImportedCustomerRecord record) {
        if (restaurant == null) {
            throw new IllegalArgumentException("Restaurant is required for customer import");
        }
        if (record == null || isBlank(record.sourceClientId())) {
            throw new IllegalArgumentException("Each imported row must include a source client id");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<CustomerProfile> emailMatch = findProfileByEmail(record.email());
        Optional<CustomerProfile> phoneMatch = findProfileByPhone(record.phone());

        CustomerProfile profile = resolveProfile(emailMatch, phoneMatch).orElseGet(CustomerProfile::new);
        boolean newProfile = profile.getId() == null;

        if (newProfile) {
            profile.setCreatedAt(now);
        }

        profile.setFirstName(choosePreferred(record.firstName(), profile.getFirstName()));
        profile.setLastName(choosePreferred(record.lastName(), profile.getLastName()));
        profile.setFullName(choosePreferred(buildFullName(record.firstName(), record.lastName()), profile.getFullName()));
        profile.setEmail(choosePreferred(record.email(), profile.getEmail()));
        profile.setNormalizedEmail(normalizeEmail(choosePreferred(record.email(), profile.getEmail())));
        profile.setPhone(choosePreferred(record.phone(), profile.getPhone()));
        profile.setNormalizedPhone(normalizePhone(choosePreferred(record.phone(), profile.getPhone())));
        profile.setMarketingConsent(choosePreferred(record.marketingConsent(), profile.getMarketingConsent()));
        profile.setConsentType(choosePreferred(record.consentType(), profile.getConsentType()));
        profile.setLastRestaurantId(restaurant.getRestaurantId());
        profile.setLastRestaurantName(trimToEmpty(restaurant.getName()));
        profile.setUpdatedAt(now);

        profile = customerProfileRepository.save(profile);

        CustomerSourceRecord sourceRecord = customerSourceRecordRepository
                .findByRestaurantIdAndSourceClientId(restaurant.getRestaurantId(), record.sourceClientId())
                .orElseGet(CustomerSourceRecord::new);

        if (sourceRecord.getId() == null) {
            sourceRecord.setCreatedAt(now);
            sourceRecord.setRestaurantId(restaurant.getRestaurantId());
            sourceRecord.setSourceClientId(record.sourceClientId());
            sourceRecord.setSourceSystem(IMPORT_SOURCE);
        }

        sourceRecord.setCustomerProfileId(profile.getId());
        sourceRecord.setRestaurantName(trimToEmpty(restaurant.getName()));
        sourceRecord.setFirstName(trimToEmpty(record.firstName()));
        sourceRecord.setLastName(trimToEmpty(record.lastName()));
        sourceRecord.setEmail(trimToEmpty(record.email()));
        sourceRecord.setNormalizedEmail(normalizeEmail(record.email()));
        sourceRecord.setPhone(trimToEmpty(record.phone()));
        sourceRecord.setNormalizedPhone(normalizePhone(record.phone()));
        sourceRecord.setMarketingConsent(trimToEmpty(record.marketingConsent()));
        sourceRecord.setConsentType(trimToEmpty(record.consentType()));
        sourceRecord.setTotalOrders(safeLong(record.totalOrders()));
        sourceRecord.setTotalSpent(roundCurrency(safeDouble(record.totalSpent())));
        sourceRecord.setLastOrderAt(record.lastOrderAt());
        sourceRecord.setUpdatedAt(now);
        customerSourceRecordRepository.save(sourceRecord);

        CustomerRestaurantLink link = customerRestaurantLinkRepository
                .findByCustomerProfileIdAndRestaurantId(profile.getId(), restaurant.getRestaurantId())
                .orElseGet(CustomerRestaurantLink::new);

        if (link.getId() == null) {
            link.setCustomerProfileId(profile.getId());
            link.setRestaurantId(restaurant.getRestaurantId());
            link.setCreatedAt(now);
        }

        link.setRestaurantName(trimToEmpty(restaurant.getName()));
        link.setOrderCount(safeLong(record.totalOrders()));
        link.setTotalSpent(roundCurrency(safeDouble(record.totalSpent())));
        link.setLastOrderAt(record.lastOrderAt());
        if (link.getFirstOrderAt() == null) {
            link.setFirstOrderAt(record.lastOrderAt());
        }
        link.setLastSource(IMPORT_SOURCE);
        link.setUpdatedAt(now);
        customerRestaurantLinkRepository.save(link);

        recomputeProfileAggregates(profile.getId(), now);

        return new ImportResult(
                profile.getId(),
                newProfile,
                emailMatch.isPresent(),
                phoneMatch.isPresent(),
                emailMatch.isPresent() && phoneMatch.isPresent() && !emailMatch.get().getId().equals(phoneMatch.get().getId()));
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot buildDashboardSnapshot(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDateTime startTs = start.atStartOfDay();
        LocalDateTime endTs = end.atTime(LocalTime.MAX);

        long totalProfiles = customerProfileRepository.count();
        long newProfilesInRange = customerProfileRepository.countByFirstOrderAtBetween(startTs, endTs);

        List<Long> activeInRange = customerRestaurantLinkRepository.findActiveProfileIdsBetween(startTs, endTs);
        long existingProfilesInRange = activeInRange.stream()
                .map(id -> customerProfileRepository.findById(id).orElse(null))
                .filter(profile -> profile != null && profile.getFirstOrderAt() != null && profile.getFirstOrderAt().isBefore(startTs))
                .count();

        long overlapProfiles = customerRestaurantLinkRepository.findProfileIdsWithMultipleRestaurants().size();

        List<CustomerTopRow> topProfiles = new ArrayList<>();
        for (CustomerProfile profile : customerProfileRepository.findAllByOrderByTotalOrdersDescLastOrderAtDesc()) {
            topProfiles.add(new CustomerTopRow(
                    profile.getId(),
                    trimToEmpty(profile.getFullName()),
                    trimToEmpty(profile.getEmail()),
                    trimToEmpty(profile.getPhone()),
                    safeLong(profile.getTotalOrders()),
                    roundCurrency(safeDouble(profile.getTotalSpent())),
                    profile.getLastOrderAt(),
                    trimToEmpty(profile.getLastRestaurantName())));
        }

        return new DashboardSnapshot(totalProfiles, newProfilesInRange, existingProfilesInRange, overlapProfiles, topProfiles);
    }

    @Transactional(readOnly = true)
    public Optional<RestaurantCustomerSummary> findByRestaurantAndSourceClient(Long restaurantId, String sourceClientId) {
        return customerSourceRecordRepository.findByRestaurantIdAndSourceClientId(restaurantId, sourceClientId)
                .map(source -> new RestaurantCustomerSummary(
                        source.getCustomerProfileId(),
                        trimToEmpty(source.getFirstName()),
                        trimToEmpty(source.getLastName()),
                        trimToEmpty(source.getEmail()),
                        trimToEmpty(source.getPhone())));
    }

    private void recomputeProfileAggregates(Long profileId, LocalDateTime now) {
        List<CustomerRestaurantLink> links = customerRestaurantLinkRepository.findByCustomerProfileId(profileId);
        if (links.isEmpty()) {
            return;
        }

        long totalOrders = 0L;
        double totalSpent = 0.0;
        LocalDateTime first = null;
        LocalDateTime last = null;

        for (CustomerRestaurantLink link : links) {
            totalOrders += safeLong(link.getOrderCount());
            totalSpent += safeDouble(link.getTotalSpent());

            if (link.getFirstOrderAt() != null && (first == null || link.getFirstOrderAt().isBefore(first))) {
                first = link.getFirstOrderAt();
            }
            if (link.getLastOrderAt() != null && (last == null || link.getLastOrderAt().isAfter(last))) {
                last = link.getLastOrderAt();
            }
        }

        final long aggregateOrders = totalOrders;
        final double aggregateSpent = totalSpent;
        final LocalDateTime firstOrderAt = first;
        final LocalDateTime lastOrderAt = last;

        customerProfileRepository.findById(profileId).ifPresent(profile -> {
            profile.setTotalOrders(aggregateOrders);
            profile.setTotalSpent(roundCurrency(aggregateSpent));
            if (firstOrderAt != null) {
                profile.setFirstOrderAt(firstOrderAt);
            }
            if (lastOrderAt != null) {
                profile.setLastOrderAt(lastOrderAt);
            }
            profile.setUpdatedAt(now == null ? LocalDateTime.now() : now);
            customerProfileRepository.save(profile);
        });
    }

    private Optional<CustomerProfile> findProfileByBestMatch(String email, String phone) {
        Optional<CustomerProfile> emailMatch = findProfileByEmail(email);
        Optional<CustomerProfile> phoneMatch = findProfileByPhone(phone);
        return resolveProfile(emailMatch, phoneMatch);
    }

    private Optional<CustomerProfile> resolveProfile(Optional<CustomerProfile> emailMatch, Optional<CustomerProfile> phoneMatch) {
        if (emailMatch.isPresent() && phoneMatch.isPresent()) {
            if (emailMatch.get().getId().equals(phoneMatch.get().getId())) {
                return emailMatch;
            }
            return emailMatch;
        }
        return emailMatch.isPresent() ? emailMatch : phoneMatch;
    }

    private Optional<CustomerProfile> findProfileByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return customerProfileRepository.findFirstByNormalizedEmail(normalized);
    }

    private Optional<CustomerProfile> findProfileByPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return customerProfileRepository.findFirstByNormalizedPhone(normalized);
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            return "";
        }
        return email.trim().toLowerCase(Locale.CANADA);
    }

    private String normalizePhone(String phone) {
        if (isBlank(phone)) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("1")) {
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+1" + digits;
        }
        if (digits.isBlank()) {
            return "";
        }
        return "+" + digits;
    }

    private String normalizeAddressKey(String streetAddress, String city, String postalCode) {
        String combined = trimToEmpty(streetAddress) + "|" + trimToEmpty(city) + "|" + trimToEmpty(postalCode);
        String normalized = combined
                .toLowerCase(Locale.CANADA)
                .replaceAll("[^a-z0-9]", "");
        return normalized;
    }

    private void upsertCustomerAddress(
            Long customerProfileId,
            String streetAddress,
            String city,
            String postalCode,
            Double latitude,
            Double longitude,
            String locationSource,
            LocalDateTime now) {
        if (customerProfileId == null) {
            return;
        }

        String normalizedStreet = trimToEmpty(streetAddress);
        String normalizedCity = trimToEmpty(city);
        String normalizedPostal = trimToEmpty(postalCode);

        if (normalizedStreet.isBlank() && normalizedCity.isBlank() && normalizedPostal.isBlank()) {
            return;
        }

        String normalizedKey = normalizeAddressKey(normalizedStreet, normalizedCity, normalizedPostal);
        if (normalizedKey.isBlank()) {
            return;
        }

        CustomerAddress address = customerAddressRepository
                .findFirstByCustomerProfileIdAndNormalizedAddress(customerProfileId, normalizedKey)
                .orElseGet(CustomerAddress::new);

        if (address.getId() == null) {
            address.setCustomerProfileId(customerProfileId);
            address.setCreatedAt(now);
            address.setLabel("");
        }

        address.setStreetAddress(normalizedStreet);
        address.setCity(normalizedCity);
        address.setPostalCode(normalizedPostal);
        address.setLatitude(latitude);
        address.setLongitude(longitude);
        if (latitude != null && longitude != null) {
            address.setLocationConfirmedAt(now);
            address.setLocationSource(trimToEmpty(locationSource));
        } else {
            address.setLocationConfirmedAt(null);
            address.setLocationSource(trimToEmpty(locationSource));
        }
        address.setNormalizedAddress(normalizedKey);
        address.setLastUsedAt(now);
        address.setUpdatedAt(now);
        customerAddressRepository.save(address);
    }

    private String choosePreferred(String incoming, String existing) {
        if (!isBlank(incoming)) {
            return incoming.trim();
        }
        return trimToEmpty(existing);
    }

    private String deriveFirstName(String fullName, String fallback) {
        if (isBlank(fullName)) {
            return trimToEmpty(fallback);
        }
        String[] tokens = fullName.trim().split("\\s+");
        return tokens.length == 0 ? trimToEmpty(fallback) : tokens[0];
    }

    private String deriveLastName(String fullName, String fallback) {
        if (isBlank(fullName)) {
            return trimToEmpty(fallback);
        }
        String[] tokens = fullName.trim().split("\\s+");
        if (tokens.length < 2) {
            return trimToEmpty(fallback);
        }
        return tokens[tokens.length - 1];
    }

    private String buildFullName(String firstName, String lastName) {
        String first = trimToEmpty(firstName);
        String last = trimToEmpty(lastName);
        if (first.isBlank() && last.isBlank()) {
            return "";
        }
        if (first.isBlank()) {
            return last;
        }
        if (last.isBlank()) {
            return first;
        }
        return first + " " + last;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : Math.max(0.0, value);
    }

    private double roundCurrency(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record CustomerPrefill(
            Long customerProfileId,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String streetAddress,
            String city,
            String postalCode,
            List<CustomerAddressChoice> addresses) {
        }

        public record CustomerAddressChoice(
            Long id,
            String label,
            String streetAddress,
            String city,
                String postalCode,
                Double latitude,
                Double longitude,
                LocalDateTime locationConfirmedAt,
                String locationSource) {
    }

    public record ImportedCustomerRecord(
            String sourceClientId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String marketingConsent,
            String consentType,
            Long totalOrders,
            Double totalSpent,
            LocalDateTime lastOrderAt) {
    }

    public record ImportResult(
            Long customerProfileId,
            boolean newProfile,
            boolean emailMatched,
            boolean phoneMatched,
            boolean conflictingMatch) {
    }

    public record DashboardSnapshot(
            long totalProfiles,
            long newProfilesInRange,
            long existingProfilesInRange,
            long multiRestaurantProfiles,
            List<CustomerTopRow> topProfiles) {
    }

    public record CustomerTopRow(
            Long customerProfileId,
            String fullName,
            String email,
            String phone,
            long totalOrders,
            double totalSpent,
            LocalDateTime lastOrderAt,
            String lastRestaurantName) {
    }

    public record RestaurantCustomerSummary(
            Long customerProfileId,
            String firstName,
            String lastName,
            String email,
            String phone) {
    }
}
