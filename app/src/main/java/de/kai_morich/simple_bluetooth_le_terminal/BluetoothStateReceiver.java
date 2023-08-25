package de.kai_morich.simple_bluetooth_le_terminal;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// 블루투스 상태가 변하면 SerialService에 해당 사실을 알려주어, 등록된 디바이스 재연결을 시도하게 한다.
public class BluetoothStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (bluetoothState == BluetoothAdapter.STATE_ON) {
                Log.d("BluetoothStateReceiver","bluetooth state change detected");
                Intent broadcastIntent = new Intent("bluetooth_state_changed");
                LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent);
            }
        }
    }
}