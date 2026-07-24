package ca.admin.delivermore.data.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import ca.admin.delivermore.collector.data.entity.RestaurantMenuCategory;
import ca.admin.delivermore.collector.data.entity.RestaurantMenuItem;

public final class MenuVisibilitySupport {

    public static final int DAYS_ALL = 127;
    public static final int DAYS_NONE = 0;
    public static final String PREFIX_HIDE_UNTIL = "HIDE_UNTIL|";
    public static final String PREFIX_SHOW_RANGE = "SHOW_RANGE|";

    public enum VisibilityMode {
        ALWAYS,
        HIDE_FROM_MENU,
        HIDE_UNTIL,
        SHOW_WEEKLY,
        SHOW_DATE_RANGE
    }

    public record VisibilitySettings(
            VisibilityMode mode,
            LocalTime startTime,
            LocalTime endTime,
            int daysMask,
            LocalDateTime fromDateTime,
            LocalDateTime untilDateTime) {

        public boolean restrictive() {
            return mode != VisibilityMode.ALWAYS;
        }
    }

    private MenuVisibilitySupport() {
    }

    public static VisibilitySettings read(Boolean active, String activeBegin, String activeEnd, Integer activeDays) {
        String begin = trimToNull(activeBegin);
        String end = trimToNull(activeEnd);
        boolean isActive = !Boolean.FALSE.equals(active);

        if (begin != null && begin.startsWith(PREFIX_HIDE_UNTIL)) {
            LocalDateTime until = parseDateTime(trimToNull(begin.substring(PREFIX_HIDE_UNTIL.length())));
            if (until == null) {
                until = parseDateTime(end);
            }
            return new VisibilitySettings(VisibilityMode.HIDE_UNTIL, null, null, DAYS_NONE, null, until);
        }

        if (begin != null && begin.startsWith(PREFIX_SHOW_RANGE)) {
            LocalDateTime from = parseDateTime(trimToNull(begin.substring(PREFIX_SHOW_RANGE.length())));
            LocalDateTime until = parseDateTime(end);
            if (from != null && until != null) {
                return new VisibilitySettings(VisibilityMode.SHOW_DATE_RANGE, null, null, DAYS_ALL, from, until);
            }
        }

        LocalDateTime beginAsDateTime = parseDateTime(begin);
        LocalDateTime endAsDateTime = parseDateTime(end);
        if (isActive && beginAsDateTime != null && endAsDateTime != null) {
            return new VisibilitySettings(VisibilityMode.SHOW_DATE_RANGE, null, null, DAYS_ALL, beginAsDateTime, endAsDateTime);
        }
        if (!isActive && beginAsDateTime != null && endAsDateTime == null) {
            return new VisibilitySettings(VisibilityMode.HIDE_UNTIL, null, null, DAYS_NONE, null, beginAsDateTime);
        }

        LocalTime start = parseTime(begin);
        LocalTime stop = parseTime(end);
        int normalizedDays = normalizeDays(activeDays);

        if (!isActive) {
            return new VisibilitySettings(VisibilityMode.HIDE_FROM_MENU, null, null, DAYS_NONE, null, null);
        }

        boolean weeklyConfigured = start != null || stop != null || normalizedDays != DAYS_ALL;
        if (weeklyConfigured) {
            return new VisibilitySettings(VisibilityMode.SHOW_WEEKLY, start, stop, normalizedDays, null, null);
        }

        return new VisibilitySettings(VisibilityMode.ALWAYS, null, null, DAYS_ALL, null, null);
    }

    public static void applyToCategory(RestaurantMenuCategory category, VisibilitySettings settings) {
        category.setActive(toActive(settings));
        category.setActiveBegin(toActiveBegin(settings));
        category.setActiveEnd(toActiveEnd(settings));
        category.setActiveDays(toActiveDays(settings));
    }

    public static void applyToItem(RestaurantMenuItem item, VisibilitySettings settings) {
        item.setActive(toActive(settings));
        item.setActiveBegin(toActiveBegin(settings));
        item.setActiveEnd(toActiveEnd(settings));
        item.setActiveDays(toActiveDays(settings));
    }

    public static boolean isVisibleNow(Boolean active, String activeBegin, String activeEnd, Integer activeDays, LocalDateTime now) {
        VisibilitySettings settings = read(active, activeBegin, activeEnd, activeDays);
        LocalDateTime current = now == null ? LocalDateTime.now() : now;

        return switch (settings.mode()) {
            case ALWAYS -> true;
            case HIDE_FROM_MENU -> false;
            case HIDE_UNTIL -> settings.untilDateTime() != null && current.isAfter(settings.untilDateTime());
            case SHOW_DATE_RANGE -> isInDateRange(settings, current);
            case SHOW_WEEKLY -> isInWeeklyWindow(settings, current);
        };
    }

