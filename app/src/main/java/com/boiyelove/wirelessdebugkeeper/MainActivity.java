package com.boiyelove.wirelessdebugkeeper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, KeeperService.class));
        TextView message = new TextView(this);
        message.setText("Wireless Debug Keeper is running. It restores wireless debugging, speaks at 70%, 50%, 30%, and 10% while the battery falls, and announces charger connections.");
        message.setTextSize(18);
        message.setPadding(32, 32, 32, 32);
        setContentView(message);
    }
}
