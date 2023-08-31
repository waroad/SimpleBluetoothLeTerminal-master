package de.kai_morich.simple_bluetooth_le_terminal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;
import org.greenrobot.eventbus.EventBus;
import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
/**
 * wrap BLE communication into socket like class
 *   - connect, disconnect and write as methods,
 *   - read + status is returned by SerialListener
 */
// BLE 디바이스와의 모든 연결을 담당한다.
@SuppressLint("MissingPermission") // various BluetoothGatt, BluetoothDevice methods
class SerialSocket extends BluetoothGattCallback {

    /**
     * delegate device specific behaviour to inner class
     */
    private volatile BluetoothDevice device;
    private static class DeviceDelegate {
        boolean connectCharacteristics(BluetoothGattService s) { return true; }
        // following methods only overwritten for Telit devices
        void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) { /*nop*/ }
        void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {/*nop*/ }
        void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { /*nop*/ }
        boolean canWrite() { return true; }
        void disconnect() {/*nop*/ }
    }

    private static final UUID BLUETOOTH_LE_CCCD           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_CC254X_CHAR_RW = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_NRF_SERVICE    = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID BLUETOOTH_LE_NRF_CHAR_RW2   = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"); // read on microbit, write on adafruit
    private static final UUID BLUETOOTH_LE_NRF_CHAR_RW3   = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID BLUETOOTH_LE_MICROCHIP_SERVICE    = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455");
    private static final UUID BLUETOOTH_LE_MICROCHIP_CHAR_RW    = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616");
    private static final UUID BLUETOOTH_LE_MICROCHIP_CHAR_W     = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3");

    // https://play.google.com/store/apps/details?id=com.telit.tiosample
    // https://www.telit.com/wp-content/uploads/2017/09/TIO_Implementation_Guide_r6.pdf
    private static final UUID BLUETOOTH_LE_TIO_SERVICE          = UUID.fromString("0000FEFB-0000-1000-8000-00805F9B34FB");
    private static final UUID BLUETOOTH_LE_TIO_CHAR_TX          = UUID.fromString("00000001-0000-1000-8000-008025000000"); // WNR
    private static final UUID BLUETOOTH_LE_TIO_CHAR_RX          = UUID.fromString("00000002-0000-1000-8000-008025000000"); // N
    private static final UUID BLUETOOTH_LE_TIO_CHAR_TX_CREDITS  = UUID.fromString("00000003-0000-1000-8000-008025000000"); // W
    private static final UUID BLUETOOTH_LE_TIO_CHAR_RX_CREDITS  = UUID.fromString("00000004-0000-1000-8000-008025000000"); // I

    private static final int MAX_MTU = 512; // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
    private static final int DEFAULT_MTU = 23;
    private static final String TAG = "SerialSocket";

    private final ArrayList<byte[]> writeBuffer;
    private final IntentFilter pairingIntentFilter;
    private final BroadcastReceiver pairingBroadcastReceiver;
    private final BroadcastReceiver disconnectBroadcastReceiver;

    private final Context context;
    private SerialListener listener;
    private DeviceDelegate delegate;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic, writeCharacteristic;

    private boolean writePending;
    private boolean canceled;
    private boolean connected;
    private int payloadSize = DEFAULT_MTU-3;

    private int songPlayed=0;
    private SoundPool soundPool;
    private int soundID;
    private int first_send=1;
    private int pos;
    private TypedArray songIds;
    private boolean isway = false;
    ArrayList<String> songNames = new ArrayList<>();
    private static final String PREFS_NAME = "Recordings";
    private static final String KEY_RECORDINGS = "recordings";

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    SerialSocket(Context context, BluetoothDevice device) {
        if(context instanceof Activity)
            throw new InvalidParameterException("expected non UI context");
        this.context = context;
        this.device = device;
        writeBuffer = new ArrayList<>();
        pairingIntentFilter = new IntentFilter();
        pairingIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        pairingIntentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    onPairingBroadcastReceive(context, intent);
                }
            }
        };
        disconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(listener != null)
                    listener.onSerialIoError(new IOException("background disconnect"));
            }
        };
    }
    String getName() {
        return device.getName() != null ? device.getName() : device.getAddress();
    }
    // 연결 해제 메시지 수신시,
