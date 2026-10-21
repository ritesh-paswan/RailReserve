package com.railreserve.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.model.Booking;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FindBookingActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText pnrInput;
    private Button lookupButton;
    private ProgressBar progress;
    private TextView message, resultPnr, resultStatus, train, route, dateTime, passenger, seat, fare, cancelledNote;
    private View resultCard;
    private BookingService service;
    private boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_find_booking);
        View root = findViewById(R.id.findBookingRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top + 16, v.getPaddingRight(), bars.bottom + 16);
            return insets;
        });
        service = BookingServiceFactory.create(getApplicationContext());
        pnrInput = findViewById(R.id.pnrInput);
        lookupButton = findViewById(R.id.lookupButton);
        progress = findViewById(R.id.lookupProgress);
        message = findViewById(R.id.lookupMessage);
        resultCard = findViewById(R.id.lookupResultCard);
        resultPnr = findViewById(R.id.lookupResultPnr);
        resultStatus = findViewById(R.id.lookupResultStatus);
        train = findViewById(R.id.lookupTrain);
        route = findViewById(R.id.lookupRoute);
        dateTime = findViewById(R.id.lookupDateTime);
        passenger = findViewById(R.id.lookupPassenger);
        seat = findViewById(R.id.lookupSeat);
        fare = findViewById(R.id.lookupFare);
        cancelledNote = findViewById(R.id.lookupCancelledNote);
        findViewById(R.id.lookupBackButton).setOnClickListener(v -> finish());
        lookupButton.setOnClickListener(v -> lookup());
        pnrInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                resultCard.setVisibility(View.GONE);
                message.setVisibility(View.GONE);
                pnrInput.setError(null);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void lookup() {
        String pnr = pnrInput.getText().toString().trim();
        if (pnr.isEmpty()) {
            pnrInput.setError("Enter your 10-digit PNR.");
            pnrInput.requestFocus();
            return;
        }
        if (!pnr.matches("[0-9]{10}")) {
            pnrInput.setError("Enter the 10-digit numeric PNR from your confirmation.");
            pnrInput.requestFocus();
            return;
        }
        resultCard.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        pnrInput.setError(null);
        lookupButton.setEnabled(false);
        lookupButton.setText("Searching...");
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                Booking booking = service.findBookingByPnr(pnr);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (booking == null) showMessage("No booking found for this PNR. Check it and try again.", false);
                    else showBooking(booking);
                    finishLookup();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    showMessage("We couldn't look up this booking. Please try again.", true);
                    finishLookup();
                });
            }
        });
    }

    private void finishLookup() {
        progress.setVisibility(View.GONE);
        lookupButton.setEnabled(true);
        lookupButton.setText("Find booking");
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setTextColor(error ? 0xFFB42318 : getColor(R.color.muted));
        message.setVisibility(View.VISIBLE);
    }

    private void showBooking(Booking booking) {
        resultPnr.setText("DEMO PNR  " + booking.getPnr());
        boolean cancelled = "CANCELLED".equalsIgnoreCase(booking.getStatus());
        resultStatus.setText(cancelled ? "CANCELLED" : booking.getStatus());
        resultStatus.setBackgroundResource(cancelled ? R.drawable.status_cancelled : R.drawable.status_confirmed);
        train.setText(booking.getTrainName() + "  ·  " + booking.getTrainNumber());
        route.setText(booking.getSource() + "  →  " + booking.getDestination());
        dateTime.setText("Journey date  " + booking.getJourneyDate() + "\n"
                + "Departure  " + booking.getDeparture() + "     Arrival  " + booking.getArrival()
                + (booking.getArrivalDayOffset() > 0 ? " (next day)" : ""));
        passenger.setText(booking.getPassengerName() + "  ·  " + booking.getAge() + " years  ·  " + booking.getGender());
        seat.setText(booking.getTravelClass().getLabel() + "  ·  " + booking.getCoach() + " " + booking.getSeatNumber());
        fare.setText("₹ " + booking.getFare());
        cancelledNote.setVisibility(cancelled ? View.VISIBLE : View.GONE);
        resultCard.setVisibility(View.VISIBLE);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
