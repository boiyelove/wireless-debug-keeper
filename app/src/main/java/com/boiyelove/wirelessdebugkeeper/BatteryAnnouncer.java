package com.boiyelove.wirelessdebugkeeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

final class BatteryAnnouncer extends BroadcastReceiver {
    private static final String TAG = "BatteryAnnouncer";
    private static final int[] LEVELS = {70, 50, 30, 10};
    private final Context context;
    private final SharedPreferences state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech speech;
    private int lastPlugged = -1;

    BatteryAnnouncer(Context context) {
        this.context = context;
        state = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("battery_announcements", Context.MODE_PRIVATE);
    }

    @Override public void onReceive(Context ignored, Intent intent) {
        if (!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return;
        int percent = Math.round(level * 100f / scale);
        int previous = state.getInt("level", -1);
        int announced = state.getInt("announced", 0);
        int crossed = -1;
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0 ? 0 : 1;
        String message = null;

        if (lastPlugged >= 0 && plugged != lastPlugged) {
            message = plugged == 1 ? "Device is charging." : "Device stopped charging.";
        }
        lastPlugged = plugged;

        if (plugged == 1) {
            announced = 0;
        } else if (previous >= 0 && percent < previous) {
            for (int i = 0; i < LEVELS.length; i++) {
                if (previous > LEVELS[i] && percent <= LEVELS[i]) {
                    if ((announced & (1 << i)) == 0) crossed = LEVELS[i];
                    announced |= 1 << i;
                }
            }
        }

        state.edit().putInt("level", percent).putInt("announced", announced).apply();
        if (crossed >= 0) {
            String levelMessage = "Battery is at " + crossed + " percent.";
            message = message == null ? levelMessage : message + " " + levelMessage;
        }
        if (message != null) announce(message);
    }

    private void announce(String message) {
        close();
        TextToSpeech[] pending = new TextToSpeech[1];
        pending[0] = new TextToSpeech(context, status -> handler.post(() -> {
            TextToSpeech engine = pending[0];
            if (engine == null || speech != engine) return;
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Text to speech initialization failed");
                close();
                return;
            }
            engine.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { handler.post(() -> finish(engine)); }
                @Override public void onError(String utteranceId) { handler.post(() -> finish(engine)); }
            });
            int result = engine.speak(message,
                    TextToSpeech.QUEUE_FLUSH, null, "battery-announcement");
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Could not speak: " + message);
                close();
            } else {
                Log.i(TAG, "Speaking: " + message);
            }
        }));
        speech = pending[0];
        handler.postDelayed(() -> finish(pending[0]), 15_000);
    }

    private void finish(TextToSpeech engine) {
        if (engine == speech) close();
    }

    void close() {
        if (speech != null) {
            speech.shutdown();
            speech = null;
        }
    }
}
