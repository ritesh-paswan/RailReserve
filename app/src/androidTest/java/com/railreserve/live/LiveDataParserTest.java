package com.railreserve.live;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class LiveDataParserTest {
    @Test public void parsesStationCodesAndAllowsEmptySearch() throws Exception {
        JSONObject data = new JSONObject("{\"stations\":[{\"code\":\"NDLS\",\"name\":\"New Delhi\",\"city\":\"Delhi\"},"
                + "{\"code\":\"PRYJ\",\"name\":\"Prayagraj Junction\",\"city\":\"Prayagraj\"}]}");
        List<LiveStation> stations = LiveDataParser.parseStations(data);
        assertEquals("NDLS", stations.get(0).getCode());
        assertEquals("New Delhi (NDLS)", stations.get(0).toString());
        assertEquals("PRYJ", stations.get(1).getCode());
        assertEquals(0, LiveDataParser.parseStations(new JSONObject("{\"stations\":[]}")).size());
    }

    @Test public void rejectsMalformedStationEnvelopeAndRows() throws Exception {
        assertThrows(BackendException.class,
                () -> LiveDataParser.parseStations(new JSONObject("{\"results\":[]}")));
        assertThrows(BackendException.class,
                () -> LiveDataParser.parseStations(new JSONObject("{\"stations\":[{\"code\":\"NDLS\"}]}")));
    }

    @Test public void buildsEncodedAvailabilityRequestForSelectedTrainClassAndDate() {
        assertEquals("/api/trains/12345/availability?source=NDLS&destination=HWH"
                        + "&journeyDate=2026-10-25&classCode=3A&quotaCode=GN",
                BackendRequestPaths.availability("12345", "NDLS", "HWH", "2026-10-25", "3A", "GN"));
    }

    @Test public void parsesTrainOffsetsAndMissingOptionalFieldsWithoutInventingValues() throws Exception {
        JSONObject data = new JSONObject("{\"from\":{\"code\":\"NDLS\",\"name\":\"New Delhi\"},"
                + "\"to\":{\"code\":\"HWH\",\"name\":\"Howrah\"},\"count\":1,\"trains\":["
                + "{\"number\":\"12345\",\"name\":\"Example Express\",\"from\":{\"departure\":\"22:00\",\"day\":1},"
                + "\"to\":{\"arrival\":\"08:00\",\"day\":2}}]}");
        LiveTrain train = LiveDataParser.parseTrains(data).get(0);
        assertEquals("12345", train.getNumber());
        assertEquals(Integer.valueOf(2), train.getArrivalDay());
        assertEquals("08:00", train.getArrival());
        assertNull(train.getDurationMinutes());
        assertNull(train.getRunDays());
    }

    @Test public void schedulePreservesStopOrderNullTimesAndDayOffsets() throws Exception {
        JSONObject data = new JSONObject("{\"train\":{\"number\":\"12345\",\"name\":\"Example Express\"},"
                + "\"stops\":[{\"sequence\":1,\"station\":{\"code\":\"NDLS\",\"name\":\"New Delhi\"},"
                + "\"arrival\":null,\"departure\":\"22:00\",\"departureDay\":1},"
                + "{\"sequence\":2,\"station\":{\"code\":\"HWH\",\"name\":\"Howrah\"},"
                + "\"arrival\":\"08:00\",\"departure\":null,\"arrivalDay\":2}]}");
        LiveSchedule schedule = LiveDataParser.parseSchedule(data);
        assertEquals(2, schedule.getStops().size());
        assertEquals("NDLS", schedule.getStops().get(0).getStation().getCode());
        assertNull(schedule.getStops().get(0).getArrival());
        assertEquals(Integer.valueOf(2), schedule.getStops().get(1).getArrivalDay());
        assertNull(schedule.getStops().get(1).getDeparture());
    }

    @Test public void parsesLiveAvailabilityStatusAndDate() throws Exception {
        LiveAvailability availability = LiveDataParser.parseAvailability(
                new JSONObject("{\"availabilityStatus\":\"AVAILABLE 4\",\"statusType\":\"AVAILABLE\","
                        + "\"availabilityDate\":\"2026-10-25\"}"));
        assertEquals("AVAILABLE 4", availability.getStatus());
        assertEquals("AVAILABLE", availability.getStatusType());
        assertEquals("2026-10-25", availability.getJourneyDate());
        assertThrows(BackendException.class,
                () -> LiveDataParser.parseAvailability(new JSONObject("{\"statusType\":\"UNKNOWN\"}")));
    }

    @Test public void rejectsUnexpectedEnvelopesAndMapsSanitizedBackendErrors() throws Exception {
        assertThrows(BackendException.class, () -> LiveDataParser.successData("not-json"));
        try {
            LiveDataParser.successData("{\"success\":false,\"error\":{\"code\":\"PROVIDER_RATE_LIMITED\",\"message\":\"secret\"}}");
        } catch (BackendException exception) {
            assertEquals("PROVIDER_RATE_LIMITED", exception.getCode());
            assertEquals("Request limit reached. Please try again later.", exception.getMessage());
            return;
        }
        throw new AssertionError("Expected backend error");
    }
}
