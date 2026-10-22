package com.railreserve.live;

import com.railreserve.BuildConfig;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class BackendApiClient {
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 25000;
    private final String baseUrl;

    public BackendApiClient() {
        String configured = BuildConfig.BACKEND_BASE_URL == null ? "" : BuildConfig.BACKEND_BASE_URL.trim();
        while (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
        baseUrl = configured;
    }

    public JSONObject stationSearch(String query) throws BackendException {
        return get(BackendRequestPaths.stationSearch(query));
    }

    public JSONObject trainsBetween(String source, String destination, String date) throws BackendException {
        return get(BackendRequestPaths.trainsBetween(source, destination, date));
    }

    public JSONObject schedule(String trainNumber) throws BackendException {
        return get(BackendRequestPaths.schedule(trainNumber));
    }

    public JSONObject fare(String trainNumber, String source, String destination, String journeyDate,
                           String classCode, String quotaCode) throws BackendException {
        return get(BackendRequestPaths.fare(trainNumber, source, destination, journeyDate, classCode, quotaCode));
    }

    public JSONObject availability(String trainNumber, String source, String destination,
                                   String journeyDate, String classCode, String quotaCode) throws BackendException {
        return get(BackendRequestPaths.availability(trainNumber, source, destination, journeyDate, classCode, quotaCode));
    }

    public String getBaseUrl() { return baseUrl; }

    private JSONObject get(String path) throws BackendException {
        if (baseUrl.isEmpty()) throw new BackendException("BACKEND_NOT_CONFIGURED", "Train-data service URL is not configured.");
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) (isLoopbackHost(url.getHost())
                    ? url.openConnection(Proxy.NO_PROXY)
                    : url.openConnection());
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readBody(stream);
            JSONObject data;
            try {
                data = LiveDataParser.successData(body);
            } catch (BackendException exception) {
                if ((status < 200 || status >= 300) && "INVALID_RESPONSE".equals(exception.getCode())) {
                    throw new BackendException("HTTP_" + status, statusMessage(status), exception);
                }
                throw exception;
            }
            if (status < 200 || status >= 300) {
                throw new BackendException("HTTP_" + status, statusMessage(status));
            }
            return data;
        } catch (SocketTimeoutException exception) {
            throw new BackendException("TIMEOUT", "Train-data service timed out. Check your connection and retry.", exception);
        } catch (BackendException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw new BackendException("CANCELLED", "Request cancelled.", exception);
            }
            throw new BackendException("CONNECTION", "Unable to connect to the train-data service. Check your connection and try again.", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String statusMessage(int status) {
        if (status == 401 || status == 403) return "The train-data service rejected the request. Check backend access and try again.";
        if (status == 404) return "No train data was found for this request.";
        if (status == 429) return "Request limit reached. Please try again later.";
        if (status >= 500) return "Train-data service is temporarily unavailable. Please try again later.";
        return "Train-data service returned an unexpected response.";
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }
}
