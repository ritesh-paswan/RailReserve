package com.railreserve.live;

import java.util.Locale;

public final class StationSuggestionMatcher {
    private StationSuggestionMatcher() { }

    public static boolean matches(LiveStation station, String query) {
        if (station == null) return false;
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        String label = station.getLabel().toLowerCase(Locale.ROOT);
        String city = station.getCity() == null
                ? ""
                : station.getCity().toLowerCase(Locale.ROOT);
        return label.contains(normalized) || city.contains(normalized);
    }
}
