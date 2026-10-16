package com.boiyelove.wirelessdebugkeeper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, KeeperService.class).putExtra("manual", true));
        TextView message = new TextView(this);
        message.setText("Wireless Debug Keeper is running. It checks Flight mode, Wi-Fi, and Bluetooth, restores wireless debugging, and announces battery levels and charger changes.");
        message.setTextSize(18);
        message.setPadding(32, 32, 32, 32);
        setContentView(message);
    }
}
