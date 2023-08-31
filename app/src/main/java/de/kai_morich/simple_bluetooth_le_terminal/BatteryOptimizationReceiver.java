package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BatteryOptimizationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = context.getPackageName();
        Intent settingsIntent = new Intent();
        settingsIntent.setAction(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(settingsIntent);
    }
}
