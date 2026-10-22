package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;
import com.railreserve.adapter.BookingAdapter;
import com.railreserve.model.Booking;
import com.railreserve.service.BookingService;
import com.railreserve.service.BookingServiceFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppSectionActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "app_section";
    public static final String SECTION_TICKETS = "tickets";
    public static final String SECTION_PROFILE = "profile";
    public static final String SECTION_ABOUT = "about";

    private TextView heading;
    private TextView intro;
    private TextView details;
    private LinearLayout ticketActions;
    private LinearLayout ticketCards;
    private TextView ticketStatus;
    private ProgressBar ticketProgress;
    private BookingService bookingService;
    private BookingAdapter ticketAdapter;
    private final ExecutorService ticketExecutor = Executors.newSingleThreadExecutor();
    private String currentSection = SECTION_TICKETS;
    private boolean initialResume = true;
    private boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_app_section);
        View root = findViewById(R.id.appSectionRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), bars.top, view.getPaddingRight(), bars.bottom);
            return insets;
        });

        heading = findViewById(R.id.sectionHeading);
        intro = findViewById(R.id.sectionIntro);
        details = findViewById(R.id.sectionDetails);
        ticketActions = findViewById(R.id.ticketsActions);
        ticketCards = findViewById(R.id.ticketCards);
        ticketStatus = findViewById(R.id.ticketStatus);
        ticketProgress = findViewById(R.id.ticketProgress);
        bookingService = BookingServiceFactory.create(getApplicationContext());
        ticketAdapter = new BookingAdapter(this, null);
        findViewById(R.id.openMyBookings).setOnClickListener(view ->
                startActivity(new Intent(this, MyBookingsActivity.class)));
        findViewById(R.id.openFindBooking).setOnClickListener(view ->
                startActivity(new Intent(this, FindBookingActivity.class)));
        findViewById(R.id.openDemoWorkflow).setOnClickListener(view ->
                startActivity(new Intent(this, com.railreserve.MainActivity.class)));

        String section = state == null
                ? getIntent().getStringExtra(EXTRA_SECTION)
                : state.getString(EXTRA_SECTION, SECTION_TICKETS);
        showSection(section == null ? SECTION_TICKETS : section);
    }

    @Override protected void onResume() {
        super.onResume();
        if (initialResume) {
            initialResume = false;
        } else if (SECTION_TICKETS.equals(currentSection)) {
            loadTicketBookings();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString(EXTRA_SECTION, currentSection);
        super.onSaveInstanceState(out);
    }

    void showSection(String section) {
        currentSection = section;
        if (SECTION_PROFILE.equals(section)) {
            heading.setText(R.string.app_section_profile);
            intro.setText(R.string.profile_intro);
            details.setText(R.string.profile_details);
            details.setVisibility(View.VISIBLE);
            ticketActions.setVisibility(View.GONE);
        } else if (SECTION_ABOUT.equals(section)) {
            heading.setText(R.string.app_section_about);
            intro.setText(R.string.about_intro);
            details.setText(R.string.about_details);
            details.setVisibility(View.VISIBLE);
            ticketActions.setVisibility(View.GONE);
        } else {
            heading.setText(R.string.app_section_tickets);
            intro.setText(R.string.tickets_intro);
            details.setVisibility(View.GONE);
            ticketActions.setVisibility(View.VISIBLE);
            loadTicketBookings();
        }
        AppNavigation.bind(this, section);
    }

    private void loadTicketBookings() {
        ticketProgress.setVisibility(View.VISIBLE);
        ticketStatus.setVisibility(View.VISIBLE);
        ticketStatus.setText(R.string.tickets_loading);
        ticketExecutor.execute(() -> {
            try {
                List<Booking> bookings = bookingService.getBookings();
                runOnUiThread(() -> {
                    if (destroyed || !SECTION_TICKETS.equals(currentSection)) return;
                    ticketProgress.setVisibility(View.GONE);
                    ticketCards.removeAllViews();
                    ticketAdapter.setBookings(bookings);
                    if (bookings.isEmpty()) {
                        ticketCards.setVisibility(View.GONE);
                        ticketStatus.setText(R.string.empty_demo_bookings);
                        ticketStatus.setVisibility(View.VISIBLE);
                        return;
                    }
                    ticketStatus.setText(getString(R.string.tickets_booking_count, bookings.size()));
                    ticketStatus.setVisibility(View.VISIBLE);
                    for (int i = 0; i < ticketAdapter.getCount(); i++) {
                        View card = ticketAdapter.getView(i, null, ticketCards);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (i > 0) params.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
                        ticketCards.addView(card, params);
                    }
                    ticketCards.setVisibility(View.VISIBLE);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (destroyed || !SECTION_TICKETS.equals(currentSection)) return;
                    ticketProgress.setVisibility(View.GONE);
                    ticketCards.removeAllViews();
                    ticketCards.setVisibility(View.GONE);
                    ticketStatus.setText(exception.getMessage() == null
                            ? getString(R.string.tickets_load_failed)
                            : exception.getMessage());
                    ticketStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        ticketExecutor.shutdownNow();
        super.onDestroy();
    }
}
