package com.railreserve.live;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LiveDataRepository {
    private static final LiveDataRepository INSTANCE = new LiveDataRepository();
    private final BackendApiClient api = new BackendApiClient();
    private final Map<String, List<LiveStation>> stationCache = new HashMap<>();
    private final Map<String, LiveSchedule> scheduleCache = new HashMap<>();

    private LiveDataRepository() { }
    public static LiveDataRepository getInstance() { return INSTANCE; }

    public synchronized List<LiveStation> searchStations(String query) throws BackendException {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (stationCache.containsKey(normalized)) return stationCache.get(normalized);
        JSONObject data = api.stationSearch(query);
        List<LiveStation> loaded = Collections.unmodifiableList(new ArrayList<>(LiveDataParser.parseStations(data)));
        stationCache.put(normalized, loaded);
        return loaded;
    }

    public List<LiveTrain> searchTrains(String from, String to, String date) throws BackendException {
        return LiveDataParser.parseTrains(api.trainsBetween(from, to, date));
    }

    public LiveFare getFare(String trainNumber, String from, String to, String date,
                            String classCode, String quotaCode) throws BackendException {
        return LiveDataParser.parseFare(
                api.fare(trainNumber, from, to, date, classCode, quotaCode));
    }

    public LiveAvailability getAvailability(String trainNumber, String from, String to, String date,
                                            String classCode, String quotaCode) throws BackendException {
        return LiveDataParser.parseAvailability(
                api.availability(trainNumber, from, to, date, classCode, quotaCode));
    }

    public synchronized LiveSchedule getSchedule(String trainNumber) throws BackendException {
        if (scheduleCache.containsKey(trainNumber)) return scheduleCache.get(trainNumber);
        LiveSchedule schedule = LiveDataParser.parseSchedule(api.schedule(trainNumber));
        scheduleCache.put(trainNumber, schedule);
        return schedule;
    }
}
