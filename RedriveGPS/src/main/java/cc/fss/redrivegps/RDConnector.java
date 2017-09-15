package cc.fss.redrivegps;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;

import com.google.common.primitives.Bytes;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import cc.fss.redrivegps.activities.BluetoothPairingDialog;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 30/04/15.
 * Copyright © 2015 FSS Sp. z o.o. All rights reserved.
 */
public class RDConnector implements RDConnectorInterface, BeaconConsumer {

    public static final String ACTION_CONNECTOR_DID_CONNECT = "cc.fss.redrive.drive.CONNECTOR_DID_CONNECT";
    public static final String ACTION_CONNECTOR_DID_DISCONNECT = "cc.fss.redrive.drive.CONNECTOR_DID_DISCONNECT";
    public static final String ACTION_CONNECTOR_DID_UPDATE_BATTERY_LEVEL = "cc.fss.redrive.drive.CONNECTOR_DID_UPDATE_BATTERY_LEVEL";
    public static final String ACTION_CONNECTOR_DID_UPDATE_BATTERY_STATE = "cc.fss.redrive.drive.CONNECTOR_DID_UPDATE_BATTERY_STATE";
    public static final String ACTION_CONNECTOR_DID_READ_DEVICE_INFO = "cc.fss.redrive.drive.CONNECTOR_DID_READ_DEVICE_INFO";
    public static final String RD_BEACON_PROXIMITY_UUID = "E697DDF2-A094-4AF2-BFF4-A61D6C6D15B0";

    private static final String RD_TRANSFER_SERVICE_UUID = "B9640F9F-533E-4732-B24B-7F2B4BE4923F";
    private static final String RD_TRANSFER_CHARACTERISTIC_UUID = "63B935A0-BAA7-4DB2-954C-51B940648082";
    private static final String RD_SETTINGS_CHARACTERISTIC_UUID = "63B935A0-BAA7-4DB2-954C-51B940648083";
    private static final String RD_TRANSPARENT_SERVICE_UUID = "49535343-FE7D-4AE5-8FA9-9FAFD205E455";
    private static final String RD_TRANSPARENT_RX_CHARACTERISTIC_UUID = "49535343-8841-43F4-A8D4-ECBE34729BB3";
    private static final String TAG = RDConnector.class.getSimpleName();

    private static final long SCAN_PERIOD = 100000;

    private final Context mContext;
    private final BluetoothManager mBluetoothManager;
    private final BluetoothAdapter mBluetoothAdapter;
    private final PDBLeScanCallback mPDBLeScanCallback;
    private final BeaconManager mBeaconManager;
    private final Region mBeaconRegion;
    private final HashMap<String, Integer> mRangingCache = new HashMap<>();
    private final RDConverter mRDConverter = new RDConverter();
    private final HashMap<String, BluetoothDevice> discoveredPeripherals = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver mBondStateBroadcastReceiver = new BondStateBroadcastReceiver();
    private final BroadcastReceiver mPairingBroadcastReceiver = new PairingBroadcastReceiver();
    private BluetoothGatt mGatt;
    private BluetoothLeScanner mLEScanner;
    private BluetoothDevice mBluetoothDevice;
    private RDConnectorDelegate delegate;
    private RDConnectorDiagnosticDelegate diagnosticDelegate;
    private boolean mListening;
    private boolean mScanning;
    private boolean mAllowsBackgroundMode = false;
    private boolean mReconnectOnStart = false;
    private BluetoothGattCharacteristic mTransparentRxCharacteristic;
    private BluetoothGattCharacteristic mSettingsCharacteristic;
    private boolean mWriting = false;
    private boolean mWriteDidEnd = false;
    private boolean mWriteDidCancel = false;
    private float mSendingProgress;
    private long mCountOfBytes;
    private int mCountOfPacketSent;
    private int mWriteChunkSize;
    private int mCountOfBytesSent;
    private byte[] mWriteBufferData;
    private BufferedInputStream mFileBuffer;
    private RDDevice mDevice;
    private Date mReferenceMidnightUTC;
    private long mMaxTimeUTC;
    //field to check whether data received after connect
    private boolean mDidReceiveData = false;

