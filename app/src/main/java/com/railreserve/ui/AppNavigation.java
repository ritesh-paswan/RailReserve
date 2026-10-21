package com.railreserve.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.widget.Button;
import androidx.core.content.ContextCompat;
import com.railreserve.R;

final class AppNavigation {
    private AppNavigation() { }

    static void bind(Activity activity, String selectedSection) {
        bindItem(activity, R.id.navHome, "home", selectedSection);
        bindItem(activity, R.id.navTickets, AppSectionActivity.SECTION_TICKETS, selectedSection);
        bindItem(activity, R.id.navProfile, AppSectionActivity.SECTION_PROFILE, selectedSection);
        bindItem(activity, R.id.navAbout, AppSectionActivity.SECTION_ABOUT, selectedSection);
    }

    private static void bindItem(Activity activity, int id, String section, String selectedSection) {
        Button button = activity.findViewById(id);
        boolean selected = section.equals(selectedSection);
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(activity,
                selected ? R.color.teal_soft : R.color.surface)));
        button.setTextColor(ContextCompat.getColor(activity,
                selected ? R.color.brand_dark : R.color.muted));
        button.setSelected(selected);
        button.setOnClickListener(view -> navigate(activity, section));
    }

    private static void navigate(Activity activity, String section) {
        if ("home".equals(section)) {
            if (activity instanceof AppSectionActivity) {
                Intent intent = new Intent(activity, LiveSearchActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            }
            return;
        }
        if (activity instanceof AppSectionActivity) {
            ((AppSectionActivity) activity).showSection(section);
            return;
        }
        Intent intent = new Intent(activity, AppSectionActivity.class);
        intent.putExtra(AppSectionActivity.EXTRA_SECTION, section);
        activity.startActivity(intent);
    }
}
