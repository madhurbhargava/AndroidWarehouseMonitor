package android.packt.com.androidwarehousemonitor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_LOCATION = 1;
    private static String TAG = "AndroidWarehouseMonitor";
    private static String NAME_TAG = "SensorTag";

    //Service UUIDs
    private static final UUID UUID_IR_TEMPERATURE_SERVICE = UUID.fromString("f000aa00-0451-4000-b000-000000000000");
    private static final UUID UUID_HUMIDITY_SERVICE = UUID.fromString("f000aa20-0451-4000-b000-000000000000");

    //Characteristic UUIDs

    private static final UUID UUID_CHARACTERISTIC_TEMPERATURE_DATA = UUID.fromString("f000aa01-0451-4000-b000-000000000000");
    private static final UUID UUID_CHARACTERISTIC_TEMPERATURE_CONFIG = UUID.fromString("f000aa02-0451-4000-b000-000000000000");
    private static final UUID UUID_CHARACTERISTIC_HUMIDITY_DATA = UUID.fromString("f000aa21-0451-4000-b000-000000000000");
    private static final UUID UUID_CHARACTERISTIC_HUMIDITY_CONFIG = UUID.fromString("f000aa22-0451-4000-b000-000000000000");

    //Descriptor
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    private boolean connected = false;
    private boolean enableHumidityFetch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager != null ? btManager.getAdapter() : null;
        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(),
                    "No Bluetooth Support found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        checkPermissions(btAdapter);
    }

    private void checkPermissions(BluetoothAdapter bluetoothAdapter) {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        ensureLocationPermissionIsEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission Granted");
                    startScanning();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Location Permission Not granted", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            }
            default:
        }
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == -1) {
            ensureLocationPermissionIsEnabled();
            return;
        }
        Toast.makeText(this, "Bluetooth not turned on", Toast.LENGTH_LONG).show();
        finish();
    }

    //TODO: Update the UI here
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    private void ensureLocationPermissionIsEnabled() {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        startScanning();
    }

    protected void startScanning() {
        bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        AsyncTask.execute(new Runnable() { @Override public void run() { bluetoothLeScanner.startScan(leScanCallback); } });
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getDevice() != null) {
                if (result.getDevice().getName() != null && result.getDevice().getName().contains(NAME_TAG)) {
                    Log.i(TAG, result.getDevice().getName());
                    if (connected == false) {
                        connected = true;
                        bluetoothLeScanner.stopScan(leScanCallback);
                        result.getDevice().connectGatt(MainActivity.this, true, gattCallback);
                    }
                }
            }
        }
    };

    protected BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_CONNECTED");
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange() - STATE_DISCONNECTED");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            gatt.readCharacteristic(gatt.getService(UUID_IR_TEMPERATURE_SERVICE).getCharacteristic(UUID_CHARACTERISTIC_TEMPERATURE_DATA));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(characteristic.getUuid().equals(UUID_CHARACTERISTIC_TEMPERATURE_DATA) || characteristic.getUuid().equals(UUID_CHARACTERISTIC_HUMIDITY_DATA)) {
                //Enable local notifications
                gatt.setCharacteristicNotification(characteristic, true);
                //Enabled remote notifications
                BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(desc);
            }
            else if (characteristic.getUuid().equals(UUID_CHARACTERISTIC_TEMPERATURE_CONFIG) || characteristic.getUuid().equals(UUID_CHARACTERISTIC_HUMIDITY_CONFIG)) {
                characteristic.setValue(new byte[] {0x01});
                gatt.writeCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (characteristic.getUuid().equals(UUID_CHARACTERISTIC_TEMPERATURE_DATA)) {
                final double ambient = Utilities.extractAmbientTemperature(characteristic);

                // Upload to Firebase Backend
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                DatabaseReference myRef = database.getReference("Temperature");

                myRef.setValue(ambient);
                //Update the UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)findViewById(R.id.textView)).setText("Temperature: "+ambient+"\u00b0"+"C");
                    }
                });
            }else if (characteristic.getUuid().equals(UUID_CHARACTERISTIC_HUMIDITY_DATA)) {
                double humidity = Utilities.extractHumidity(characteristic);
            }

            if(enableHumidityFetch == false) {
                enableHumidityFetch = true;
                gatt.readCharacteristic(gatt.getService(UUID_HUMIDITY_SERVICE).getCharacteristic(UUID_CHARACTERISTIC_HUMIDITY_DATA));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            enableConfigurationForCharacteristic(gatt, descriptor.getCharacteristic());
        }

        private void enableConfigurationForCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UUID_CHARACTERISTIC_TEMPERATURE_DATA)) {
                gatt.readCharacteristic(gatt.getService(UUID_IR_TEMPERATURE_SERVICE).getCharacteristic(UUID_CHARACTERISTIC_TEMPERATURE_CONFIG));
            } else if(characteristic.getUuid().equals(UUID_CHARACTERISTIC_HUMIDITY_DATA)) {
                gatt.readCharacteristic(gatt.getService(UUID_HUMIDITY_SERVICE).getCharacteristic(UUID_CHARACTERISTIC_HUMIDITY_CONFIG));
            }
        }
    };
}