    public RDConnector(Context context) {
        mContext = context;

        mPDBLeScanCallback = new PDBLeScanCallback();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mBeaconManager = BeaconManager.getInstanceForApplication(mContext);
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        mBeaconRegion = new Region("PDB_BEACON", Identifier.parse(RD_BEACON_PROXIMITY_UUID), null, null);

        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mContext.registerReceiver(mBondStateBroadcastReceiver, intent);

        IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mPairingBroadcastReceiver, pairingRequestFilter);
    }

    public static String resolveDeviceName(Beacon beacon, Boolean minorValueOnly) {
        String name = "Unknown";

        if (beacon.getId1().equals(Identifier.fromUuid(UUID.fromString(RD_BEACON_PROXIMITY_UUID)))) {
            int minor = beacon.getId3().toInt();
            String minorString = String.format("%02X%02X", minor >> 8 & 0x00FF, minor & 0x00FF);
            name = minorValueOnly ? minorString : "RDG_" + minorString;
        }

        return name;
    }

    /**
     * Call start scanning ReDrive devices
     */
    public void start() {
        startScanningOrMonitoring();
    }

    /**
     * Call stop scanning ReDrive devices, clea
     */
    public void stop() {
        stopBeaconMonitoring();

        stopScanning();

        discoveredPeripherals.clear();

        disconnect();
    }

    public void clear() {
        stop();
        disconnect(true);

        mContext.unregisterReceiver(mBondStateBroadcastReceiver);
        mContext.unregisterReceiver(mPairingBroadcastReceiver);
    }

    private void startScanningOrMonitoring() {
        if (mAllowsBackgroundMode) {
            startBeaconMonitoring();
        } else {
            startScanning(null);
        }
    }

    private void startScanning(Beacon beacon) {
        Log.i(TAG, "Start scanning - beacon " + beacon);

        if (mScanning) {
            return;
        }

        if (mBluetoothAdapter == null) {
            return;
        }

        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mScanning = true;
        discoveredPeripherals.clear();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanning();
            }
        }, SCAN_PERIOD);

        if (mLEScanner != null) {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(RD_TRANSFER_SERVICE_UUID))
                    .build();

            List<ScanFilter> filters = new ArrayList<>();
            filters.add(scanFilter);

            if (mPDBLeScanCallback != null) {
                mLEScanner.stopScan(mPDBLeScanCallback);
            }

            mLEScanner.startScan(filters, settings, mPDBLeScanCallback);
        }

        if (delegate != null) {
            delegate.connectorDidStartScan(this);
        }

        if (diagnosticDelegate != null) {
            diagnosticDelegate.connectorDidStartScan(this);
        }
    }

    private void stopScanning() {
        if (mBluetoothAdapter == null) {
            return;
        }

        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mScanning = false;

        if (mLEScanner != null) {
            mLEScanner.stopScan(mPDBLeScanCallback);
        }

        if (delegate != null) {
            delegate.connectorDidStopScan(this);
        }

        if (diagnosticDelegate != null) {
            diagnosticDelegate.connectorDidStopScan(this);
        }
    }

    private void startBeaconMonitoring() {
        mBeaconManager.bind(this);
    }

    private void stopBeaconMonitoring() {
        try {
            for (Region region : mBeaconManager.getMonitoredRegions()) {
                mBeaconManager.stopMonitoringBeaconsInRegion(region);
            }
            for (Region region : mBeaconManager.getRangedRegions()) {
                mBeaconManager.stopRangingBeaconsInRegion(region);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mBeaconManager.unbind(this);
    }

    /**
     * Call to connect to BluetoothDevice
     * @param device for connect
     * @return result of connect process
     */
    public boolean connect(BluetoothDevice device) {
        Log.i(TAG, "connect() Try connect to device " + device);

        boolean result = false;

        stopScanning();

        if (mBluetoothDevice == null || mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_DISCONNECTED) {
            cleanupSending();

            mBluetoothDevice = null;

            disconnect(false);

            if (device != null) {
                mBluetoothDevice = device;

                int bondState = mBluetoothDevice.getBondState();

                if (mGatt != null) {
                    mGatt.close();
                }

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.i(TAG, "Device is bonded, connect GATT");
                    mGatt = mBluetoothDevice.connectGatt(mContext, false, new PDBGattCallback());
                } else {
                    if (mBluetoothDevice.createBond()) {
                        Log.i(TAG, "Start bonding process");
                    } else {
                        Log.wtf(TAG, "Bond state:" + bondState);
                    }
                }

                result = true;
            }
        } else if (mBluetoothDevice != null && mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
            startListening();
        } else {
            Log.d(TAG, "Connect fail - device: " + mBluetoothDevice + " state: " + mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT));
        }

        return result;
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public boolean tryConnectWithBeacon(Beacon beacon) {
        boolean result = false;

        if (mAllowsBackgroundMode && mBeaconManager.getMonitoredRegions().contains(mBeaconRegion)) {
            try {
                mBeaconManager.stopRangingBeaconsInRegion(mBeaconRegion);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            startScanning(beacon);

            result = true;
        }

        return result;
    }

    private void reconnect() {
        if (mBluetoothDevice != null) {
            connect(mBluetoothDevice);
        }
    }

    public void disconnect() {
        disconnect(false);
    }

    public void disconnect(boolean notify) {
        cleanup();

        if (notify) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (delegate != null) {
                        delegate.connectorDidDisconnect(RDConnector.this, mBluetoothDevice);
                    }

                    if (diagnosticDelegate != null) {
                        diagnosticDelegate.connectorDidDisconnect(RDConnector.this, mBluetoothDevice);
                    }

                    Intent intent = new Intent(ACTION_CONNECTOR_DID_DISCONNECT);
                    mContext.sendBroadcast(intent);
                }
            });
        }
    }

    private void setupMidnightUTCTimestamp() {
        Calendar date = new GregorianCalendar();

        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        mReferenceMidnightUTC = date.getTime();
        mMaxTimeUTC = 0;
    }

    private void cleanup() {
        if (mGatt != null) {
            stopListening();
            mGatt.disconnect();
        }

        mSettingsCharacteristic = null;
        mTransparentRxCharacteristic = null;

        cleanupSending();

        mDidReceiveData = false;
    }

    public void startListening() {
        try {
            if (mGatt != null && mGatt.getServices() != null) {
                if (mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
                    final ArrayList<BluetoothGattCharacteristic> characteristicsToEnabled = new ArrayList<>();

                    for (BluetoothGattService service : mGatt.getServices()) {
                        if (service.getUuid().equals(UUID.fromString(RD_TRANSFER_SERVICE_UUID))) {
                            for (final BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                if (characteristic.getUuid().equals(UUID.fromString(RD_TRANSFER_CHARACTERISTIC_UUID))) {
                                    if (characteristic.getDescriptors().size() > 0) {
                                        mListening = true;
                                        characteristicsToEnabled.add(characteristic);
                                    } else {
                                        Log.wtf(this.getClass().getSimpleName(), "startListening() characteristic.getDescriptors() size 0");
                                    }
                                } else if (characteristic.getUuid().equals(UUID.fromString(RD_SETTINGS_CHARACTERISTIC_UUID))) {
                                    if (characteristic.getDescriptors().size() > 0) {
                                        mSettingsCharacteristic = characteristic;
                                        characteristicsToEnabled.add(characteristic);
                                    }
                                }
                            }
                        }
                    }

                    int delay = 0;

                    for (final BluetoothGattCharacteristic characteristic : characteristicsToEnabled) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mGatt != null) {
                                    mGatt.setCharacteristicNotification(characteristic, true);
                                    BluetoothGattDescriptor desc = characteristic.getDescriptors().get(0);
                                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    mGatt.writeDescriptor(desc);

                                    Log.wtf(TAG, "ENABLE_NOTIFICATION_VALUE " + characteristic.getUuid().toString());
                                }
                            }
                        }, delay);

                        delay += 3000;
                    }
                } else {
                    reconnect();
                }
            } else {
                Log.wtf(TAG, "startListening() wtf null");
            }
        } catch (ConcurrentModificationException ignored) {

        }
    }

    public void stopListening() {
        mListening = false;

        if (mGatt == null) return;

        Log.wtf(TAG, "stopListening()");

        for (BluetoothGattService service : mGatt.getServices()) {
            if (service.getUuid().equals(UUID.fromString(RD_TRANSFER_SERVICE_UUID))) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    if (characteristic.getUuid().equals(UUID.fromString(RD_TRANSFER_CHARACTERISTIC_UUID)) && characteristic.getDescriptors().size() > 0) {
                        mGatt.setCharacteristicNotification(characteristic, false);
                        BluetoothGattDescriptor desc = characteristic.getDescriptors().get(0);
                        desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                        mGatt.writeDescriptor(desc);
                    }

                    if (characteristic.getUuid().equals(UUID.fromString(RD_SETTINGS_CHARACTERISTIC_UUID))) {
                        mGatt.setCharacteristicNotification(characteristic, false);
                        BluetoothGattDescriptor desc = characteristic.getDescriptors().get(0);
                        desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                        mGatt.writeDescriptor(desc);
                    }
                }
            }
        }
    }

    private void sendCommandRequest(RDCommands.RDCommandRequest request) {
        if (mSettingsCharacteristic != null) {
            if (request.status == 0) {
                mSettingsCharacteristic.setValue(request.data);
                mSettingsCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

                mGatt.writeCharacteristic(mSettingsCharacteristic);
            }
        }
    }

    public void setGpsRate(int gpsRate) {
        RDCommands.RDCommandRequest request = RDCommands.requestForSetGPSRate(gpsRate);

        sendCommandRequest(request);
    }

    public void readDeviceInfo() {
        Log.i(TAG, "Send read device info request");
        RDCommands.RDCommandRequest request = RDCommands.requestForDeviceInfo();
        sendCommandRequest(request);
    }

    public void startDeviceLogging() {
        RDCommands.RDCommandRequest request = RDCommands.request((byte) RDCommands.RDS_CMD_LOGGING_START);
        sendCommandRequest(request);
    }

    public void stopDeviceLogging() {
        RDCommands.RDCommandRequest request = RDCommands.request((byte) RDCommands.RDS_CMD_LOGGING_STOP);
        sendCommandRequest(request);
    }

    @Override
    public void onBeaconServiceConnect() {
        mBeaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(final Collection<Beacon> beacons, Region region) {
//                Log.i("BeaconManager","didRangeBeaconsInRegion " + beacons);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (diagnosticDelegate != null) {
                            diagnosticDelegate.connectorDidUpdateDiscoveredBeacons(RDConnector.this, beacons);
                        }
                    }
                });

                if (beacons.size() > 0 && mAllowsBackgroundMode) {
                    int rssi = -80;

                    for (Beacon beacon : beacons) {
                        if (beacon.getRssi() > rssi && beacon.getRssi() < 0) {
                            Integer hitsInt = mRangingCache.get(beacon.getId3().toString());
                            int hits = hitsInt != null ? hitsInt : 1;

                            if (hits > 0) {
                                stopBeaconMonitoring();
                                startScanning(beacon);
                            }

                            hits = hits + 1;

                            mRangingCache.put(beacon.getId3().toString(), hits);
                        }
                    }
                }
            }
        });

        mBeaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "didEnterRegion" + region);

                try {
                    mRangingCache.clear();
                    mBeaconManager.startRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "didExitRegion" + region);

                try {
                    mBeaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                Log.i(TAG, "didDetermineStateForRegion:" + region + " state:" + (i == MonitorNotifier.INSIDE ? "inside" : "outside"));

                try {
                    if (i == MonitorNotifier.INSIDE) {
                        mRangingCache.clear();
                        mBeaconManager.startRangingBeaconsInRegion(region);
                    } else {
                        mBeaconManager.stopRangingBeaconsInRegion(region);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            mBeaconManager.startMonitoringBeaconsInRegion(mBeaconRegion);
            mBeaconManager.requestStateForRegion(mBeaconRegion);

            Log.i(TAG, "StartMonitoringBeacons - region: " + mBeaconRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Context getApplicationContext() {
        return mContext;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        mContext.unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return mContext.bindService(intent, serviceConnection, i);
    }

    public void setTimezone(int offsetFromGMT) {
        RDCommands.RDCommandRequest request = RDCommands.requestForSetTimezone(offsetFromGMT);

        sendCommandRequest(request);
    }

    public boolean isSendingFile() {
        return mFileBuffer != null;
    }

    /**
     * @return value indicating whether or not the connector is currently scanning for peripherals.
     */
    public boolean isScanning() {
        return mScanning;
    }

    private void didUpdateDeviceInfo(final HashMap<String, Object> changes) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (String key : changes.keySet()) {
                    if (key.equals("sdCardRecording") && mDevice != null) {
                        mDevice.setSdCardRecording((Boolean) changes.get(key));
                    }
                }

                delegate.connectorDidUpdateDeviceInfo(RDConnector.this, changes);
                diagnosticDelegate.connectorDidUpdateDeviceInfo(RDConnector.this, changes);
            }
        });
    }

    private void cleanupSending() {
        mWriteBufferData = null;
        mWriteDidEnd = false;
        mWriting = false;

        if (mFileBuffer != null) {
            try {
                mFileBuffer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFileBuffer = null;
        }

        mSendingProgress = 0;
        mCountOfBytes = 0;
        mCountOfPacketSent = 0;
    }

    private void sendDataIfNeeded() {
        if (!mWriting) {
            if (mWriteDidCancel) {
                cleanupSending();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (diagnosticDelegate != null) {
                            diagnosticDelegate.connectorDidCompleteSending(RDConnector.this);
                        }
                    }
                });

                return;
            }

            mWriting = true;
            mWriteDidEnd = mWriteBufferData.length == 0;

            // init FWUpdateMode
            // >
            // 4 bytes
            // 0x00000000
            // 0x01000000
            // 0x02000000
            // ...
            // 0xFFFFFFFF - // no data

            byte[] data;

            ByteBuffer b = ByteBuffer.allocate(4);
            b.order(ByteOrder.LITTLE_ENDIAN);

            if (!mWriteDidEnd) {
                b.putInt(mCountOfPacketSent);
                data = b.array();
                data = Bytes.concat(data, mWriteBufferData);

                int availableBytes = 0;
                try {
                    availableBytes = mFileBuffer.available();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (availableBytes > 0) {
                    int bufferSize = availableBytes >= mWriteChunkSize ? mWriteChunkSize : availableBytes;
                    byte[] buffer = new byte[bufferSize];
                    try {
                        int result = mFileBuffer.read(buffer, 0, buffer.length);
                        if (result > 0) {
                            mWriteBufferData = buffer;
                        } else {
                            mWriteBufferData = new byte[0];
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    mWriteBufferData = new byte[0];
                }
            } else {
                Log.d(TAG, "Write Did End");

                try {
                    mFileBuffer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mFileBuffer = null;

                b.putInt(0xFFFFFFFF);
                data = b.array();
            }

            if (data.length > 0) {
                Log.d(TAG, "write data - size:" + data.length);

                mTransparentRxCharacteristic.setValue(data);
                mTransparentRxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

                mGatt.writeCharacteristic(mTransparentRxCharacteristic);

                mCountOfBytesSent += data.length;
                mCountOfPacketSent += 1;
            }

            mSendingProgress = (float) mCountOfBytesSent / (float) mCountOfBytes;
        }
    }

    public void stopSendingFile() {
        mWriteDidCancel = true;

        cleanupSending();
    }

    public void startSendingFile(File file) throws IOException {
        if (file != null && file.isFile() && file.exists()) {
            if (mTransparentRxCharacteristic != null && !mWriting) {
                if (mFileBuffer != null) {
                    mFileBuffer.close();
                }

                Log.d(TAG, "startSendingFile");

                mFileBuffer = new BufferedInputStream(new FileInputStream(file));

                long totalFileLength = file.length();

                mSendingProgress = 0;

                mWriteDidCancel = false;
                mWriteDidEnd = false;
                mWriteChunkSize = 128;

                mCountOfBytesSent = 0;
                mCountOfPacketSent = 0;

                mCountOfBytes = totalFileLength + (totalFileLength / mWriteChunkSize) * 4;

                byte[] buffer = new byte[mWriteChunkSize];
                int result = mFileBuffer.read(buffer, 0, mWriteChunkSize);

                if (result > 0) {
                    mWriteBufferData = buffer;

                    RDCommands.RDCommandRequest request = RDCommands.requestForInitFirmwareUpdate(totalFileLength);

                    sendCommandRequest(request);
                }
            } else {
                Log.d(TAG, "startSendingFile error char null or writing");
            }
        }
    }

    private void didReceiveData(final byte[] buffer, final long msgId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (delegate != null) {
                    delegate.connectorDidReceiveData(buffer, msgId);
                }
                if (diagnosticDelegate != null) {
                    diagnosticDelegate.connectorDidReceiveData(buffer, msgId);
                }
            }
        });

        if (buffer.length > 0) {
            final RDData rdData = new RDData();

            char header = (char) buffer[0];

            if (header == 'B') {
                if (mRDConverter.decodeData(buffer, rdData) == RDConverter.PDB_STATUS_NO_ERR && rdData.fields.timeUTC != 0) {
                    if (rdData.fields.timeUTC < mMaxTimeUTC) {
                        if (mMaxTimeUTC - rdData.fields.timeUTC > 230000000) {
                            mReferenceMidnightUTC = new Date(mReferenceMidnightUTC.getTime() + 86400000);
                        }
                    }

                    mMaxTimeUTC = rdData.fields.timeUTC;


                    if (delegate != null) {
                        delegate.connectorDidReadData(RDConnector.this, rdData);
                    }

                    if ((rdData.header.flags & RDConverter.PACKET_FIELD_BatteryStatus) > 0 && mDevice != null) {
                        int batteryState = (0x80 & rdData.fields.batteryStatus) > 0 ? 1 : 0;
                        int batteryLevel = (0x7F & rdData.fields.batteryStatus);

                        if (batteryLevel != mDevice.getBatteryLevel()) {
                            mDevice.setBatteryLevel(batteryLevel);

                            Intent intent = new Intent(ACTION_CONNECTOR_DID_UPDATE_BATTERY_LEVEL);
                            intent.putExtra("batteryState", batteryState);
                            intent.putExtra("batteryLevel", batteryLevel);
                            mContext.sendBroadcast(intent);
                        }

                        if (batteryState != mDevice.getBatteryState()) {
                            mDevice.setBatteryState(batteryState);

                            Intent intent = new Intent(ACTION_CONNECTOR_DID_UPDATE_BATTERY_STATE);
                            intent.putExtra("batteryState", batteryState);
                            intent.putExtra("batteryLevel", batteryLevel);
                            mContext.sendBroadcast(intent);
                        }
                    }
                }
            }
        }
    }

    public void setAllowsBackgroundMode(boolean allowsBackgroundMode) {
        boolean restartScanning = mScanning;

        stopBeaconMonitoring();
        stopScanning();

        mAllowsBackgroundMode = allowsBackgroundMode;

        if (allowsBackgroundMode || restartScanning) {
            startScanningOrMonitoring();
        }
    }

    public void setDelegate(RDConnectorDelegate delegate) {
        this.delegate = delegate;
    }

    public void setDiagnosticDelegate(RDConnectorDiagnosticDelegate diagnosticDelegate) {
        this.diagnosticDelegate = diagnosticDelegate;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int getState() {
        if (mBluetoothDevice != null) {
            return mBluetoothManager.getConnectionState(mBluetoothDevice, BluetoothProfile.GATT);
        }

        return -1;
    }

    public HashMap<String, BluetoothDevice> getDiscoveredPeripherals() {
        return discoveredPeripherals;
    }

    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    public RDDevice getDevice() {
        return mDevice;
    }

    public Date getReferenceMidnightUTC() {
        return mReferenceMidnightUTC;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class PDBLeScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            Log.v(TAG, "onScanResult: " + result.getDevice());

            BluetoothDevice device = result.getDevice();
            BluetoothDevice discoveredDevice = discoveredPeripherals.get(device.getAddress());

            discoveredPeripherals.put(device.getAddress(), device);

            if (discoveredDevice != null) {
                return;
            }

            mReconnectOnStart = true;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (delegate != null) {
                        delegate.connectorDidDiscover(result.getDevice());
                    }

                    if (diagnosticDelegate != null) {
                        diagnosticDelegate.connectorDidDiscover(result.getDevice());
                    }
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e("PDBLeScanCallback", "onScanFailed() errorCode " + errorCode);
        }
    }

    private class PDBGattCallback extends BluetoothGattCallback {

        private long _msgId;
        private ByteArrayOutputStream receivedData;
        private long _msgNo;

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            Log.i("PDBGattCallback", "onConnectionStateChange() operationStatus:" + status + " newConnectionStatus:" + newState);

            if (delegate != null) {
                delegate.connectorDidChangeConnectionState(RDConnector.this, status, newState);
            }

            if (diagnosticDelegate != null) {
                diagnosticDelegate.connectorDidChangeConnectionState(RDConnector.this, status, newState);
            }

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                mDevice = new RDDevice();

                setupMidnightUTCTimestamp();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (delegate != null)
                            delegate.connectorDidConnect(RDConnector.this, mBluetoothDevice);
                        if (diagnosticDelegate != null)
                            diagnosticDelegate.connectorDidConnect(RDConnector.this, mBluetoothDevice);

                        Intent intent = new Intent(ACTION_CONNECTOR_DID_CONNECT);
                        mContext.sendBroadcast(intent);
                    }
                });

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        gatt.discoverServices();
                    }
                }, 1000);
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnect(true);

                if (status == 22) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            connect(mBluetoothDevice);
                        }
                    }, 1000);
                }
            } else if (newState == BluetoothProfile.STATE_CONNECTED && mReconnectOnStart) {
                mReconnectOnStart = false;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mDidReceiveData) {
                            disconnect(false);
                            connect(mBluetoothDevice);
                        }
                    }
                }, 6 * 1000);
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (delegate != null)
                            delegate.connectorDidDisconnect(RDConnector.this, mBluetoothDevice);
                        if (diagnosticDelegate != null)
                            diagnosticDelegate.connectorDidDisconnect(RDConnector.this, mBluetoothDevice);
                    }
                });

                Intent intent = new Intent(ACTION_CONNECTOR_DID_DISCONNECT);
                mContext.sendBroadcast(intent);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i("PDBGattCallback", "onServicesDiscovered() services:" + gatt.getServices());

            List<BluetoothGattService> services = gatt.getServices();

            boolean startListening = false;

            for (BluetoothGattService service : services) {
                if (service.getUuid().equals(UUID.fromString(RD_TRANSFER_SERVICE_UUID))) {
                    if (!mListening && !startListening) {
                        startListening = true;
                        startListening();
                    }
                } else if (service.getUuid().equals(UUID.fromString(RD_TRANSPARENT_SERVICE_UUID))) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        if (characteristic.getUuid().equals(UUID.fromString(RD_TRANSPARENT_RX_CHARACTERISTIC_UUID))) {
                            mTransparentRxCharacteristic = characteristic;
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int statusResp) {
            super.onCharacteristicWrite(gatt, characteristic, statusResp);

            if (characteristic.getUuid().equals(UUID.fromString(RD_SETTINGS_CHARACTERISTIC_UUID))) {
                Log.i(TAG, "onCharacteristicWrite " + characteristic.getUuid() + " statusResp:" + statusResp);
            } else if (characteristic.getUuid().equals(UUID.fromString(RD_TRANSPARENT_RX_CHARACTERISTIC_UUID))) {
                mWriting = false;

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (diagnosticDelegate != null) {
                            diagnosticDelegate.connectorDidSendBytes(RDConnector.this, mCountOfBytes, mSendingProgress);
                        }
                    }
                });

                if (mWriteDidEnd) {
                    cleanupSending();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (diagnosticDelegate != null) {
                                diagnosticDelegate.connectorDidCompleteSending(RDConnector.this);
                            }
                        }
                    });
                } else {
                    sendDataIfNeeded();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UUID.fromString(RD_TRANSFER_CHARACTERISTIC_UUID))) {
                mDidReceiveData = true;
                byte[] data = characteristic.getValue();

                if (data.length > 2) {
                    byte bMsg = data[0];

                    byte msgNo = (byte) (bMsg & 0x03);
                    byte msgId = (byte) (bMsg >> 2);

                    if (_msgId != msgId) {
                        if (receivedData != null && receivedData.size() > 2) {
                            byte[] finalData = receivedData.toByteArray();
                            didReceiveData(finalData, _msgId);
                        }

                        _msgId = msgId;
                        _msgNo = 0;

                        receivedData = new ByteArrayOutputStream(0);
                    }

                    if (_msgId == msgId) {
                        if (_msgNo == msgNo) {
                            receivedData.write(data, 1, data.length - 1);
                            _msgNo++;
                        }
                    }
                }
            } else if (characteristic.getUuid().equals(UUID.fromString(RD_SETTINGS_CHARACTERISTIC_UUID))) {
                byte[] responseData = characteristic.getValue();

                RDCommands.RDCommandResponse response = new RDCommands.RDCommandResponse();
                int status = RDCommands.deserializeResponse(responseData, (short) responseData.length, response);

                Log.d(TAG, "onCharacteristicChanged status:" + status + " cmd:" + response.cmd);

                if (status == RDCommands.RDS_STATUS_NO_ERR) {
                    switch (response.cmd) {
                        case RDCommands.RDS_CMS_SET_GPS_RATE:
                        case RDCommands.RDS_CMS_SET_DEVICE_NAME:
                            break;
                        case (byte) RDCommands.RDS_CMD_LOGGING_START: {
                            HashMap<String, Object> changes = new HashMap<>();
                            changes.put("sdCardRecording", true);
                            didUpdateDeviceInfo(changes);
                            break;
                        }
                        case (byte) RDCommands.RDS_CMD_LOGGING_STOP: {
                            HashMap<String, Object> changes = new HashMap<>();
                            changes.put("sdCardRecording", false);
                            didUpdateDeviceInfo(changes);
                            break;
                        }
                        case RDCommands.RDS_CMS_SET_TIMEZONE: {
                            readDeviceInfo();
                            break;
                        }
                        case RDCommands.RDS_CMD_FW_UPDATE_MODE: {
                            mHandler.postDelayed(new Runnable() { //FIX: sometimes onCharacteristicChanged is calling before onCharacteristicWrite
                                @Override
                                public void run() {
                                    sendDataIfNeeded();
                                }
                            }, 50);
                            break;
                        }
                        case RDCommands.RDS_CMS_GET_DEVICE_INFO: {
                            RDCommands.RDDeviceInfo deviceInfo = new RDCommands.RDDeviceInfo();
                            if (RDCommands.deviceInfoFromCmdResponse(response, deviceInfo) == 0) {
                                RDDevice device = new RDDevice();
                                device.setName(gatt.getDevice().getName());
                                device.setGpsRate(deviceInfo.gpsRate);

                                device.setScCardInserted((deviceInfo.sdCardFlags & 0x01) > 0);
                                device.setSdCardRecording((deviceInfo.sdCardFlags & 0x02) > 0);

                                device.setTimezone(deviceInfo.timezone);
                                device.setFirmwareVersionString(String.format(Locale.US, "%02X.%02X.%04X", deviceInfo.major, deviceInfo.minor, (int) deviceInfo.build));

                                if (deviceInfo.btMac != null && deviceInfo.btMac.length == 6) {
                                    String macAddress = String.format(Locale.US, "%X-%X-%X-%X-%X-%X", deviceInfo.btMac[0], deviceInfo.btMac[1], deviceInfo.btMac[2], deviceInfo.btMac[3], deviceInfo.btMac[4], deviceInfo.btMac[5]);
                                    device.setMacAddress(macAddress);
                                }

                                Log.d(TAG, "Get device info:" + device);

                                mDevice = device;

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (delegate != null) {
                                            delegate.connectorDidReadDeviceInfo(RDConnector.this);
                                        }

                                        if (diagnosticDelegate != null) {
                                            diagnosticDelegate.connectorDidReadDeviceInfo(RDConnector.this);
                                        }

                                        Intent intent = new Intent(ACTION_CONNECTOR_DID_READ_DEVICE_INFO);
                                        mContext.sendBroadcast(intent);
                                    }
                                });
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private class BondStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

                Log.d(TAG, "ACTION_BOND_STATE_CHANGED - PREVIOUS_BOND_STATE:" + prevBondState + " BOND_STATE:" + bondState);

                if (mGatt != null) {
                    mGatt.close();
                }

                if (prevBondState == BluetoothDevice.BOND_BONDING) {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        if (mBluetoothDevice != null) {
                            mGatt = mBluetoothDevice.connectGatt(mContext, false, new PDBGattCallback());
                        }
                    }
                }
            }
        }
    }

    private class PairingBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

                if (type == BluetoothDevice.PAIRING_VARIANT_PIN) {
                    Intent pairingIntent = new Intent();
                    pairingIntent.setClass(context, BluetoothPairingDialog.class);
                    pairingIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                    pairingIntent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, type);
                    pairingIntent.setAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
                    pairingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    mContext.startActivity(pairingIntent);

                    abortBroadcast();
                } else {
                    Log.w(TAG, "Unexpected pairing type: " + type);
                }
            }
        }
    }
}