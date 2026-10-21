package com.railreserve.live;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StationSuggestionMatcherTest {
    @Test public void matchesSubstringInStationNameAndCodeWithoutCaseSensitivity() {
        LiveStation station = new LiveStation("NDLS", "New Delhi", null);

        assertTrue(StationSuggestionMatcher.matches(station, "Delhi"));
        assertTrue(StationSuggestionMatcher.matches(station, "ndls"));
        assertFalse(StationSuggestionMatcher.matches(station, "Mumbai"));
    }

    @Test public void matchesCityAndHandlesBlankQueries() {
        LiveStation station = new LiveStation("NDLS", "New Delhi", "Delhi");

        assertTrue(StationSuggestionMatcher.matches(station, "del"));
        assertTrue(StationSuggestionMatcher.matches(station, " "));
        assertFalse(StationSuggestionMatcher.matches(null, "Delhi"));
    }
}