// 모든 리소스를 해제하고 EventBus를 이용해 SerialService로 연결이 해제됐다는 사실을 보내준다.
    void disconnect() {
        Log.d(TAG, "disconnect");
        listener = null; // ignore remaining data and errors
        device = null;
        canceled = true;
        synchronized (writeBuffer) {
            writePending = false;
            writeBuffer.clear();
        }
        readCharacteristic = null;
        writeCharacteristic = null;
        if(delegate != null)
            delegate.disconnect();
        if (gatt != null) {
            Log.d(TAG, "gatt.disconnect");
            gatt.disconnect();
            Log.d(TAG, "gatt.close");
            try {
                gatt.close();
            } catch (Exception ignored) {}
            gatt = null;
            connected = false;
        }
        try {
            context.unregisterReceiver(pairingBroadcastReceiver);
        } catch (Exception ignored) {
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
        EventBus.getDefault().post(new SignalEvent());
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void connect(SerialListener listener) throws IOException {
        if(connected || gatt != null)
            throw new IOException("already connected");
        canceled = false;
        this.listener = listener;
        context.registerReceiver(disconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DISCONNECT));
        Log.d(TAG, "##### connect "+device);
        context.registerReceiver(pairingBroadcastReceiver, pairingIntentFilter);
        if (Build.VERSION.SDK_INT < 23) {
            Log.d(TAG, "connectGatt");
            gatt = device.connectGatt(context, true, this);
        } else {
            Log.d(TAG, "connectGatt,LE");
            gatt = device.connectGatt(context, true, this, BluetoothDevice.TRANSPORT_LE);
        }
        if (gatt == null)
            throw new IOException("connectGatt failed");
        // continues asynchronously in onPairingBroadcastReceive() and onConnectionStateChange()
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void onPairingBroadcastReceive(Context context, Intent intent) {
        // for ARM Mbed, Microbit, ... use pairing from Android bluetooth settings
        // for HM10-clone, ... pairing is initiated here
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if(device==null || !device.equals(this.device))
            return;
        switch (Objects.requireNonNull(intent.getAction())) {
            case BluetoothDevice.ACTION_PAIRING_REQUEST:
                final int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                Log.d(TAG, "pairing request " + pairingVariant);
                onSerialConnectError(new IOException(context.getString(R.string.pairing_request)));
                // pairing dialog brings app to background (onPause), but it is still partly visible (no onStop), so there is no automatic disconnect()
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                Log.d(TAG, "bond state " + previousBondState + "->" + bondState);
                break;
            default:
                Log.d(TAG, "unknown broadcast " + intent.getAction());
                break;
        }
    }
    // 거리가 멀어지는 등의 연결 상태에 변화가 있을시 여기서 연결을 시도한다.
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        // status directly taken from gat_api.h, e.g. 133=0x85=GATT_ERROR ~= timeout
        Log.d(TAG,"onConnectionStateChange called");
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG,"connect status "+status+", discoverServices");
            if (!gatt.discoverServices())
                onSerialConnectError(new IOException("discoverServices failed"));
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (connected) {
                onSerialIoError(new IOException("gatt status " + status));
            }
            else
                onSerialConnectError(new IOException("gatt status " + status));
        } else {
            Log.d(TAG, "unknown connect state "+newState+" "+status);
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(TAG, "servicesDiscovered, status " + status);
        canceled=false;
        connectCharacteristics1(gatt);
    }
    // 1,2,3 순서로 하나씩 자원할당을 하고 테스팅하면서 연결을 한다.
    private void connectCharacteristics1(BluetoothGatt gatt) {
        Log.d(TAG,"connectCharacteristics1111111111111 called");
        boolean sync = true;
        writePending = false;
        delegate=null;
        for (BluetoothGattService gattService : gatt.getServices()) {
            if (gattService.getUuid().equals(BLUETOOTH_LE_CC254X_SERVICE))
                delegate = new Cc245XDelegate();
            if (gattService.getUuid().equals(BLUETOOTH_LE_MICROCHIP_SERVICE))
                delegate = new MicrochipDelegate();
            if (gattService.getUuid().equals(BLUETOOTH_LE_NRF_SERVICE))
                delegate = new NrfDelegate();
            if (gattService.getUuid().equals(BLUETOOTH_LE_TIO_SERVICE))
                delegate = new TelitDelegate();

            if(delegate != null) {
                sync = delegate.connectCharacteristics(gattService);
                break;
            }
        }
        if(delegate==null || readCharacteristic==null || writeCharacteristic==null) {
            for (BluetoothGattService gattService : gatt.getServices()) {
                Log.d(TAG, "service "+gattService.getUuid());
                for(BluetoothGattCharacteristic characteristic : gattService.getCharacteristics())
                    Log.d(TAG, "characteristic "+characteristic.getUuid());
            }
            onSerialConnectError(new IOException("no serial profile found"));
            return;
        }
        if(sync)
            connectCharacteristics2(gatt);
    }

    private void connectCharacteristics2(BluetoothGatt gatt) {
        Log.d(TAG,"connectCharacteristics2222222222222 called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "request max MTU");
            if (!gatt.requestMtu(MAX_MTU))
                onSerialConnectError(new IOException("request MTU failed"));
            // continues asynchronously in onMtuChanged
        } else {
            connectCharacteristics3(gatt);
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        Log.d(TAG,"mtu size "+mtu+", status="+status);
        if(status ==  BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3;
            Log.d(TAG, "payload size "+payloadSize);
        }
        connectCharacteristics3(gatt);
    }

    private void connectCharacteristics3(BluetoothGatt gatt) {
        Log.d(TAG,"connectCharacteristics333333333333 called");
        int writeProperties = writeCharacteristic.getProperties();
        if((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) ==0) {
            onSerialConnectError(new IOException("write characteristic not writable"));
            return;
        }
        if(!gatt.setCharacteristicNotification(readCharacteristic,true)) {
            onSerialConnectError(new IOException("no notification for read characteristic"));
            return;
        }
        BluetoothGattDescriptor readDescriptor = readCharacteristic.getDescriptor(BLUETOOTH_LE_CCCD);
        if(readDescriptor == null) {
            onSerialConnectError(new IOException("no CCCD descriptor for read characteristic"));
            return;
        }
        int readProperties = readCharacteristic.getProperties();
        if((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            Log.d(TAG, "enable read indication");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        }else if((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            Log.d(TAG, "enable read notification");
            readDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            onSerialConnectError(new IOException("no indication/notification for read characteristic ("+readProperties+")"));
            return;
        }
        Log.d(TAG,"writing read characteristic descriptor");
        if(!gatt.writeDescriptor(readDescriptor)) {
            onSerialConnectError(new IOException("read characteristic CCCD descriptor not writable"));
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        delegate.onDescriptorWrite(gatt, descriptor, status);
        if(canceled)
            return;
        if(descriptor.getCharacteristic() == readCharacteristic) {
            Log.d(TAG,"writing read characteristic descriptor finished, status="+status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onSerialConnectError(new IOException("write descriptor failed"));
            } else {
                // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                onSerialConnect();
                connected = true;
                Log.d(TAG, "connected");
            }
        }
    }
    // 핸드폰에서 알람이 울리게 하기 위해 SoundPool을 사용했는데, initializing을 해준다.
    private void initializeSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else {
            soundPool = new SoundPool(1, AudioManager.STREAM_ALARM, 0);
        }
    }
    /*
     * read
     */
// 문자 수신시 처리해준다. soundPool을 만들어 알람을 키기도, soundPool을 해제해 알람을 끄기도,
// disconnect()를 호출해 자원 해제를 하기도 한다.
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "Message received");
        delegate.onCharacteristicChanged(gatt, characteristic);
        if (characteristic == readCharacteristic) {
            byte[] data = readCharacteristic.getValue();
            onSerialRead(data);
            Log.d(TAG, "read, data=" + readCharacteristic.getStringValue(0));
            isway = getBooleanValue();
            if (!isway) {
                String[] rawSongNames = context.getResources().getStringArray(R.array.song_names);
                songNames.addAll(Arrays.asList(rawSongNames));
                songIds = context.getResources().obtainTypedArray(R.array.song_ids);
                pos = getIntValue();
                int resourceId = songIds.getResourceId(pos, 0);
                if (soundID== 0 && readCharacteristic.getStringValue(0).equals("start")) {
                    initializeSoundPool();
                    soundID = soundPool.load(context, resourceId, 1);
                    soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                        @Override
                        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                            float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (int)maxVolume, 0);
                            float volume = maxVolume / 15.0f; // Assuming maxVolume is 15
                            soundPool.play(soundID, volume, volume, 1, 0, 1.0f);
                        }
                    });
                } else if (soundID != 0&& readCharacteristic.getStringValue(0).equals("stop")) {
                    soundPool.release(); // Release the current SoundPool
                    Log.d("tag","stop");
                    soundID = 0;
                } else if (readCharacteristic.getStringValue(0).equals("disconnect") && first_send==1) {
                    soundPool.release(); // Release the current SoundPool
                    soundID = 0;
                    disconnect();
                    first_send=0;
                }
            } else {
                loadRecordings();
                pos = getIntValue();
                File path = context.getFilesDir();
                File file = new File(path, songNames.get(pos));
                Log.d("tag","show");
                if (file.exists() && readCharacteristic.getStringValue(0).equals("start")) {
                    initializeSoundPool();
                    soundID = soundPool.load(file.getAbsolutePath(), 1);
                    soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                        @Override
                        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                            float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (int)maxVolume, 0);
                            float volume = maxVolume / 15.0f; // Assuming maxVolume is 15
                            soundPool.play(soundID, volume, volume, 1, 0, 1.0f);
                        }
                    });
                } else if (soundID != 0 && readCharacteristic.getStringValue(0).equals("stop")) {
                    soundPool.release(); // Release the current SoundPool
                    soundID = 0;
                } else if (readCharacteristic.getStringValue(0).equals("disconnect") && first_send==1) {
                    soundPool.release(); // Release the current SoundPool
                    soundID = 0;
                    disconnect();
                    first_send=0;
                }
            }
        }
    }
    private void loadRecordings() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String recordingsString = prefs.getString(KEY_RECORDINGS, "");
        if (!recordingsString.isEmpty()) {
            String[] filePaths = recordingsString.split(",");
            for (String filePath : filePaths) {
                songNames.add(filePath);
            }
        }
    }
    public int getIntValue() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("test", Context.MODE_PRIVATE);
        return sharedPreferences.getInt("inputText", 0);
    }
    public Boolean getBooleanValue() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("test", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("input",false);
    }
    /*
     * write
     */
