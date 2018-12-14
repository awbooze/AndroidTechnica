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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;

/**
 * The main class for the main activity. Displays Ars Technica in a webView and contains a toolbar
 * aligned to the bottom of the screen, through which can be accessed actions and the settings
 * menu.
 */

public class MainActivity extends AppCompatActivity {

    //Define variables for MainActivity
    private SwipeRefreshLayout pullRefreshLayout;
    private WebView arsWebView;
    private Toolbar appToolbar;
    private ActionBar appActionBar;
    private ProgressBar arsWebProgressBar;
    private Menu bottomMenu;
    private TextView appbarTitle;
    private String homeURL;
    private String currentURL;
    private String headerText;
    private boolean hackActionBarReset = false;
    private boolean isScheduled;
    private boolean findOnPage = false;
    private SharedPreferences sharedSettings;
    private SharedPreferences.Editor sharedSettingsEditor;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedSettingsChangeListener;
    private InstallReceiver installReceiver;
    private ArsWebViewClient aWebViewClient;
    private ArsWebChromeClient aWebChromeClient;
    private FrameLayout customViewContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View aCustomView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // The app is initially set to the theme for the splash screen.
        // Sets the theme to the actual app theme, not the splash screen theme.
        // Make sure this is before calling super.onCreate
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        //Initialize Settings
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedSettings = PreferenceManager.getDefaultSharedPreferences(this);
        homeURL = sharedSettings.getString("key_url", "");
        currentURL = sharedSettings.getString("CurrentURL", homeURL);

        //Set the main activity layout to be the content webview.
        setContentView(R.layout.activity_main);

        //Create the custom view container for videos
        customViewContainer = findViewById(R.id.customViewContainer);

