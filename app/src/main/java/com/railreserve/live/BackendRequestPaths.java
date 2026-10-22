package com.railreserve.live;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public final class BackendRequestPaths {
    private BackendRequestPaths() { }

    public static String stationSearch(String query) {
        return "/api/stations/search?q=" + encode(query);
    }

    public static String trainsBetween(String source, String destination, String date) {
        return "/api/trains/between?from=" + encode(source) + "&to=" + encode(destination) + "&date=" + encode(date);
    }

    public static String schedule(String trainNumber) {
        return "/api/trains/" + encode(trainNumber) + "/schedule";
    }

    public static String fare(String trainNumber, String source, String destination,
                              String journeyDate, String classCode, String quotaCode) {
        return "/api/trains/" + encode(trainNumber) + "/fare?source=" + encode(source)
                + "&destination=" + encode(destination) + "&journeyDate=" + encode(journeyDate)
                + "&classCode=" + encode(classCode) + "&quotaCode=" + encode(quotaCode);
    }

    public static String availability(String trainNumber, String source, String destination,
                                      String journeyDate, String classCode, String quotaCode) {
        return "/api/trains/" + encode(trainNumber) + "/availability?source=" + encode(source)
                + "&destination=" + encode(destination) + "&journeyDate=" + encode(journeyDate)
                + "&classCode=" + encode(classCode) + "&quotaCode=" + encode(quotaCode);
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (UnsupportedEncodingException exception) { throw new IllegalStateException("UTF-8 is unavailable.", exception); }
    }
}
