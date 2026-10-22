package com.railreserve.live;

public final class LiveFare {
    private final boolean known;
    private final Double total;

    public LiveFare(boolean known, Double total) {
        this.known = known;
        this.total = total;
    }

    public boolean isKnown() { return known; }
    public Double getTotal() { return total; }
}
