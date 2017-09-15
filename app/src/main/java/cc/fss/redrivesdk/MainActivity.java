package cc.fss.redrivesdk;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import cc.fss.redrivegps.RDConnector;
import cc.fss.redrivegps.RDConnectorDelegate;
import cc.fss.redrivegps.RDConnectorInterface;
import cc.fss.redrivegps.RDData;
import cc.fss.redrivesdk.viewHolders.DeviceViewHolder;

public class MainActivity extends AppCompatActivity implements RDConnectorDelegate {

    private RDConnector mConnector;
    private RecyclerView mRecyclerView;
    private DevicesListAdapter mDevicesListAdapter = new DevicesListAdapter();
    private DeviceInfoListAdapter mDeviceInfoListAdapter = new DeviceInfoListAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mConnector = new RDConnector(this);
        mConnector.setDelegate(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 199);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mConnector.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mConnector.getState() == BluetoothProfile.STATE_CONNECTING || mConnector.getState() == BluetoothProfile.STATE_CONNECTED) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_disconnect, menu);
        } else {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_scan, menu);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.scan_item) {
            startScanning();
        } else if (item.getItemId() == R.id.disconnect_item) {
            disconnect();
        }

        return true;
    }

    private void disconnect() {
        mConnector.disconnect(true);
    }

    private void startScanning() {
        mRecyclerView.setAdapter(mDevicesListAdapter);

        if (checkBluetooth()) {
            mConnector.start();
        }
    }

    private boolean checkBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            int REQUEST_ENABLE_BT = 1;
            startActivityForResult(intentBtEnabled, REQUEST_ENABLE_BT);

            return false;
        }

        return true;
    }

    private void connect(BluetoothDevice device) {
        mConnector.connect(device);
    }

    //region RDConnector Delegate
    @Override
    public void connectorDidStartScan(RDConnector connector) {

    }

    @Override
    public void connectorDidStopScan(RDConnector connector) {

    }

    @Override
    public void connectorDidDiscover(BluetoothDevice device) {
        mDevicesListAdapter.setDevices(new ArrayList<>(mConnector.getDiscoveredPeripherals().values()));
    }

    @Override
    public void connectorDidConnect(RDConnector connector, BluetoothDevice device) {
        mRecyclerView.setAdapter(mDeviceInfoListAdapter);
        invalidateOptionsMenu();
    }

    @Override
    public void connectorDidChangeConnectionState(RDConnector connector, int GATTStatus, int newState) {

    }

    @Override
    public void connectorDidDisconnect(RDConnector connector, BluetoothDevice device) {
        invalidateOptionsMenu();
    }

    @Override
    public void connectorDidReceiveData(byte[] data, long tag) {

    }

    @Override
    public void connectorDidReadData(RDConnectorInterface connector, final RDData RDData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDeviceInfoListAdapter.setData(RDData);
            }
        });
    }

    @Override
    public void connectorDidReadDeviceInfo(RDConnector connector) {

    }

    @Override
    public void connectorDidUpdateDeviceInfo(RDConnector connector, HashMap changes) {

    }
    //endregion

    private class DevicesListAdapter extends RecyclerView.Adapter<DeviceViewHolder> {

        List<BluetoothDevice> mDevices;

        @Override
        public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = View.inflate(MainActivity.this, R.layout.row_device, null);
            return new DeviceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(DeviceViewHolder holder, final int position) {
            BluetoothDevice device = mDevices.get(position);

            holder.titleTextView.setText(device.getName());

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    didSelectItem(position);
                }
            });
        }

        private void didSelectItem(int position) {
            BluetoothDevice device = mDevices.get(position);

            connect(device);
        }

        @Override
        public int getItemCount() {
            return mDevices != null ? mDevices.size() : 0;
        }

        public void setDevices(List<BluetoothDevice> devices) {
            mDevices = devices;
            notifyDataSetChanged();
        }
    }

    private class DeviceInfoListAdapter extends RecyclerView.Adapter<DeviceInfoViewHolder> {
        private List<String> items = new ArrayList<>();

        @Override
        public DeviceInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = View.inflate(MainActivity.this, R.layout.row_title_value, null);
            return new DeviceInfoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(DeviceInfoViewHolder holder, int position) {
            String item = items.get(position);

            holder.titleTextView.setText(item);
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        public void setData(RDData data) {
            RDData.Fields fields = data.fields;

            String batt = String.format(Locale.US, "Bat:       %03d%% %s", fields.batteryStatus & 0x7F, (((fields.batteryStatus & 0x80) > 0) ? "1" : "0"));

            String t = String.format(Locale.US, "T:         %09.2f UTC %d sats %sGPS", fields.timeUTC / 1000.0, fields.satellitesNo & 0x7F, ((fields.satellitesNo & 0x80) > 0 ? "D" : ""));
            String v = String.format(Locale.US, "V:         %05.1f km/h", fields.speed / 100.0);
            String l = String.format(Locale.US, "L:         <%f, %f>", (double) fields.latitude / 100000.0 / 60.0, (double) fields.longitude / 100000.0 / -60.0);
            String he = String.format(Locale.US, "H:         %06.2f\u00B0", fields.heading / 100.0);
            String hd = String.format(Locale.US, "HDOP:      %03.1f", fields.HDOP / 100.0);
            String hi = String.format(Locale.US, "Alt:       %+09.2f", fields.height / 100.0);

            items.clear();
            items.add(batt);
            items.add(t);
            items.add(v);
            items.add(l);
            items.add(he);
            items.add(hd);
            items.add(hi);

            notifyDataSetChanged();
        }
    }
}
