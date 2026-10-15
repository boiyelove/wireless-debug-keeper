package com.boiyelove.wirelessdebugkeeper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

public final class KeeperService extends Service {
    private static final String TAG = "WirelessDebugKeeper";
    private static final String CHANNEL = "wireless_debug_keeper";
    private static final String SETTING = "adb_wifi_enabled";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean observing;
    private long lastAttempt;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BatteryAnnouncer batteryAnnouncer;
    private final ContentObserver settingObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { restoreIfNeeded(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Wireless debugging", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Wireless debugging keeper")
                .setContentText("Restores debugging and announces battery levels")
                .setOngoing(true).build();
        startForeground(1, notification);
        JobScheduler jobs = getSystemService(JobScheduler.class);
        jobs.schedule(new JobInfo.Builder(1, new ComponentName(this, WatchdogJob.class))
                .setPeriodic(15 * 60_000L).setPersisted(true).build());
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(SETTING),
                false, settingObserver);
        observing = true;
        connectivity = getSystemService(ConnectivityManager.class);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { handler.post(checkOnce); }
        };
        connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback);
        batteryAnnouncer = new BatteryAnnouncer(this);
        registerReceiver(batteryAnnouncer, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        restoreIfNeeded();
    }

    private final Runnable checkOnce = this::restoreIfNeeded;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        restoreIfNeeded();
        return START_STICKY;
    }

    private void restoreIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (lastAttempt != 0 && now - lastAttempt < 5 * 60_000) return;
        if (Settings.Global.getInt(getContentResolver(), SETTING, 0) == 1) return;
        if (!hasWifi(connectivity)) return;
        // A rejected network prompt pauses retries for five minutes.
        lastAttempt = now;
        restoreIfNeeded(this);
    }

    static void restoreIfNeeded(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (!hasWifi(connectivity)) return;
        if (Settings.Global.getInt(context.getContentResolver(), SETTING, 0) == 1) return;
        try {
            Settings.Global.putInt(context.getContentResolver(), SETTING, 1);
            Log.i(TAG, "Restored wireless debugging");
        } catch (SecurityException e) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS is missing", e);
        }
    }

    private static boolean hasWifi(ConnectivityManager connectivity) {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
        }
        return false;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (observing) getContentResolver().unregisterContentObserver(settingObserver);
        if (connectivity != null && networkCallback != null) connectivity.unregisterNetworkCallback(networkCallback);
        if (batteryAnnouncer != null) {
            unregisterReceiver(batteryAnnouncer);
            batteryAnnouncer.close();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