// 스마트폰에서 BLE 디바이스에 메시지를 보낼 때 필요한데, 현재 엡에서는 딱히 사용하지는 않는다.
    void write(byte[] data) throws IOException {
        if(canceled || !connected || writeCharacteristic == null)
            throw new IOException("not connected");
        byte[] data0;
        synchronized (writeBuffer) {
            if(data.length <= payloadSize) {
                data0 = data;
            } else {
                data0 = Arrays.copyOfRange(data, 0, payloadSize);
            }
            if(!writePending && writeBuffer.isEmpty() && delegate.canWrite()) {
                writePending = true;
            } else {
                writeBuffer.add(data0);
                Log.d(TAG,"write queued, len="+data0.length);
                data0 = null;
            }
            if(data.length > payloadSize) {
                for(int i=1; i<(data.length+payloadSize-1)/payloadSize; i++) {
                    int from = i*payloadSize;
                    int to = Math.min(from+payloadSize, data.length);
                    writeBuffer.add(Arrays.copyOfRange(data, from, to));
                    Log.d(TAG,"write queued, len="+(to-from));
                }
            }
        }
        if(data0 != null) {
            writeCharacteristic.setValue(data0);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                onSerialIoError(new IOException("write failed"));
            } else {
                Log.d(TAG,"write started, len="+data0.length);
            }
        }
        // continues asynchronously in onCharacteristicWrite()
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if(canceled || !connected || writeCharacteristic == null)
            return;
        if(status != BluetoothGatt.GATT_SUCCESS) {
            onSerialIoError(new IOException("write failed"));
            return;
        }
        delegate.onCharacteristicWrite(gatt, characteristic, status);
        if(canceled)
            return;
        if(characteristic == writeCharacteristic) { // NOPMD - test object identity
            Log.d(TAG,"write finished, status="+status);
            writeNext();
        }
    }

    private void writeNext() {
        final byte[] data;
        synchronized (writeBuffer) {
            if (!writeBuffer.isEmpty() && delegate.canWrite()) {
                writePending = true;
                data = writeBuffer.remove(0);
            } else {
                writePending = false;
                data = null;
            }
        }
        if(data != null) {
            writeCharacteristic.setValue(data);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                onSerialIoError(new IOException("write failed"));
            } else {
                Log.d(TAG,"write started, len="+data.length);
            }
        }
    }

    /**
     * SerialListener
     */
    private void onSerialConnect() {
        if (listener != null)
            listener.onSerialConnect();
    }

    private void onSerialConnectError(Exception e) {
//        canceled = true;
        if (listener != null)
            listener.onSerialConnectError(e);
    }

    private void onSerialRead(byte[] data) {
        if (listener != null)
            listener.onSerialRead(data);
    }

    private void onSerialIoError(Exception e) {
        writePending = false;
//        canceled = true;
        if (listener != null)
            listener.onSerialIoError(e);
    }

    /**
     * device delegates
     */

    private class Cc245XDelegate extends DeviceDelegate {
        @Override
        boolean connectCharacteristics(BluetoothGattService gattService) {
            Log.d(TAG, "service cc254x uart");
            readCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
            writeCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_CC254X_CHAR_RW);
            return true;
        }
    }

    private class MicrochipDelegate extends DeviceDelegate {
        @Override
        boolean connectCharacteristics(BluetoothGattService gattService) {
            Log.d(TAG, "service microchip uart");
            readCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_MICROCHIP_CHAR_RW);
            writeCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_MICROCHIP_CHAR_W);
            if(writeCharacteristic == null)
                writeCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_MICROCHIP_CHAR_RW);
            return true;
        }
    }

    private class NrfDelegate extends DeviceDelegate {
        @Override
        boolean connectCharacteristics(BluetoothGattService gattService) {
            Log.d(TAG, "service nrf uart");
            BluetoothGattCharacteristic rw2 = gattService.getCharacteristic(BLUETOOTH_LE_NRF_CHAR_RW2);
            BluetoothGattCharacteristic rw3 = gattService.getCharacteristic(BLUETOOTH_LE_NRF_CHAR_RW3);
            if (rw2 != null && rw3 != null) {
                int rw2prop = rw2.getProperties();
                int rw3prop = rw3.getProperties();
                boolean rw2write = (rw2prop & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
                boolean rw3write = (rw3prop & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
                Log.d(TAG, "characteristic properties " + rw2prop + "/" + rw3prop);
                if (rw2write && rw3write) {
                    onSerialConnectError(new IOException("multiple write characteristics (" + rw2prop + "/" + rw3prop + ")"));
                } else if (rw2write) {
                    writeCharacteristic = rw2;
                    readCharacteristic = rw3;
                } else if (rw3write) {
                    writeCharacteristic = rw3;
                    readCharacteristic = rw2;
                } else {
                    onSerialConnectError(new IOException("no write characteristic (" + rw2prop + "/" + rw3prop + ")"));
                }
            }
            return true;
        }
    }

    private class TelitDelegate extends DeviceDelegate {
        private BluetoothGattCharacteristic readCreditsCharacteristic, writeCreditsCharacteristic;
        private int readCredits, writeCredits;

        @Override
        boolean connectCharacteristics(BluetoothGattService gattService) {
            Log.d(TAG, "service telit tio 2.0");
            readCredits = 0;
            writeCredits = 0;
            readCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_TIO_CHAR_RX);
            writeCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_TIO_CHAR_TX);
            readCreditsCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_TIO_CHAR_RX_CREDITS);
            writeCreditsCharacteristic = gattService.getCharacteristic(BLUETOOTH_LE_TIO_CHAR_TX_CREDITS);
            if (readCharacteristic == null) {
                onSerialConnectError(new IOException("read characteristic not found"));
                return false;
            }
            if (writeCharacteristic == null) {
                onSerialConnectError(new IOException("write characteristic not found"));
                return false;
            }
            if (readCreditsCharacteristic == null) {
                onSerialConnectError(new IOException("read credits characteristic not found"));
                return false;
            }
            if (writeCreditsCharacteristic == null) {
                onSerialConnectError(new IOException("write credits characteristic not found"));
                return false;
            }
            if (!gatt.setCharacteristicNotification(readCreditsCharacteristic, true)) {
                onSerialConnectError(new IOException("no notification for read credits characteristic"));
                return false;
            }
            BluetoothGattDescriptor readCreditsDescriptor = readCreditsCharacteristic.getDescriptor(BLUETOOTH_LE_CCCD);
            if (readCreditsDescriptor == null) {
                onSerialConnectError(new IOException("no CCCD descriptor for read credits characteristic"));
                return false;
            }
            readCreditsDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            Log.d(TAG,"writing read credits characteristic descriptor");
            if (!gatt.writeDescriptor(readCreditsDescriptor)) {
                onSerialConnectError(new IOException("read credits characteristic CCCD descriptor not writable"));
                return false;
            }
            Log.d(TAG, "writing read credits characteristic descriptor");
            return false;
            // continues asynchronously in connectCharacteristics2
        }

        @Override
        void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(descriptor.getCharacteristic() == readCreditsCharacteristic) {
                Log.d(TAG, "writing read credits characteristic descriptor finished, status=" + status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onSerialConnectError(new IOException("write credits descriptor failed"));
                } else {
                    connectCharacteristics2(gatt);
                }
            }
            if(descriptor.getCharacteristic() == readCharacteristic) {
                Log.d(TAG, "writing read characteristic descriptor finished, status=" + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    readCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    grantReadCredits();
                    // grantReadCredits includes gatt.writeCharacteristic(writeCreditsCharacteristic)
                    // but we do not have to wait for confirmation, as it is the last write of connect phase.
                }
            }
        }

        @Override
        void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if(characteristic == readCreditsCharacteristic) { // NOPMD - test object identity
                int newCredits = readCreditsCharacteristic.getValue()[0];
                synchronized (writeBuffer) {
                    writeCredits += newCredits;
                }
                Log.d(TAG, "got write credits +"+newCredits+" ="+writeCredits);

                if (!writePending && !writeBuffer.isEmpty()) {
                    Log.d(TAG, "resume blocked write");
                    writeNext();
                }
            }
            if(characteristic == readCharacteristic) { // NOPMD - test object identity
                grantReadCredits();
                Log.d(TAG, "read, credits=" + readCredits);
            }
        }

        @Override
        void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(characteristic == writeCharacteristic) { // NOPMD - test object identity
                synchronized (writeBuffer) {
                    if (writeCredits > 0)
                        writeCredits -= 1;
                }
                Log.d(TAG, "write finished, credits=" + writeCredits);
            }
            if(characteristic == writeCreditsCharacteristic) { // NOPMD - test object identity
                Log.d(TAG,"write credits finished, status="+status);
            }
        }

        @Override
        boolean canWrite() {
            if(writeCredits > 0)
                return true;
            Log.d(TAG, "no write credits");
            return false;
        }

        @Override
        void disconnect() {
            readCreditsCharacteristic = null;
            writeCreditsCharacteristic = null;
        }

        private void grantReadCredits() {
            final int minReadCredits = 16;
            final int maxReadCredits = 64;
            if(readCredits > 0)
                readCredits -= 1;
            if(readCredits <= minReadCredits) {
                int newCredits = maxReadCredits - readCredits;
                readCredits += newCredits;
                byte[] data = new byte[] {(byte)newCredits};
                Log.d(TAG, "grant read credits +"+newCredits+" ="+readCredits);
                writeCreditsCharacteristic.setValue(data);
                if (!gatt.writeCharacteristic(writeCreditsCharacteristic)) {
                    if(connected)
                        onSerialIoError(new IOException("write read credits failed"));
                    else
                        onSerialConnectError(new IOException("write read credits failed"));
                }
            }
        }

    }

}
