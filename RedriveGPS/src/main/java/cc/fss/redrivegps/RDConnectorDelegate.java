package cc.fss.redrivegps;

import android.bluetooth.BluetoothDevice;

import java.util.HashMap;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 30/04/15.
 * Copyright © 2015 FSS Sp. z o.o. All rights reserved.
 */
public interface RDConnectorDelegate {
    void connectorDidStartScan(RDConnector connector);

    void connectorDidStopScan(RDConnector connector);

    void connectorDidDiscover(BluetoothDevice device);

    void connectorDidConnect(RDConnector connector, BluetoothDevice device);

    void connectorDidChangeConnectionState(RDConnector connector, int GATTStatus, int newState);

    void connectorDidDisconnect(RDConnector connector, BluetoothDevice device);

    void connectorDidReceiveData(byte[] data, long tag);

    void connectorDidReadData(RDConnectorInterface connector, RDData RDData);

    void connectorDidReadDeviceInfo(RDConnector connector);

    void connectorDidUpdateDeviceInfo(RDConnector connector, HashMap changes);
}
