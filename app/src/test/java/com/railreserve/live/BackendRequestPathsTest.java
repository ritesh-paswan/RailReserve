package com.railreserve.live;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BackendRequestPathsTest {
    @Test public void usesBackendPathsAndEncodesQueryValues() {
        assertEquals("/api/stations/search?q=New+Delhi", BackendRequestPaths.stationSearch("New Delhi"));
        assertEquals("/api/trains/between?from=NDLS&to=HWH&date=2026-10-20",
                BackendRequestPaths.trainsBetween("NDLS", "HWH", "2026-10-20"));
        assertEquals("/api/trains/12345/schedule", BackendRequestPaths.schedule("12345"));
        assertEquals("/api/trains/12345/fare?source=NDLS&destination=HWH&journeyDate=2026-10-20&classCode=3A&quotaCode=GN",
                BackendRequestPaths.fare("12345", "NDLS", "HWH", "2026-10-20", "3A", "GN"));
    }
}