        //Initialize the webview and related services
        arsWebView = findViewById(R.id.arswebview);
        arsWebView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
        registerForContextMenu(arsWebView);
        arsWebView.getSettings().setLoadsImagesAutomatically(true);
        arsWebView.getSettings().setJavaScriptEnabled(true);
        arsWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        aWebViewClient = new ArsWebViewClient();
        arsWebView.setWebViewClient(aWebViewClient);
        aWebChromeClient = new ArsWebChromeClient();
        arsWebView.setWebChromeClient(aWebChromeClient);
        arsWebView.setFindListener(new WebView.FindListener() {
            @Override
            public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
                Toast.makeText(getApplicationContext(), "Matches: " + numberOfMatches, Toast.LENGTH_LONG).show();
            }
        });

        //Initialize the bottom toolbar
        appToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(appToolbar);
        appActionBar = getSupportActionBar();
        appActionBar.setDisplayShowCustomEnabled(true);
        appActionBar.setDisplayShowTitleEnabled(false);

        //Initialize the app title view used to display the current url
        appbarTitle = new TextView(this);
        headerText = currentURL.substring(8);
        appbarTitle.setText(headerText);
        appbarTitle.setTextSize(15);
        appbarTitle.setMaxLines(2);
        appbarTitle.setMovementMethod(new ScrollingMovementMethod());
        appbarTitle.setScrollContainer(true);
        appbarTitle.setVerticalScrollBarEnabled(true);
        appbarTitle.setScrollbarFadingEnabled(true);
        appbarTitle.setHorizontallyScrolling(false);
        appbarTitle.setHorizontalScrollBarEnabled(false);
        appActionBar.setCustomView(appbarTitle);

        //Initialize the progress bar and set to to be visible
        arsWebProgressBar = findViewById(R.id.arsWebProgress);
        arsWebProgressBar.setVisibility(ProgressBar.VISIBLE);

        //Initialize the SwipeRefreshView
        pullRefreshLayout = findViewById(R.id.pullrefresh);
        pullRefreshLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorPrimaryDark);
        /*
        * Sets up a SwipeRefreshLayout.OnRefreshListener that is invoked when the user
        * performs a swipe-to-refresh gesture.
        */
        pullRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                // This method performs the actual data-refresh operation.
                // The method calls setRefreshing(false) when it's finished.
                reload();
            }
        }
        );

        //Call methods to initialize dynamic app shortcuts and notification channels (API 26+; Android 8+)
        initializeDynamicAppShortcuts();
        initializeNotificationChannels();

        //Initialize jobScheduler using the setting for whether the job is already scheduled
        isScheduled = sharedSettings.getBoolean("isScheduled", false);
        initializeJobScheduler(!isScheduled);

        //Load Ars Technica
        if (getIntent().getStringExtra("CurrentURL") != null) {       //Load if from Notification or saved page.

            currentURL = getIntent().getStringExtra("CurrentURL");
            loadURL(currentURL);
        }
        else if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {   //Load if from link or app shortcut

            currentURL = getIntent().getDataString();
            loadURL(currentURL);
        }
        else if (savedInstanceState != null) {                          //Restore from savedInstanceState (may not actually work)
            arsWebView.restoreState(savedInstanceState);
        }
        else {                                                          //Else, load the main Ars Technica webpage
            loadURL(currentURL);
        }
    }

    //Called when the app receives a new intent, such as a pendingIntent from a notification
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        if (intent.getStringExtra("CurrentURL") != null) {      //Load if from notification

            currentURL = intent.getStringExtra("CurrentURL");
            loadURL(currentURL);
        }
        else if (Intent.ACTION_VIEW.equals(intent.getAction())) {     //Load if from link or app shortcut

            currentURL = intent.getDataString();
            loadURL(currentURL);
        }
    }

    @Override
    protected void onStart (){
        super.onStart();

        //Creates a receiver to receive an intent when the app is re-installed
        IntentFilter installIntentFilter = new IntentFilter("android.intent.action.MY_PACKAGE_REPLACED");
        installReceiver = new InstallReceiver();
        registerReceiver(installReceiver, installIntentFilter);     //register the receiver to receive when the package is replaced.

        //Register listener to check if settings changes to know when to re-initialize jobScheduler
        sharedSettingsChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("sync_frequency")) {
                    initializeJobScheduler(true);
                }
                if (key.equals("enable_notifications")) {
                    if (sharedSettings.getBoolean("enable_notifications", false)) {
                        initializeJobScheduler(true);
                    }
                    else {
                        initializeJobScheduler(true);
                    }
                }
            }
        };

        sharedSettings.registerOnSharedPreferenceChangeListener(sharedSettingsChangeListener);
    }

    /**
     * Defines a behavior for a receiver that receives a broadcast when the app is reinstalled.
     */

    public class InstallReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            initializeJobScheduler(true);
        }
    }

    private void initializeNotificationChannels () {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {// Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library

            //Initialize Notification Manager
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            String[] RSSTitleArray = getResources().getStringArray(R.array.pref_poll_selections_titles);
            String[] RSSChannelArray = getResources().getStringArray(R.array.pref_poll_channels_titles);

            for (int i = 0; i < RSSChannelArray.length; i++) {

                CharSequence name = RSSTitleArray[i];
                //String description = getString(R.string.news_channel_description);
                int importance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel channel = new NotificationChannel(RSSChannelArray[i], name, importance);
                //channel.setDescription(description);
                channel.enableLights(true);
                channel.setLightColor(R.color.colorAccent);
                // Register the channel with the system
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeJobScheduler(boolean shouldReschedule) {

        //Define and set the network type the job needs
        int jobNetworkType;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) { //If running on Android N (API 24) or above)
            jobNetworkType = JobInfo.NETWORK_TYPE_NOT_ROAMING;                      //Set the job to require a non-roaming device
        }
        else {                                                                      //Otherwise (Android M/API 23), set it to any network
            jobNetworkType = JobInfo.NETWORK_TYPE_ANY;
        }

        //Get settings for whether the job is enabled and the sync frequency
        boolean enabled = sharedSettings.getBoolean("enable_notifications", false);
        int frequency = Integer.parseInt(sharedSettings.getString("sync_frequency", "60"));

        //Get the settings editor and the job scheduler service
        sharedSettingsEditor = sharedSettings.edit();
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);

        //Build the job info
        JobInfo jobInfo = new JobInfo.Builder(1, new ComponentName(this, RSSFeedService.class))
                .setRequiredNetworkType(jobNetworkType)
                .setPeriodic(60000 * frequency)         //60000 (number of milliseconds in a minute) * frequency, in minutes
                .setPersisted(true)                     //This job will re-start after a reboot
                .build();

        // If the job is already enabled and shouldReschedule is true, then reschedule the job
        //TODO: Make the logic for enabling and disabling the notification job more reasonable
        if (enabled) {
            if (shouldReschedule) {

                jobScheduler.schedule(jobInfo);

                isScheduled = true;             //Do I need this?
                sharedSettingsEditor.putBoolean("isScheduled", shouldReschedule);
                sharedSettingsEditor.apply();
            }
        }
        //Else, remove the job entirely.
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {   //If on Android N (API 24) or above
                if (jobScheduler.getPendingJob(1) != null) {      //If the job exists

                    //Cancel the job
                    jobScheduler.cancel(1);

                    //Save the current status in the app's settings
                    sharedSettingsEditor.putBoolean("isScheduled", false);
                    sharedSettingsEditor.apply();
                }
            }
            else {                                                  //If on Android M (API 23)

                //Get a list of all jobs by the app (it's only started one, though)
                List<JobInfo> jobList = jobScheduler.getAllPendingJobs();

                //If the job exists (this is a failsafe
                if (jobList.contains(jobInfo)) {

                    //Cancel the job
                    jobScheduler.cancel(1);

                    //Save the current status in the app's settings
                    sharedSettingsEditor.putBoolean("isScheduled", false);
                    sharedSettingsEditor.apply();
                }
            }

            Toast.makeText(this, "RSS Notification Service Canceled", Toast.LENGTH_SHORT).show();
        }
    }

    //Creates the app shortcuts for the app
    private void initializeDynamicAppShortcuts() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {   //If using Android 7.1 (API 25) or above

            //Get the shortcut settings and grab the home url from the settings
            ShortcutManager shortcutManager = (ShortcutManager) getSystemService(SHORTCUT_SERVICE);
            String url = sharedSettings.getString("key_url", getResources().getString(R.string.pref_default_url));

            //Create the home icon
            Icon homeIcon;

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                homeIcon = Icon.createWithResource(this, R.mipmap.shortcut_home);
            }
            else {
                homeIcon = Icon.createWithResource(this, R.mipmap.shortcut_home_round);
            }

            //Build and set the home shortcut
            ShortcutInfo homeShortcut = new ShortcutInfo.Builder(this, "homeShortcut")
                    .setShortLabel(getResources().getString(R.string.menu_home))
                    .setIcon(homeIcon)
                    .setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    .build();

            shortcutManager.setDynamicShortcuts(Collections.singletonList(homeShortcut));
        }
    }

    //Called to load a new url
    private void loadURL(String url) {

        arsWebView.loadUrl(url);
    }

    //Called to reload the web page
    private void reload(){

        arsWebView.reload();
        currentURL = arsWebView.getUrl();
        if(pullRefreshLayout.isRefreshing()) {
            pullRefreshLayout.setRefreshing(false);
        }
    }

    /**
     * Creates a customized WebViewClient, which determines the behavior of the internal webView
     */

    private class ArsWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (Uri.parse(url).getHost().equals("arstechnica.com")) {
                // This is Ars Technica, so do not override; let my WebView load the page
                return false;
            }
            else if (Uri.parse(url).getHost().equals("nepsta.com")) {
                return true;
            }
            else {
                // Otherwise, the link is not for a page on Ars, so launch another Activity that handles URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        }
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null) {
                currentURL = url;
                headerText = url.substring(8);
                appbarTitle.setText(headerText);

                sharedSettingsEditor = sharedSettings.edit();
                sharedSettingsEditor.putString("CurrentURL", url);
                sharedSettingsEditor.apply();

                invalidateOptionsMenu();
            }
        }
        @Override
        public void onPageFinished(WebView view, String url) {
            //For when pages are finished loading.
            super.onPageFinished(view, url);
        }
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
        }
    }

    /**
     * Creates a customized WebChromeClient, which determines how the webView looks and acts.
     */

    private class ArsWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int progress) {

            arsWebProgressBar.setProgress(progress);    //Set the visual progressbar to the progress.
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            super.onShowCustomView(view, callback);
            getWindow().getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAG_HIDE_NAVIGATION | SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_IMMERSIVE);
            //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            //If a view already exists, then immediately terminate the new one
            if (aCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }

            //Create the custom view
            aCustomView = view;

            //Hide all the regular views
            arsWebView.setVisibility(View.GONE);
            pullRefreshLayout.setVisibility(View.GONE);
            arsWebProgressBar.setVisibility(View.GONE);
            appToolbar.setVisibility(View.GONE);
            customViewContainer.setVisibility(View.VISIBLE);

            //Adds custom view to the container and shows it
            customViewContainer.addView(view);
            customViewCallback = callback;
        }

        @Override
        public void onHideCustomView() {
            super.onHideCustomView();
            //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

            //If the custom view doesn't currently exist, don't remove it
            if (aCustomView == null) {
                return;
            }

            //Make all regular views visible again
            arsWebView.setVisibility(View.VISIBLE);
            pullRefreshLayout.setVisibility(View.VISIBLE);
            arsWebProgressBar.setVisibility(View.VISIBLE);
            appToolbar.setVisibility(View.VISIBLE);
            customViewContainer.setVisibility(View.GONE);

            //Hide the custom view
            aCustomView.setVisibility(View.GONE);

            //Remove the custom view from its container and set it to null
            customViewContainer.removeView(aCustomView);
            customViewCallback.onCustomViewHidden();
            aCustomView = null;
        }

    }

    //Called when a key is pressed
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:         // User chose to move backward in the page history or exit the video
                    if (aCustomView != null) {      // If in the custom view, then hide it
                        aWebChromeClient.onHideCustomView();
                    }
                    else if (arsWebView.canGoBack()) {   //If the app has web history
                    arsWebView.goBack();            //Then go back
                    }
                    else {
                        super.onBackPressed();      //Do the same as if the back button had normally been pressed.
                    }
                    return true;
            }

        }
        return super.onKeyDown(keyCode, event);     //Return with the same as if the button had normally been pressed.
    }

    //Called when the app needs to create a context menu
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(menu, view, menuInfo);
        final WebView.HitTestResult hitTestResult = arsWebView.getHitTestResult();

        //Creates a listener that listens for menu item clicks and decides what to do
        MenuItem.OnMenuItemClickListener handler = new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 0:
                        Intent browserImageIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(hitTestResult.getExtra()));    //Open image in browser
                        startActivity(browserImageIntent);
                        return true;
                    case 1:
                        Toast.makeText(MainActivity.this, R.string.unfinished, Toast.LENGTH_SHORT).show();
                        return true;
                    case 2:
                        Toast.makeText(MainActivity.this, R.string.unfinished, Toast.LENGTH_SHORT).show();
                        return true;
                    case 10:
                        Intent browserLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(hitTestResult.getExtra()));     //Open page in browser
                        startActivity(browserLinkIntent);
                        return true;
                    case 11:
                        Intent browserNewPageIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(hitTestResult.getExtra()));  //Open page in a new browser window
                        browserNewPageIntent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                        startActivity(browserNewPageIntent);
                        return true;
                    case 12:
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);        //Share link with other apps.
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, hitTestResult.getExtra());
                        arsWebView.getContext().startActivity(Intent.createChooser(shareIntent,"Share link"));
                        return true;
                    case 13:
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);        //Copy the url to the clipboard
                        ClipData clip = ClipData.newPlainText("Link", hitTestResult.getExtra());
                        if (clip != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(MainActivity.this, R.string.url_copy_successful, Toast.LENGTH_SHORT).show();
                        }
                        else{
                            Toast.makeText(MainActivity.this, R.string.url_copy_failed, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };

        //Set the header title of the context menu to the link or image url
        TextView headerTitle = new TextView(this);
        String headerText = hitTestResult.getExtra();
        headerTitle.setText(headerText);
        headerTitle.setTextSize(16);
        headerTitle.setPadding((int) (10 * getResources().getDisplayMetrics().density), (int) (6 * getResources().getDisplayMetrics().density), (int) (10 * getResources().getDisplayMetrics().density), (int) (6 * getResources().getDisplayMetrics().density));
        menu.setHeaderView(headerTitle);

        //Compares the hit test result to the type to define the menu items
        if (hitTestResult.getType() == WebView.HitTestResult.IMAGE_TYPE || hitTestResult.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            // Menu options for an image.
            //menu.add(Menu.NONE, 0, 0, "Open image in browser").setOnMenuItemClickListener(handler);
            //menu.add(Menu.NONE, 1, 0, "Save image").setOnMenuItemClickListener(handler);
            //menu.add(Menu.NONE, 2, 0, "Copy image").setOnMenuItemClickListener(handler);
        }
        else if (hitTestResult.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            // Menu options for a hyperlink.
            /*if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInMultiWindowMode()) {
                menu.add(Menu.NONE, 11, 0, "Open in new browser window").setOnMenuItemClickListener(handler);
            }
            else {
                menu.add(Menu.NONE, 10, 0, "Open in browser").setOnMenuItemClickListener(handler);
            }*/
            menu.add(Menu.NONE, 12, 0, "Share link").setOnMenuItemClickListener(handler);
            menu.add(Menu.NONE, 13, 0, "Copy link address").setOnMenuItemClickListener(handler);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        invalidateOptionsMenu();
    }

    //Called when the options menu for the bottom app bar gets created
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        //Create the menu using a MenuInflater
        bottomMenu = menu;
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.bottom_menu, menu);

        //Configure the search view for finding text on the page
        MenuItem findOnPageItem = menu.findItem(R.id.action_find_on_page);
        findOnPageItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                findOnPage = true;
                invalidateOptionsMenu();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                findOnPage = false;
                invalidateOptionsMenu();
                arsWebView.clearMatches();
                return true;
            }
        });
        SearchView findOnPageView = (SearchView) findOnPageItem.getActionView();
        findOnPageView.setSubmitButtonEnabled(true);
        findOnPageView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                arsWebView.findAllAsync(query);
                Toast.makeText(getApplicationContext(), query, Toast.LENGTH_LONG).show();
                Log.d("string_changed", "String changed in search view");
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                arsWebView.findAllAsync(newText);
                Toast.makeText(getApplicationContext(), newText, Toast.LENGTH_LONG).show();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();                                                   //Clear the menu

        MenuInflater menuInflater = getMenuInflater();              //Recreate the menu like it had just been created
        menuInflater.inflate(R.menu.bottom_menu, menu);

        if(findOnPage) {
            MenuItem findOnPage_Upward = menu.findItem(R.id.action_find_on_page_upward);
            findOnPage_Upward.setVisible(true);
            MenuItem findOnPage_Downward = menu.findItem(R.id.action_find_on_page_downward);
            findOnPage_Downward.setVisible(true);
        }
        else {

            MenuItem findOnPage_Upward = menu.findItem(R.id.action_find_on_page_upward);
            findOnPage_Upward.setVisible(false);
            MenuItem findOnPage_Downward = menu.findItem(R.id.action_find_on_page_downward);
            findOnPage_Downward.setVisible(false);

            MenuItem forwardItem = menu.findItem(R.id.action_forward);  //Find the forward button on the menu

            if (arsWebView.canGoForward()) {                            //If the app can go forward
                forwardItem.setEnabled(true);                           //Enable the forward button and
                forwardItem.getIcon().setAlpha(255);                    //Show the user that it is enabled
            }
            else {                                                      //Otherwise
                forwardItem.setEnabled(false);                          //Disable the forward button and
                forwardItem.getIcon().setAlpha(140);                    //Show the user that it is disabled
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        //Include the super call to expand the search action view
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.action_backward:
                // User chose to move backward in the page history
                if (arsWebView.canGoBack()) {   //If the app has web history
                    arsWebView.goBack();        //Then go back
                }
                return true;
            case R.id.action_home:
                // User chose to go to the home page.
                homeURL = sharedSettings.getString("key_url", "https://arstechnica.com");
                loadURL(homeURL);
                return true;
            case R.id.action_forward:
                // User chose to move forward in the page history
                if (arsWebView.canGoForward()) {    //If the app can go forward
                    arsWebView.goForward();         //Go forward
                }
                return true;
            case R.id.action_share:
                // User chose to share the page url through another app
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, arsWebView.getUrl());
                arsWebView.getContext().startActivity(Intent.createChooser(shareIntent,"Share link"));
                return true;
            case R.id.action_open_in_browser:
                // User chose to open the page in their default browser - Not currently in use
                Intent browserNewPageIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(arsWebView.getUrl()));
                startActivity(browserNewPageIntent);
                return true;
            case R.id.action_copy:
                //User chose to copy the page to the clipboard
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Web Page", arsWebView.getUrl());
                if (clip != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.url_copy_successful, Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(this, R.string.url_copy_failed, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_find_on_page:
                return true;
            case R.id.action_reload:
                // User chose to reload the page.
                reload();
                return true;
            case R.id.action_settings:
                // User chose the "Settings" item, so show the app settings UI
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        /* If the user wants to be shown less notifications, then set the last time notifications
         * were checked for to the time the user left the program.
         */
        if (sharedSettings.getBoolean("less_notifications", false)) {
            sharedSettingsEditor = sharedSettings.edit();
            sharedSettingsEditor.putString("least_recent_date", String.valueOf(Calendar.getInstance().getTimeInMillis()));
            sharedSettingsEditor.apply();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(installReceiver);       //Unregister the broadcast reciever to stop using system resources.
        sharedSettings.unregisterOnSharedPreferenceChangeListener(sharedSettingsChangeListener);

        //Set the current url so the app can come back to this
        sharedSettingsEditor = sharedSettings.edit();
        sharedSettingsEditor.putString("CurrentURL", currentURL);
        sharedSettingsEditor.apply();

        //If the custom video view exists, hide it
        if (aCustomView != null) {
            aWebChromeClient.onHideCustomView();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        arsWebView.saveState(outState);
    }
}