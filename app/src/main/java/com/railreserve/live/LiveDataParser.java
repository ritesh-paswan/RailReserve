package com.railreserve.live;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class LiveDataParser {
    private LiveDataParser() { }

    public static List<LiveStation> parseStations(JSONObject data) throws BackendException {
        JSONArray rows = data == null ? null : data.optJSONArray("stations");
        if (rows == null) throw malformed("Station list was missing.");
        List<LiveStation> stations = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.optJSONObject(i);
            String code = requiredString(item, "code");
            String name = requiredString(item, "name");
            if (code == null || name == null) throw malformed("Station data was incomplete.");
            stations.add(new LiveStation(code, name, optionalString(item, "city")));
        }
        return stations;
    }

    public static List<LiveTrain> parseTrains(JSONObject data) throws BackendException {
        JSONObject from = data == null ? null : data.optJSONObject("from");
        JSONObject to = data == null ? null : data.optJSONObject("to");
        JSONArray rows = data == null ? null : data.optJSONArray("trains");
        LiveStation source = parseStation(from);
        LiveStation destination = parseStation(to);
        Object rawCount = data == null ? null : data.opt("count");
        if (source == null || destination == null || !(rawCount instanceof Number) || rows == null) {
            throw malformed("Train search response was incomplete.");
        }
        List<LiveTrain> trains = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            JSONObject routeFrom = row == null ? null : row.optJSONObject("from");
            JSONObject routeTo = row == null ? null : row.optJSONObject("to");
            String number = requiredString(row, "number");
            String name = requiredString(row, "name");
            if (number == null || name == null || routeFrom == null || routeTo == null) {
                throw malformed("Train result was incomplete.");
            }
            trains.add(new LiveTrain(number, name, optionalString(row, "type"), source, destination,
                    optionalString(routeFrom, "departure"), optionalInteger(routeFrom, "day"),
                    optionalString(routeTo, "arrival"), optionalInteger(routeTo, "day"),
                    optionalInteger(row, "durationMinutes"), optionalDouble(row, "distanceKm"),
                    optionalInteger(row, "totalHaltsBetween"), optionalStrings(row.optJSONArray("runDays"))));
        }
        return trains;
    }

    public static LiveSchedule parseSchedule(JSONObject data) throws BackendException {
        JSONObject train = data == null ? null : data.optJSONObject("train");
        JSONArray route = data == null ? null : data.optJSONArray("stops");
        String number = requiredString(train, "number");
        String name = requiredString(train, "name");
        if (number == null || name == null || route == null) throw malformed("Train schedule response was incomplete.");
        List<LiveTrainStop> stops = new ArrayList<>();
        for (int i = 0; i < route.length(); i++) {
            JSONObject item = route.optJSONObject(i);
            JSONObject stationObject = item == null ? null : item.optJSONObject("station");
            LiveStation station = parseStation(stationObject);
            if (item == null || station == null) throw malformed("Train schedule contained an incomplete stop.");
            stops.add(new LiveTrainStop(optionalInteger(item, "sequence"), station,
                    optionalString(item, "arrival"), optionalString(item, "departure"),
                    optionalInteger(item, "arrivalDay"), optionalInteger(item, "departureDay"),
                    optionalDouble(item, "distanceKm"), optionalBoolean(item, "isHalt"), optionalString(item, "platform")));
        }
        return new LiveSchedule(number, name, optionalString(train, "type"), optionalString(train, "category"),
                parseStation(train.optJSONObject("source")), parseStation(train.optJSONObject("destination")),
                optionalStrings(train.optJSONArray("runDays")), stops);
    }

    public static LiveAvailability parseAvailability(JSONObject data) throws BackendException {
        String status = requiredString(data, "availabilityStatus");
        String statusType = requiredString(data, "statusType");
        if (status == null || statusType == null) {
            throw malformed("Seat availability response was incomplete.");
        }
        return new LiveAvailability(status, statusType, optionalString(data, "availabilityDate"));
    }

    public static LiveFare parseFare(JSONObject data) throws BackendException {
        if (data == null || !(data.opt("fareKnown") instanceof Boolean)) {
            throw malformed("Fare response was incomplete.");
        }
        boolean known = data.optBoolean("fareKnown");
        Double total = optionalDouble(data, "totalFare");
        if (known && (total == null || total < 0)) {
            throw malformed("Fare response did not include a valid total.");
        }
        return new LiveFare(known, total);
    }

    public static JSONObject successData(String body) throws BackendException {
        try {
            JSONObject envelope = new JSONObject(body);
            if (!envelope.optBoolean("success", false)) {
                JSONObject error = envelope.optJSONObject("error");
                String code = error == null ? "BACKEND_ERROR" : error.optString("code", "BACKEND_ERROR");
                throw new BackendException(code, userMessage(code));
            }
            JSONObject data = envelope.optJSONObject("data");
            if (data == null) throw malformed("Backend response was incomplete.");
            return data;
        } catch (JSONException exception) {
            throw new BackendException("INVALID_RESPONSE", "Train-data service returned invalid data.", exception);
        }
    }

    private static LiveStation parseStation(JSONObject value) {
        String code = requiredString(value, "code");
        return code == null ? null : new LiveStation(code, optionalString(value, "name"), optionalString(value, "city"));
    }

    private static String requiredString(JSONObject value, String key) {
        String result = optionalString(value, key);
        return result == null || result.trim().isEmpty() ? null : result;
    }

    private static String optionalString(JSONObject value, String key) {
        if (value == null || value.isNull(key)) return null;
        Object raw = value.opt(key);
        return raw instanceof String && !((String) raw).trim().isEmpty() ? (String) raw : null;
    }

    private static Integer optionalInteger(JSONObject value, String key) {
        if (value == null || value.isNull(key)) return null;
        Object raw = value.opt(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        return null;
    }

    private static Double optionalDouble(JSONObject value, String key) {
        if (value == null || value.isNull(key)) return null;
        Object raw = value.opt(key);
        return raw instanceof Number ? ((Number) raw).doubleValue() : null;
    }

    private static Boolean optionalBoolean(JSONObject value, String key) {
        if (value == null || value.isNull(key)) return null;
        Object raw = value.opt(key);
        return raw instanceof Boolean ? (Boolean) raw : null;
    }

    private static List<String> optionalStrings(JSONArray values) {
        if (values == null) return null;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            if (value instanceof String) result.add((String) value);
        }
        return result;
    }

    private static BackendException malformed(String message) {
        return new BackendException("INVALID_RESPONSE", message);
    }

    public static String userMessage(String code) {
        if ("PROVIDER_NOT_CONFIGURED".equals(code)) return "Train-data service is not configured. Try again later.";
        if ("PROVIDER_AUTH_ERROR".equals(code)) return "The provider rejected this request. Please try again later.";
        if ("PROVIDER_ACCESS_DENIED".equals(code)) return "The provider key does not have access to this service. Check its scopes and plan.";
        if ("PROVIDER_CREDITS_EXHAUSTED".equals(code)) return "The provider account has insufficient credits. Try again later.";
        if ("PROVIDER_VERSION_UNAVAILABLE".equals(code)) return "The provider API version is unavailable. Contact support.";
        if ("PROVIDER_RATE_LIMITED".equals(code) || "RATE_LIMITED".equals(code)) return "Request limit reached. Please try again later.";
        if ("PROVIDER_NOT_FOUND".equals(code)) return "No train data was found for this request.";
        if ("PROVIDER_TIMEOUT".equals(code)) return "Train-data service timed out. Check your connection and retry.";
        if ("PROVIDER_UNAVAILABLE".equals(code)) return "Unable to connect to the train-data provider. Try again later.";
        if ("INVALID_INPUT".equals(code)) return "Check the selected stations and journey date, then try again.";
        if ("INVALID_PROVIDER_RESPONSE".equals(code) || "INVALID_RESPONSE".equals(code)) return "Train-data service returned an unexpected response.";
        if ("NOT_FOUND".equals(code)) return "No train data was found for this request.";
        return "Unable to connect to the train-data service. Check your connection and try again.";
    }
}