    public static int normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return DAYS_ALL;
        }
        return days;
    }

    public static int dayMaskFromSelection(
            boolean monday,
            boolean tuesday,
            boolean wednesday,
            boolean thursday,
            boolean friday,
            boolean saturday,
            boolean sunday) {
        int mask = 0;
        if (monday) {
            mask |= 1;
        }
        if (tuesday) {
            mask |= 2;
        }
        if (wednesday) {
            mask |= 4;
        }
        if (thursday) {
            mask |= 8;
        }
        if (friday) {
            mask |= 16;
        }
        if (saturday) {
            mask |= 32;
        }
        if (sunday) {
            mask |= 64;
        }
        return mask;
    }

    public static boolean includesDay(int daysMask, DayOfWeek dayOfWeek) {
        int bit = switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 4;
            case THURSDAY -> 8;
            case FRIDAY -> 16;
            case SATURDAY -> 32;
            case SUNDAY -> 64;
        };
        return (daysMask & bit) == bit;
    }

    public static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static LocalDateTime parseDateTime(String value) {
        String input = trimToNull(value);
        if (input == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        try {
            OffsetDateTime parsed = OffsetDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return parsed.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        String normalized = input.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Boolean toActive(VisibilitySettings settings) {
        return switch (settings.mode()) {
            case ALWAYS, SHOW_WEEKLY, SHOW_DATE_RANGE -> Boolean.TRUE;
            case HIDE_FROM_MENU, HIDE_UNTIL -> Boolean.FALSE;
        };
    }

    private static String toActiveBegin(VisibilitySettings settings) {
        return switch (settings.mode()) {
            case ALWAYS, HIDE_FROM_MENU -> null;
            case HIDE_UNTIL -> PREFIX_HIDE_UNTIL + formatDateTime(settings.untilDateTime());
            case SHOW_WEEKLY -> formatTime(settings.startTime());
            case SHOW_DATE_RANGE -> PREFIX_SHOW_RANGE + formatDateTime(settings.fromDateTime());
        };
    }

    private static String toActiveEnd(VisibilitySettings settings) {
        return switch (settings.mode()) {
            case ALWAYS, HIDE_FROM_MENU, HIDE_UNTIL -> null;
            case SHOW_WEEKLY -> formatTime(settings.endTime());
            case SHOW_DATE_RANGE -> formatDateTime(settings.untilDateTime());
        };
    }

    private static Integer toActiveDays(VisibilitySettings settings) {
        return switch (settings.mode()) {
            case ALWAYS, SHOW_DATE_RANGE -> DAYS_ALL;
            case HIDE_FROM_MENU, HIDE_UNTIL -> DAYS_NONE;
            case SHOW_WEEKLY -> normalizeDays(settings.daysMask());
        };
    }

    private static String formatTime(LocalTime value) {
        if (value == null) {
            return null;
        }
        return value.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static LocalTime parseTime(String value) {
        String input = trimToNull(value);
        if (input == null) {
            return null;
        }

        if (input.chars().allMatch(Character::isDigit)) {
            try {
                int seconds = Integer.parseInt(input);
                if (seconds < 0) {
                    return null;
                }
                // GloriaFood commonly encodes active windows as seconds from midnight.
                int normalized = seconds % (24 * 60 * 60);
                return LocalTime.ofSecondOfDay(normalized);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            return LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isInDateRange(VisibilitySettings settings, LocalDateTime now) {
        LocalDateTime from = settings.fromDateTime();
        LocalDateTime until = settings.untilDateTime();
        if (from == null || until == null) {
            return false;
        }
        return !now.isBefore(from) && !now.isAfter(until);
    }

    private static boolean isInWeeklyWindow(VisibilitySettings settings, LocalDateTime now) {
        int days = normalizeDays(settings.daysMask());
        if (!includesDay(days, now.getDayOfWeek())) {
            return false;
        }

        LocalTime start = settings.startTime();
        LocalTime end = settings.endTime();
        if (start == null && end == null) {
            return true;
        }

        LocalTime current = now.toLocalTime();
        if (start == null) {
            return !current.isAfter(end);
        }
        if (end == null) {
            return !current.isBefore(start);
        }

        if (!start.isAfter(end)) {
            return !current.isBefore(start) && !current.isAfter(end);
        }

        // Overnight window such as 22:00-02:00.
        return !current.isBefore(start) || !current.isAfter(end);
    }
}
