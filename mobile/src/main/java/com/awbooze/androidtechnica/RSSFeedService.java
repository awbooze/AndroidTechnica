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

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Creats a job that will fetch RSS feeds from Ars Technica's RSS feeds about different topics.
 * It then formats them into notifications and displays them.
 */

public class RSSFeedService extends JobService {

    // Define variables for SettingsActivity
    private int notificationID = 1;
    private int RSSLength = 0;
    private String RSSURL;
    private String articleURL;
    private CharSequence articleTitle;
    private CharSequence articleText;
    private String bigText;
    private String channelID;
    private String channelTitle;
    private String[] RSSSelectArray;
    private String[] RSSTitleArray;
    private String[] RSSChannelArray;
    private Date leastRecentDate;
    private Date articleDate;
    private DateFormat dateFormat;
    private SharedPreferences sharedSettings;

    @Override
    public boolean onStartJob(final JobParameters params) {

        // Get the settings that define what notifications are shown
        sharedSettings = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> RSSURLSet = sharedSettings.getStringSet("rss_feeds_subscribed", null);
        RSSTitleArray = getResources().getStringArray(R.array.pref_poll_selections_titles);
        RSSChannelArray = getResources().getStringArray(R.array.pref_poll_channels_titles);

        if (RSSURLSet == null) {    //If the user isn't subscribed to anything
            finished(params);       //End the job
        }
        else {                      //If the user is subscribed to something
            RSSSelectArray = RSSURLSet.toArray(new String[0]);  //Transform the set of rss feeds into an array
            RSSLength = RSSSelectArray.length;                  //Get the array's length
        }

        // Initialize DateFormat object with the default date formatting
        dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        dateFormat.setTimeZone(Calendar.getInstance().getTimeZone());

        // Get the post date that the service should stop displaying notifications after
        leastRecentDate = new Date(Long.parseLong(sharedSettings.getString("least_recent_date",
                String.valueOf(Calendar.getInstance().getTimeInMillis() - 86400000))));

        // Create a thread for each channel the user is subscribed to
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < RSSLength; i++) {

                    RSSURL = RSSSelectArray[i];

                    switch (RSSURL) {
                        case "http://feeds.arstechnica.com/arstechnica/technology-lab":     //IT
                            channelID = RSSChannelArray[0];
                            channelTitle = RSSTitleArray[0];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/gadgets":    //Tech
                            channelID = RSSChannelArray[1];
                            channelTitle = RSSTitleArray[1];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/business":
                            channelID = RSSChannelArray[2];
                            channelTitle = RSSTitleArray[2];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/security":
                            channelID = RSSChannelArray[3];
                            channelTitle = RSSTitleArray[3];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/tech-policy":
                            channelID = RSSChannelArray[4];
                            channelTitle = RSSTitleArray[4];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/gaming":     //Gaming and culture
                            channelID = RSSChannelArray[5];
                            channelTitle = RSSTitleArray[5];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/science":
                            channelID = RSSChannelArray[6];
                            channelTitle = RSSTitleArray[6];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/multiverse": //Sci-fi (seems to be depreciated in favor of gaming)
                            channelID = RSSChannelArray[7];
                            channelTitle = RSSTitleArray[7];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/cars":
                            channelID = RSSChannelArray[8];
                            channelTitle = RSSTitleArray[8];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/staff-blogs":
                            channelID = RSSChannelArray[9];
                            channelTitle = RSSTitleArray[9];
                            break;
                        case "http://feeds.arstechnica.com/arstechnica/cardboard":  //Board Games
                            channelID = RSSChannelArray[10];
                            channelTitle = RSSTitleArray[10];
                            break;
                    }

                    notifyThread(params, RSSURL, channelID, channelTitle);
                }
            }
        });

        //thread.setPriority(Process.BACKGROUND);
        thread.start();

        boolean toastWhenRunning = sharedSettings.getBoolean("toast_when_service_runs", false);
        if (toastWhenRunning) {
            Toast.makeText(this, R.string.toast_when_service_runs_message, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public void notifyThread(JobParameters params, String url, String channel, String channelName) {

        try {
            // Create XmlPullParserFactory and XmlPullParser
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputStream input = new URL(url).openStream();

            XmlPullParser xmlParser = factory.newPullParser();
            xmlParser.setInput(input, "utf-8");

            boolean insideItem = false;

            // Returns the type of current event: START_TAG, END_TAG, etc..
            int eventType = xmlParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {

                    // If the name of the tag is item, then you are inside an item
                    if (xmlParser.getName().equalsIgnoreCase("item")) {
                        insideItem = true;
                    }
                    // If the item is a link
                    else if (xmlParser.getName().equalsIgnoreCase("link")) {
                        if (insideItem) {
                            articleURL = xmlParser.nextText();  // extract the link of the article
                        }
                    }
                    // If the item is a title
                    else if (xmlParser.getName().equalsIgnoreCase("title")) {
                        if (insideItem) {
                            String articleTitleString = xmlParser.nextText(); // extract the headline

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                articleTitle = Html.fromHtml(articleTitleString, Html.FROM_HTML_MODE_LEGACY);
                            } else {
                                articleTitle = Html.fromHtml(articleTitleString);
                            }
                            bigText = articleTitleString;
                        }
                    }
                    // If the item is the article description
                    else if (xmlParser.getName().equalsIgnoreCase("description")) {
                        if (insideItem) {
                            String articleTextString = xmlParser.nextText(); //Extract the article description

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                articleText = Html.fromHtml(articleTextString, Html.FROM_HTML_MODE_LEGACY);
                            } else {
                                articleText = Html.fromHtml(articleTextString);
                            }
                            bigText = bigText + "<br>" + articleTextString;
                        }
                    }
                    // If the item is the published date
                    else if (xmlParser.getName().equalsIgnoreCase("pubDate")) {
                        if (insideItem) {
                            try {
                                articleDate = dateFormat.parse(xmlParser.nextText()); //Extract the article date
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                // Else if it is an end tag for an item, then you are not inside an item
                else if (eventType == XmlPullParser.END_TAG && xmlParser.getName().equalsIgnoreCase("item")) {
                    insideItem = false;

                    // If the time the article was published is after the job was last ran
                    if (articleDate.getTime() > leastRecentDate.getTime()) {

                        // Add the most recent notification content for developers to use
                        SharedPreferences.Editor sharedSettingsEditor = sharedSettings.edit();
                        sharedSettingsEditor.putString("rss_dev_most_recent_notification_content", articleTitle + "\n" + articleText + "\n" + articleURL);
                        sharedSettingsEditor.apply();

                        // Create an intent for the article and add everything to it.
                        Intent articleIntent = new Intent(getApplicationContext(), MainActivity.class);
                        articleIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        articleIntent.putExtra("CurrentURL", articleURL);
                        // Create the TaskStackBuilder and add the intent, which inflates the back stack
                        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
                        stackBuilder.addNextIntentWithParentStack(articleIntent);
                        // Get the PendingIntent containing the entire back stack
                        PendingIntent articlePendingIntent = stackBuilder.getPendingIntent(notificationID, PendingIntent.FLAG_UPDATE_CURRENT);

                        // Create an intent for the comments page using the same method
                        Intent commentsIntent = new Intent(getApplicationContext(), MainActivity.class);
                        commentsIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        commentsIntent.putExtra("CurrentURL", articleURL + "&comments=1");
                        stackBuilder.addNextIntentWithParentStack(commentsIntent);
                        // Get the PendingIntent containing the entire back stack
                        PendingIntent commentsPendingIntent = stackBuilder.getPendingIntent(notificationID + 100, PendingIntent.FLAG_UPDATE_CURRENT);

                        /*Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, articleURL);
                        shareIntent.putExtra("Share", true);
                        stackBuilder.addNextIntentWithParentStack(shareIntent);
                        PendingIntent sharePendingIntent = stackBuilder.getPendingIntent(notificationID+200, PendingIntent.FLAG_UPDATE_CURRENT);*/

                        //Build notification
                        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), channel)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(Html.fromHtml(bigText)))
                                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent))
                                .setSmallIcon(R.drawable.at_notification)
                                .setContentTitle(articleTitle)
                                .setContentText(articleText)
                                .setSubText(channelName)
                                .setPriority(NotificationCompat.PRIORITY_LOW)               //Sets priority for Android 7.1 (API 25) and lower
                                .setShowWhen(true)
                                .setWhen(articleDate.getTime())                             //Sets time to the actual time the article was posted.
                                .setContentIntent(articlePendingIntent)
                                .extend(new NotificationCompat.WearableExtender()           //Adds actions to the wearable notification.
                                        .addAction(new NotificationCompat.Action(
                                                R.drawable.ic_smartphone_white_24dp, getString(R.string.notification_action_open_on_phone), articlePendingIntent))
                                        .addAction(new NotificationCompat.Action(
                                                R.drawable.ic_comment_white_24dp, getString(R.string.notification_action_comments), commentsPendingIntent)))
                                .addAction(R.drawable.ic_comment_white_24dp, getString(R.string.notification_action_comments), commentsPendingIntent)
                                //.addAction(R.drawable.ic_share_white_24dp, getString(R.string.notification_action_share), sharePendingIntent)
                                .setAutoCancel(true);                                       //Removes notification when clicked on

                        // Get Notification Manager
                        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

                        // Post the notification
                        notificationManager.notify(notificationID, notificationBuilder.build());
                        notificationID++;       // NotificationID is a unique int for each notification
                    }
                }

                eventType = xmlParser.next();   // Move to next xml element
            }

            input.close();                      // Close input stream

        }
        catch (XmlPullParserException e) {      // Catch the exceptions that may arise
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        // Save the current time as the next value for the leastRecentDate
        SharedPreferences.Editor sharedSettingsEditor = sharedSettings.edit();
        sharedSettingsEditor.putString("least_recent_date", String.valueOf(Calendar.getInstance().getTimeInMillis()));
        sharedSettingsEditor.apply();

        // Stop the job
        finished(params);
    }

    public void finished (JobParameters params) {
        // Call jobFinished so the system can stop the job.
        jobFinished(params, false);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // When the job is stopped before it's finished, return true
        return true;
    }
}