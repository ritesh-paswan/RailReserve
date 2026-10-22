package com.railreserve.live;

public final class LiveAvailability {
    private final String status;
    private final String statusType;
    private final String journeyDate;

    public LiveAvailability(String status, String statusType, String journeyDate) {
        this.status = status;
        this.statusType = statusType;
        this.journeyDate = journeyDate;
    }

    public String getStatus() { return status; }
    public String getStatusType() { return statusType; }
    public String getJourneyDate() { return journeyDate; }
}
