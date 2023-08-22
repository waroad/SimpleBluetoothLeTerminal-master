//package de.kai_morich.simple_bluetooth_le_terminal;
//
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.Service;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.IBinder;
//import android.util.Log;
//
//import androidx.annotation.Nullable;
//import androidx.core.app.NotificationCompat;
//import androidx.fragment.app.Fragment;
//
//public class BluetoothForegroundService extends Service {
//    private static final String CHANNEL_ID = "bluetooth_foreground_channel";
//    private static final int NOTIFICATION_ID = 123; // Use any unique integer value
//
//    private String deviceAddress;
//    public void setDeviceAddressFromFragment(String deviceAddress) {
//        this.deviceAddress = deviceAddress;
//    }
//    private static BluetoothForegroundService instance;
//
//    public static BluetoothForegroundService getInstance() {
//        return instance;
//    }
//    private BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            Log.d("BBBBBB","wow");
//            String action = intent.getAction();
//            if ("bluetooth_state_changed".equals(action)) {
//                Log.d("DDDDB","wow");
//                BluetoothStateReceiver bluetoothStateReceiver = new BluetoothStateReceiver();
//                bluetoothStateReceiver.startService(context); // Pass the context to the receiver
//                Log.d("TAGGG","wowwo");
////                Bundle args = new Bundle();
////                args.putString("device", deviceAddress);
////                Fragment fragment = new TerminalFragment();
////                fragment.setArguments(args);
//            }
//        }
//    };
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        instance = this;
//        // Register Bluetooth state receiver
//        IntentFilter filter = new IntentFilter("bluetooth_state_changed");
//        registerReceiver(bluetoothStateReceiver, filter);
//        createNotificationChannel();
//    }
//    private void createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    CHANNEL_ID,
//                    "Bluetooth Foreground Channel",
//                    NotificationManager.IMPORTANCE_DEFAULT
//            );
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            if (manager != null) {
//                manager.createNotificationChannel(channel);
//            }
//        }
//    }
//    @Override
//    public void onDestroy() {
//        // Unregister Bluetooth state receiver
//        unregisterReceiver(bluetoothStateReceiver);
//        super.onDestroy();
//    }
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        deviceAddress = intent.getStringExtra("deviceAddress");
//        // Perform your Bluetooth operations using deviceAddress
//
//        // Show a foreground notification
//        Notification notification = createNotification();
//        startForeground(NOTIFICATION_ID, notification);
//
//        // Return a value that determines how the service behaves after it's started
//        return START_STICKY;
//    }
//
//    private Notification createNotification() {
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("Foreground Service")
//                .setContentText("Running") // Your message here
//                .setSmallIcon(R.drawable.ic_delete_white_24dp) // Replace with your notification icon
//                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
//
//        return builder.build();
//    }
//    // Other methods and logic for Bluetooth management
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//}
