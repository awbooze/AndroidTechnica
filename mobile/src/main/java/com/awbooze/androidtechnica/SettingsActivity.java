/* Copyright 2018 Andrew Booze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.awbooze.androidtechnica;

import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v14.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.app.DialogFragment;
import android.support.v4.app.NavUtils;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.MenuItem;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The main class for the settings activity. Displays a list of settings and allows them to be
 * clicked on to modify them using a PreferenceFragment.
 */

public class SettingsActivity extends AppCompatActivity {

    private Toolbar appToolbar;
    private ActionBar appActionBar;
    private final long day = 86400000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the default view and add the title bar
        setContentView(R.layout.activity_settings);
        appToolbar = findViewById(R.id.toolbar);
        appToolbar.setTitle(R.string.settings_title);
        setSupportActionBar(appToolbar);
        appActionBar = getSupportActionBar();
        //appActionBar.setTitle(R.string.settings_title);
        appActionBar.setDisplayShowHomeEnabled(true);
        appActionBar.setDisplayHomeAsUpEnabled(true);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(R.id.preference_content, new SettingsFragment()).commit();
    }

    /**
     * The settings fragment for this activity. Displays a list of settings and allows them to be
     * clicked on to modify them using a PreferenceFragment.
     */

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            //Get SharedPreferences instance.
            final SharedPreferences sharedSettings = PreferenceManager.getDefaultSharedPreferences(getActivity());

            //Set the description of the Home URL setting to the value of the URL.
            Preference homeURLPreference = findPreference("key_url");
            homeURLPreference.setSummary(sharedSettings.getString("key_url", ""));

            //Begin preferences about notifications

            // When the preference is clicked, will open the notification settings for this app
            Preference notificationSettingsPreference = findPreference("notification_preference");
            notificationSettingsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Intent notificationIntent = new Intent();
                    notificationIntent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");

                    //for Android 5-7
                    notificationIntent.putExtra("app_package", getContext().getPackageName());
                    notificationIntent.putExtra("app_uid", getContext().getApplicationInfo().uid);

                    // for Android 8
                    notificationIntent.putExtra("android.provider.extra.APP_PACKAGE", getContext().getPackageName());

                    //Start intent
                    startActivity(notificationIntent);
                    return true;
                }
            });

            // Begin preferences relating to developer settings

            // When the preference is clicked, the app will post a sample notification
            Preference testNotificationPreference = findPreference("new_test_notification");
            testNotificationPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Intent settingsIntent = new Intent(getContext(), SettingsActivity.class);
                    // Create the TaskStackBuilder and add the intent, which inflates the back stack
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(getContext());
                    stackBuilder.addNextIntentWithParentStack(settingsIntent);
                    // Get the PendingIntent containing the entire back stack
                    PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());

                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext(), "newsChannel")
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(Html.fromHtml(getResources().getString(R.string.test_notification_large_text))))
                            .setColor(ContextCompat.getColor(getContext(), R.color.colorAccent))
                            .setSmallIcon(R.drawable.at_notification)
                            .setContentTitle(Html.fromHtml(getResources().getString(R.string.test_notification_title)))
                            .setContentText(Html.fromHtml(getResources().getString(R.string.test_notification_small_text)))
                            .setSubText(getResources().getString(R.string.news))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true);

                    // notificationId is a unique int for each notification that you must define
                    notificationManager.notify(0, notificationBuilder.build());

                    return false;
                }
            });

            // Sets the summary text for the preference that allows you to check the most recent
            // date notifications have been checked for.
            Preference RSSCheckDatePreference = findPreference("least_recent_date");
            DateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss a z", Locale.US);
            dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());
            Date leastRecentDate = new Date(Long.parseLong(sharedSettings.getString("least_recent_date", String.valueOf(Calendar.getInstance().getTimeInMillis() - 86400000))));
            RSSCheckDatePreference.setSummary(leastRecentDate.toString());

            // When this preference is clicked, it sets back the least_recent_date preference by an hour
            Preference setBackHourPreference = findPreference("rss_dev_minus_hour");
            setBackHourPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Long date = Long.parseLong(sharedSettings.getString("least_recent_date", String.valueOf(Calendar.getInstance().getTimeInMillis() - 3600000)));
                    date = date - 3600000;
                    SharedPreferences.Editor sharedSettingsEditor = sharedSettings.edit();
                    sharedSettingsEditor.putString("least_recent_date", String.valueOf(date));
                    sharedSettingsEditor.apply();
                    return false;
                }
            });

            // When this preference is clicked, it sets back the least_recent_date preference by a day
            Preference setBackDayPreference = findPreference("rss_dev_minus_day");
            setBackDayPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    Long date = Long.parseLong(sharedSettings.getString("least_recent_date", String.valueOf(Calendar.getInstance().getTimeInMillis() - 86400000)));
                    date = date - 86400000;
                    SharedPreferences.Editor sharedSettingsEditor = sharedSettings.edit();
                    sharedSettingsEditor.putString("least_recent_date", String.valueOf(date));
                    sharedSettingsEditor.apply();
                    return false;
                }
            });

            // When this preference is clicked, it opens up a dialog that shows the open source projects
            // used in this app.
            Preference licensePreference = findPreference("key_open_licenses");
            licensePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    DialogFragment licensesDialog = new LicensesDialogFragment();
                    licensesDialog.show(getFragmentManager(), "LicensesDialog");
                    return false;
                }
            });
        }
    }

    /**
     * Creates a DialogFragment to display a dialog which displays the open source projects used in
     * this app.
     */

    public static class LicensesDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            // Get the layout inflater
            LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(inflater.inflate(R.layout.dialog_licenses, null));
            builder.setTitle(R.string.pref_about_open_licenses);
                    // Add action buttons
            builder.setPositiveButton(R.string.licenses_okay, null);
            return builder.create();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}