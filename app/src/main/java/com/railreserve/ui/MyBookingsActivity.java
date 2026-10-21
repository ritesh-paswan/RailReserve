package com.railreserve.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.BookingAdapter;
import com.railreserve.model.Booking;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import com.railreserve.service.BookingHistoryOrganizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyBookingsActivity extends AppCompatActivity implements BookingAdapter.CancelListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Booking> allBookings = new ArrayList<>();
    private BookingService service;
    private BookingAdapter adapter;
    private ProgressBar progress;
    private TextView status;
    private Button emptySearchButton;
    private EditText searchInput;
    private Spinner statusSpinner;
    private Spinner sortSpinner;
    private boolean destroyed;
    private boolean cancellationInProgress;
    private boolean cancellationDialogShowing;
    private boolean bookingsLoaded;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_my_bookings);
        View root = findViewById(R.id.bookingsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(20 + bars.left, 20 + bars.top, 20 + bars.right, 20 + bars.bottom);
            return insets;
        });

        service = BookingServiceFactory.create(getApplicationContext());
        ListView list = findViewById(R.id.bookingsList);
        progress = findViewById(R.id.bookingsProgress);
        status = findViewById(R.id.bookingsStatus);
        emptySearchButton = findViewById(R.id.emptySearchButton);
        searchInput = findViewById(R.id.bookingSearchInput);
        statusSpinner = findViewById(R.id.bookingStatusSpinner);
        sortSpinner = findViewById(R.id.bookingSortSpinner);
        adapter = new BookingAdapter(this, this);
        list.setAdapter(adapter);
        emptySearchButton.setOnClickListener(view -> finish());
        configureFilters();
    }

    private void configureFilters() {
        ArrayAdapter<String> statuses = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All", "Upcoming", "Completed", "Cancelled"});
        statuses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statuses);

        ArrayAdapter<String> sorting = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Nearest journey first", "Most recent journey first"});
        sorting.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sorting);

        AdapterView.OnItemSelectedListener changed = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bookingsLoaded) renderBookings();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        statusSpinner.setOnItemSelectedListener(changed);
        sortSpinner.setOnItemSelectedListener(changed);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (bookingsLoaded) renderBookings();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        loadBookings();
    }

    private void loadBookings() {
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        emptySearchButton.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<Booking> bookings = service.getBookings();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    allBookings.clear();
                    if (bookings != null) allBookings.addAll(bookings);
                    bookingsLoaded = true;
                    renderBookings();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progress.setVisibility(View.GONE);
                    emptySearchButton.setVisibility(View.GONE);
                    showStatus(exception.getMessage() == null
                            ? "Could not load your bookings. Please try again."
                            : exception.getMessage());
                });
            }
        });
    }

    private void renderBookings() {
        String query = searchInput.getText().toString().trim();
        int selectedFilter = statusSpinner.getSelectedItemPosition();
        BookingHistoryOrganizer.StatusFilter[] filters = BookingHistoryOrganizer.StatusFilter.values();
        BookingHistoryOrganizer.JourneySort[] sorts = BookingHistoryOrganizer.JourneySort.values();
        BookingHistoryOrganizer.StatusFilter filter = selectedFilter >= 0 && selectedFilter < filters.length
                ? filters[selectedFilter] : BookingHistoryOrganizer.StatusFilter.ALL;
        int selectedSort = sortSpinner.getSelectedItemPosition();
        BookingHistoryOrganizer.JourneySort sort = selectedSort >= 0 && selectedSort < sorts.length
                ? sorts[selectedSort] : BookingHistoryOrganizer.JourneySort.NEAREST_FIRST;
        List<Booking> visible = BookingHistoryOrganizer.organize(
                allBookings, query, filter, sort, LocalDate.now());
        adapter.setBookings(visible);
        emptySearchButton.setVisibility(View.GONE);

        if (allBookings.isEmpty()) {
            showStatus(getString(R.string.empty_demo_bookings));
            emptySearchButton.setVisibility(View.VISIBLE);
        } else if (visible.isEmpty()) {
            showStatus(emptyMessage(filter, query));
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private String emptyMessage(BookingHistoryOrganizer.StatusFilter filter, String query) {
        if (!query.isEmpty()) return "No bookings match your search and selected status filter.";
        if (filter == BookingHistoryOrganizer.StatusFilter.UPCOMING) return "No upcoming bookings.";
        if (filter == BookingHistoryOrganizer.StatusFilter.COMPLETED) return "No completed bookings.";
        if (filter == BookingHistoryOrganizer.StatusFilter.CANCELLED) return "No cancelled bookings.";
        return "No bookings match the selected filters.";
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    @Override public void onCancel(Booking booking) {
        if (cancellationInProgress || cancellationDialogShowing) return;
        cancellationDialogShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cancel this reservation?")
                .setMessage("Demo PNR " + booking.getPnr() + "\n"
                        + booking.getTrainName() + "\n"
                        + booking.getJourneyDate() + "  |  "
                        + booking.getCoach() + " Seat " + booking.getSeatNumber()
                        + "\nThe seat will be released if cancellation succeeds.")
                .setNegativeButton("Keep booking", null)
                .setPositiveButton("Cancel reservation", null)
                .create();
        dialog.setOnDismissListener(ignored -> cancellationDialogShowing = false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (cancellationInProgress) return;
                    dialog.dismiss();
                    performCancellation(booking);
                }));
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff9a3328);
    }

    private void performCancellation(Booking booking) {
        cancellationInProgress = true;
        adapter.setCancellationInProgress(true);
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                service.cancelBooking(booking.getId());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    cancellationInProgress = false;
                    adapter.setCancellationInProgress(false);
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Booking cancelled. The seat is available again.",
                            Toast.LENGTH_SHORT).show();
                    loadBookings();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    cancellationInProgress = false;
                    adapter.setCancellationInProgress(false);
                    progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(this)
                            .setTitle("Cancellation not completed")
                            .setMessage(exception.getMessage() == null
                                    ? "The booking could not be cancelled."
                                    : exception.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    loadBookings();
                });
            }
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
