package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
// 배터리 제한 없이 사용하도록 하는 리시버 생성
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
