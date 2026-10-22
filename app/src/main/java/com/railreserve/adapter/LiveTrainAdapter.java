package com.railreserve.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import com.railreserve.R;
import com.railreserve.live.LiveTrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LiveTrainAdapter extends ArrayAdapter<LiveTrain> {
    public interface ScheduleClickListener { void onViewSchedule(LiveTrain train); }
    public interface AvailabilityClickListener {
        void onCheckAvailability(LiveTrain train, String classCode);
    }
    public interface FareClickListener {
        void onCheckFare(LiveTrain train, String classCode);
    }
    private final LayoutInflater inflater;
    private final ScheduleClickListener scheduleClickListener;
    private final AvailabilityClickListener availabilityClickListener;
    private final FareClickListener fareClickListener;
    private final Map<String, String> fareByKey = new HashMap<>();
    private final Set<String> checkingFareByKey = new HashSet<>();
    private final Map<String, String> availabilityByKey = new HashMap<>();
    private final Set<String> checkingByKey = new HashSet<>();
    private String selectedClass = "3A";

    public LiveTrainAdapter(Context context, ScheduleClickListener listener,
                            AvailabilityClickListener availabilityListener, FareClickListener fareListener) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
        scheduleClickListener = listener;
        availabilityClickListener = availabilityListener;
        fareClickListener = fareListener;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null ? inflater.inflate(R.layout.item_live_train, parent, false) : convertView;
        LiveTrain train = getItem(position);
        if (train == null) return row;
        ((TextView) row.findViewById(R.id.liveTrainName)).setText(value(train.getName()) + "  ·  " + value(train.getNumber()));
        ((TextView) row.findViewById(R.id.liveTrainType)).setText("Train " + value(train.getNumber()) + "  ·  " + value(train.getType()));
        ((TextView) row.findViewById(R.id.liveTrainDeparture)).setText(value(train.getDeparture()));
        ((TextView) row.findViewById(R.id.liveTrainSource)).setText(label(train.getSource()));
        ((TextView) row.findViewById(R.id.liveTrainDepartureDay)).setText(day(train.getDepartureDay()));
        ((TextView) row.findViewById(R.id.liveTrainArrival)).setText(value(train.getArrival()));
        ((TextView) row.findViewById(R.id.liveTrainDestination)).setText(label(train.getDestination()));
        ((TextView) row.findViewById(R.id.liveTrainArrivalDay)).setText(day(train.getArrivalDay()));
        ((TextView) row.findViewById(R.id.liveTrainDetails)).setText(details(train));
        String fareKey = key(train.getNumber(), selectedClass);
        TextView fare = row.findViewById(R.id.liveTrainFare);
        String fareText = fareByKey.get(fareKey);
        fare.setText(fareText == null ? "Fare estimate not checked." : fareText);
        Button fareButton = row.findViewById(R.id.liveTrainFareButton);
        boolean checkingFare = checkingFareByKey.contains(fareKey);
        fareButton.setEnabled(!checkingFare);
        fareButton.setText(checkingFare ? "Checking fare..." :
                fareText == null ? "Check fare estimate" : "Refresh fare estimate");
        fareButton.setOnClickListener(view -> fareClickListener.onCheckFare(train, selectedClass));
        String availabilityKey = key(train.getNumber(), selectedClass);
        TextView availability = row.findViewById(R.id.liveTrainAvailability);
        String availabilityText = availabilityByKey.get(availabilityKey);
        availability.setText(availabilityText == null ? "Seat availability not checked." : availabilityText);
        Button availabilityButton = row.findViewById(R.id.liveTrainAvailabilityButton);
        boolean checking = checkingByKey.contains(availabilityKey);
        availabilityButton.setEnabled(!checking);
        availabilityButton.setText(checking ? "Checking availability..." :
                availabilityText == null ? "Check seat availability" : "Refresh availability");
        availabilityButton.setOnClickListener(view ->
                availabilityClickListener.onCheckAvailability(train, selectedClass));
        Button schedule = row.findViewById(R.id.liveTrainScheduleButton);
        schedule.setOnClickListener(view -> scheduleClickListener.onViewSchedule(train));
        row.setOnClickListener(view -> scheduleClickListener.onViewSchedule(train));
        return row;
    }

    public void setSelectedClass(String classCode) {
        if (classCode == null || classCode.equals(selectedClass)) return;
        selectedClass = classCode;
        notifyDataSetChanged();
    }

    public void setAvailabilityLoading(String trainNumber, String classCode, boolean loading) {
        String key = key(trainNumber, classCode);
        if (loading) checkingByKey.add(key);
        else checkingByKey.remove(key);
        notifyDataSetChanged();
    }

    public void setFareLoading(String trainNumber, String classCode, boolean loading) {
        String key = key(trainNumber, classCode);
        if (loading) checkingFareByKey.add(key);
        else checkingFareByKey.remove(key);
        notifyDataSetChanged();
    }

    public void setFareResult(String trainNumber, String classCode, String result) {
        String key = key(trainNumber, classCode);
        checkingFareByKey.remove(key);
        fareByKey.put(key, result);
        notifyDataSetChanged();
    }

    public void setAvailabilityResult(String trainNumber, String classCode, String result) {
        String key = key(trainNumber, classCode);
        checkingByKey.remove(key);
        availabilityByKey.put(key, result);
        notifyDataSetChanged();
    }

    private String key(String trainNumber, String classCode) {
        return trainNumber + ":" + classCode;
    }

    private String details(LiveTrain train) {
        List<String> parts = new ArrayList<>();
        parts.add("Duration: " + duration(train.getDurationMinutes()));
        parts.add("Distance: " + (train.getDistanceKm() == null ? "Not provided" : train.getDistanceKm() + " km"));
        parts.add("Halts: " + value(train.getTotalHaltsBetween()));
        if (train.getRunDays() != null && !train.getRunDays().isEmpty()) {
            parts.add("Runs: " + android.text.TextUtils.join(", ", train.getRunDays()));
        }
        return android.text.TextUtils.join("  ·  ", parts);
    }

    private String duration(Integer minutes) {
        if (minutes == null || minutes < 0) return "Not provided";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String label(com.railreserve.live.LiveStation station) {
        return station == null ? "Not provided" : station.getLabel();
    }

    private String day(Integer value) {
        return value == null ? "Day offset: Not provided" : "Day offset: " + value;
    }

    private String value(Object value) {
        return value == null || value.toString().trim().isEmpty() ? "Not provided" : value.toString();
    }
}
