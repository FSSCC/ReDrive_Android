package cc.fss.redrivegps.activities;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import cc.fss.redrivegps.R;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 19/07/2017.
 * Copyright © 2017 FSS Sp. z o.o. All rights reserved.
 */

public class BluetoothPairingDialog extends Activity {

    private static final String TAG = BluetoothPairingDialog.class.getSimpleName();

    private BluetoothDevice mDevice;

    private EditText mPairingView;
    private ProgressBar mActivityIndicator;

    /**
     * Dismiss the dialog if the bond state changes to bonded or none,
     * or if pairing was canceled for {@link #mDevice}.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);
                if (bondState == BluetoothDevice.BOND_BONDED ||
                        bondState == BluetoothDevice.BOND_NONE) {
                    dismiss();
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_bluetooth_pairing);

        Intent intent = getIntent();

        mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        mPairingView = (EditText) findViewById(R.id.pinEditText);
        mActivityIndicator = (ProgressBar) findViewById(R.id.activityIndicator);

        Button okButton = (Button) findViewById(R.id.confirmButton);
        Button cancelButton = (Button) findViewById(R.id.cancelButton);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pairAction();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelAction();
            }
        });

        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    private void pairAction() {
        mActivityIndicator.setVisibility(View.VISIBLE);
        String pairingKey = mPairingView.getText().toString();
        byte[] pinBytes = convertPinToBytes(pairingKey);

        if (pinBytes != null) {
            mDevice.setPin(pinBytes);
        }
    }

    private void cancelAction() {
        try {
            Method m = mDevice.getClass().getMethod("cancelPairingUserInput", (Class[]) null);
            m.invoke(mDevice, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    public void dismiss() {
        // This is called after the click, since we finish when handling the
        // click, don't do that again here.
        if (!isFinishing()) {
            finish();
        }
    }

    /**
     * Check that a pin is valid and convert to byte array.
     *
     * Bluetooth pin's are 1 to 16 bytes of UTF-8 characters.
     * @param pin pin as java String
     * @return the pin code as a UTF-8 byte array, or null if it is an invalid
     *         Bluetooth pin.
     */
    public static byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            Log.e(TAG, "UTF-8 not supported?!?");  // this should not happen
            return null;
        }
        if (pinBytes.length <= 0 || pinBytes.length > 16) {
            return null;
        }
        return pinBytes;
    }
}
