package com.railreserve.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.live.BackendException;
import com.railreserve.live.LiveDataRepository;
import com.railreserve.live.LiveStation;
import com.railreserve.live.StationSuggestionMatcher;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LiveSearchActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_CODE = "live_source_code";
    public static final String EXTRA_SOURCE_NAME = "live_source_name";
    public static final String EXTRA_DESTINATION_CODE = "live_destination_code";
    public static final String EXTRA_DESTINATION_NAME = "live_destination_name";
    public static final String EXTRA_DATE = "live_journey_date";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LiveDataRepository repository = LiveDataRepository.getInstance();
    private StationInput sourceInput, destinationInput;
    private Button dateButton, searchButton;
    private LocalDate selectedDate = LocalDate.now();
    private boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live_search);
        View root = findViewById(R.id.liveSearchRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top + 12, view.getPaddingRight(), bars.bottom + 12);
            return insets;
        });

        sourceInput = new StationInput(findViewById(R.id.liveSourceInput), findViewById(R.id.liveSourceStatus));
        destinationInput = new StationInput(findViewById(R.id.liveDestinationInput), findViewById(R.id.liveDestinationStatus));
        AppNavigation.bind(this, "home");
        dateButton = findViewById(R.id.liveDateButton);
        searchButton = findViewById(R.id.liveSearchButton);
        if (state != null) restoreState(state);
        updateDateLabel();
        dateButton.setOnClickListener(view -> showDatePicker());
        findViewById(R.id.liveSwapButton).setOnClickListener(view -> swapStations());
        searchButton.setOnClickListener(view -> search());
    }

    private void restoreState(Bundle state) {
        String savedDate = state.getString("date");
        try { if (savedDate != null) selectedDate = LocalDate.parse(savedDate); }
        catch (RuntimeException ignored) { selectedDate = LocalDate.now(); }
        if (selectedDate.isBefore(LocalDate.now())) selectedDate = LocalDate.now();
        restoreInput(sourceInput, state, "source");
        restoreInput(destinationInput, state, "destination");
    }

    private void restoreInput(StationInput input, Bundle state, String prefix) {
        String code = state.getString(prefix + "Code");
        String name = state.getString(prefix + "Name");
        String city = state.getString(prefix + "City");
        String text = state.getString(prefix + "Text", "");
        if (code != null && name != null) input.setSelected(new LiveStation(code, name, city));
        else input.view.setText(text);
    }

    private void showDatePicker() {
        LocalDate today = LocalDate.now();
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate = LocalDate.of(year, month + 1, day);
            updateDateLabel();
        }, selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth());
        picker.getDatePicker().setMinDate(today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.getDatePicker().setMaxDate(today.plusDays(14).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.show();
    }

    private void updateDateLabel() {
        dateButton.setText("DATE  |  " + selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())));
    }

    private void swapStations() {
        LiveStation from = sourceInput.selected;
        LiveStation to = destinationInput.selected;
        String fromText = sourceInput.view.getText().toString();
        String toText = destinationInput.view.getText().toString();
        sourceInput.setSelected(to);
        destinationInput.setSelected(from);
        if (to == null) sourceInput.view.setText(toText);
        if (from == null) destinationInput.view.setText(fromText);
    }

    private void search() {
        LiveStation from = sourceInput.selected;
        LiveStation to = destinationInput.selected;
        if (from == null || !from.toString().contentEquals(sourceInput.view.getText())) {
            sourceInput.view.setError("Choose a station from the live suggestions.");
            sourceInput.view.requestFocus();
            return;
        }
        if (to == null || !to.toString().contentEquals(destinationInput.view.getText())) {
            destinationInput.view.setError("Choose a station from the live suggestions.");
            destinationInput.view.requestFocus();
            return;
        }
        if (from.getCode().equalsIgnoreCase(to.getCode())) {
            destinationInput.view.setError("Choose a different destination station.");
            destinationInput.view.requestFocus();
            return;
        }
        Intent intent = new Intent(this, LiveTrainResultsActivity.class);
        intent.putExtra(EXTRA_SOURCE_CODE, from.getCode());
        intent.putExtra(EXTRA_SOURCE_NAME, from.getName());
        intent.putExtra(EXTRA_DESTINATION_CODE, to.getCode());
        intent.putExtra(EXTRA_DESTINATION_NAME, to.getName());
        intent.putExtra(EXTRA_DATE, selectedDate.toString());
        startActivity(intent);
    }

    private void setupSelectionListener(StationInput input) {
        input.view.setOnItemClickListener((parent, view, position, id) -> {
            LiveStation station = input.adapter.getItem(position);
            if (station == null) {
                setStatus(input, "That station suggestion is no longer available. Search again.", false);
                return;
            }
            input.generation++;
            if (input.pending != null) handler.removeCallbacks(input.pending);
            input.setSelected(station);
            input.view.dismissDropDown();
            input.status.setText("Selected station code: " + station.getCode());
        });
    }

    private void saveInput(Bundle out, StationInput input, String prefix) {
        out.putString(prefix + "Text", input.view.getText().toString());
        if (input.selected != null) {
            out.putString(prefix + "Code", input.selected.getCode());
            out.putString(prefix + "Name", input.selected.getName());
            out.putString(prefix + "City", input.selected.getCity());
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("date", selectedDate.toString());
        saveInput(out, sourceInput, "source");
        saveInput(out, destinationInput, "destination");
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class StationInput {
        final AutoCompleteTextView view;
        final TextView status;
        final StationAdapter adapter;
        LiveStation selected;
        Runnable pending;
        int generation;

        StationInput(AutoCompleteTextView view, TextView status) {
            this.view = view;
            this.status = status;
            adapter = new StationAdapter();
            view.setAdapter(adapter);
            view.setThreshold(2);
            status.setOnClickListener(ignored -> retryStationSearch(this));
            view.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable editable) { onQueryChanged(StationInput.this, editable.toString()); }
            });
            setupSelectionListener(this);
        }

        void setSelected(LiveStation station) {
            selected = station;
            if (station != null) view.setText(station.toString(), false);
            else adapter.clearStations();
        }
    }

    private final class StationAdapter extends BaseAdapter implements Filterable {
        private final LayoutInflater inflater = LayoutInflater.from(LiveSearchActivity.this);
        private final ArrayList<LiveStation> stations = new ArrayList<>();
        private final ArrayList<LiveStation> visibleStations = new ArrayList<>();
        private final Filter stationFilter = new Filter() {
            @Override protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint == null ? "" : constraint.toString();
                ArrayList<LiveStation> matches = new ArrayList<>();
                synchronized (stations) {
                    for (LiveStation station : stations) {
                        if (StationSuggestionMatcher.matches(station, query)) matches.add(station);
                    }
                }
                FilterResults results = new FilterResults();
                results.values = matches;
                results.count = matches.size();
                return results;
            }

            @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                ArrayList<LiveStation> matches = new ArrayList<>();
                if (results.values instanceof ArrayList<?>) {
                    for (Object item : (ArrayList<?>) results.values) {
                        if (item instanceof LiveStation) matches.add((LiveStation) item);
                    }
                }
                synchronized (visibleStations) {
                    visibleStations.clear();
                    visibleStations.addAll(matches);
                }
                notifyDataSetChanged();
            }
        };

        @Override public int getCount() {
            synchronized (visibleStations) {
                return visibleStations.size();
            }
        }

        @Override public LiveStation getItem(int position) {
            synchronized (visibleStations) {
                return position >= 0 && position < visibleStations.size()
                        ? visibleStations.get(position)
                        : null;
            }
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView
                    ? (TextView) convertView
                    : (TextView) inflater.inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
            LiveStation station = getItem(position);
            row.setText(station == null ? "" : station.getLabel());
            return row;
        }

        @Override public Filter getFilter() {
            return stationFilter;
        }

        LiveStation findByLabel(String text) {
            synchronized (stations) {
                for (LiveStation station : stations) {
                    if (station.getLabel().equalsIgnoreCase(text.trim())) return station;
                }
            }
            return null;
        }

        void setStations(java.util.List<LiveStation> results) {
            synchronized (stations) {
                stations.clear();
                stations.addAll(results);
            }
            synchronized (visibleStations) {
                visibleStations.clear();
                visibleStations.addAll(results);
            }
            notifyDataSetChanged();
        }

        void clearStations() {
            synchronized (stations) {
                stations.clear();
            }
            synchronized (visibleStations) {
                visibleStations.clear();
            }
            notifyDataSetChanged();
        }
    }

    private void onQueryChanged(StationInput input, String text) {
        if (input.selected != null && input.selected.toString().contentEquals(text)) return;
        LiveStation exactMatch = input.adapter.findByLabel(text);
        if (exactMatch != null) {
            input.selected = exactMatch;
            input.generation++;
            if (input.pending != null) handler.removeCallbacks(input.pending);
            input.status.setText("Selected station code: " + exactMatch.getCode());
            return;
        }
        input.selected = null;
        input.generation++;
        int requestGeneration = input.generation;
        if (input.pending != null) handler.removeCallbacks(input.pending);
        input.adapter.clearStations();
        String query = text.trim();
        if (query.length() < 2) {
            setStatus(input, "Type at least 2 characters to search stations.", false);
            return;
        }
        input.pending = () -> {
            if (destroyed || input.generation != requestGeneration) return;
            requestStations(input, query, requestGeneration);
        };
        handler.postDelayed(input.pending, 450);
    }

    private void retryStationSearch(StationInput input) {
        String query = input.view.getText().toString().trim();
        if (!input.status.isClickable() || query.length() < 2 || destroyed) return;
        input.generation++;
        requestStations(input, query, input.generation);
    }

    private void requestStations(StationInput input, String query, int requestGeneration) {
        setStatus(input, "Searching live station suggestions...", false);
        try {
            executor.execute(() -> {
                java.util.List<LiveStation> stations = null;
                String error = null;
                try {
                    stations = repository.searchStations(query);
                } catch (BackendException exception) {
                    error = exception.getMessage() == null
                            ? "Could not load station suggestions."
                            : exception.getMessage();
                } catch (Exception exception) {
                    // Keep unexpected request failures visible and recoverable without logging response data.
                    error = "Could not load station suggestions. Tap here to retry.";
                }
                final java.util.List<LiveStation> result = stations;
                final String failure = error;
                runOnUiThread(() -> {
                    if (destroyed || input.generation != requestGeneration
                            || !input.view.getText().toString().trim().equals(query)) return;
                    if (failure == null) {
                        input.adapter.setStations(result);
                        setStatus(input, result.isEmpty()
                                ? "No station matches. Check the name or code."
                                : "Select a station name and code from the suggestions.", false);
                        if (!result.isEmpty() && input.view.hasFocus()) input.view.showDropDown();
                    } else {
                        setStatus(input, failure + " Tap here to retry.", true);
                    }
                });
            });
        } catch (RuntimeException exception) {
            if (!destroyed && input.generation == requestGeneration) {
                setStatus(input, "Could not load station suggestions. Tap here to retry.", true);
            }
        }
    }

    private void setStatus(StationInput input, String message, boolean retryable) {
        input.status.setText(message);
        input.status.setClickable(retryable);
        input.status.setFocusable(retryable);
    }
}
