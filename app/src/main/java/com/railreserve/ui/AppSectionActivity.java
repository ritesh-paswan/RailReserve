package com.railreserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.railreserve.R;

public final class AppSectionActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "app_section";
    public static final String SECTION_TICKETS = "tickets";
    public static final String SECTION_PROFILE = "profile";
    public static final String SECTION_ABOUT = "about";

    private TextView heading;
    private TextView intro;
    private TextView details;
    private LinearLayout ticketActions;
    private String currentSection = SECTION_TICKETS;

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
        }
        AppNavigation.bind(this, section);
    }
}
