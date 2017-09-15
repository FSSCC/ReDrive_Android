package cc.fss.redrivegps;

import android.bluetooth.BluetoothDevice;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 05/07/2017.
 * Copyright © 2017 FSS Sp. z o.o. All rights reserved.
 */

public interface RDConnectorInterface {
    void setDelegate(RDConnectorDelegate delegate);

    void setAllowsBackgroundMode(boolean backgroundModeEnabled);

    void start();

    void stopListening();

    boolean connect(BluetoothDevice device);

    int getState();

    RDDevice getDevice();

    boolean isSendingFile();

    BluetoothDevice getBluetoothDevice();

    void readDeviceInfo();

    void startSendingFile(File destinationFile) throws IOException;

    void stop();

    void startListening();

    Date getReferenceMidnightUTC();
}
