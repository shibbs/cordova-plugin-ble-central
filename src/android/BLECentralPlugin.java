// (c) 2104 Don Coleman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.megster.cordova.ble.central;

import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Build;

import android.provider.Settings;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.*;

public class BLECentralPlugin extends CordovaPlugin implements BluetoothAdapter.LeScanCallback {

    // actions
    private static final String SCAN = "scan";
    private static final String START_SCAN = "startScan";
    private static final String STOP_SCAN = "stopScan";

    private static final String LIST = "list";

    private static final String CONNECT = "connect";
    private static final String DISCONNECT = "disconnect";

    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String WRITE_WITHOUT_RESPONSE = "writeWithoutResponse";

    private static final String START_NOTIFICATION = "startNotification";
    private static final String STOP_NOTIFICATION = "stopNotification";

    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED  = "isConnected";

    private static final String SETTINGS = "showBluetoothSettings";
    private static final String ENABLE = "enable";

    private static final String BMTU = "increaseMTU";

    // callbacks
    CallbackContext discoverCallback;
    private CallbackContext enableBluetoothCallback;
    CallbackContext mtuCallback;

    private static final String TAG = "BLEPlugin";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    BluetoothAdapter bluetoothAdapter;

    // key is the MAC Address
    Map<String, Peripheral> peripherals = new LinkedHashMap<String, Peripheral>();

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        LOG.d(TAG, "action = " + action);

        if (bluetoothAdapter == null) {
            Activity activity = cordova.getActivity();
            BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        boolean validAction = true;

        if (action.equals(SCAN)) {

            UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
            int scanSeconds = args.getInt(1);
            findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds);

                } else if (action.equals(START_SCAN)) {

                    UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
                    findLowEnergyDevices(callbackContext, serviceUUIDs, -1);

                } else if (action.equals(STOP_SCAN)) {

                    bluetoothAdapter.stopLeScan(this);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        final ScanCallback stopCallback = new ScanCallback() {
                            @Override
                            public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        super.onScanFailed(errorCode);
                        LOG.v("ScanTask", "Some error occurred");
                    }
                };

                BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();

                System.out.println("Stopping LE scan");
                scanner.stopScan(stopCallback);
            }

            callbackContext.success();

        } else if (action.equals(LIST)) {

            listKnownDevices(callbackContext);

        } else if (action.equals(CONNECT)) {

            String macAddress = args.getString(0);
            connect(callbackContext, macAddress);

        } else if (action.equals(DISCONNECT)) {
            String macAddress = args.getString(0);
            if (peripherals.containsKey(macAddress) && !peripherals.get(macAddress).isConnected()) {
                disconnect(callbackContext, macAddress);
            } else {
                LOG.v("Already Disconnected");
                callbackContext.success();
            }
        } else if (action.equals(READ)) {

            String macAddress = args.getString(0);
            UUID serviceUUID = uuidFromString(args.getString(1));
            UUID characteristicUUID = uuidFromString(args.getString(2));
            read(callbackContext, macAddress, serviceUUID, characteristicUUID);

        } else if (action.equals(WRITE)) {

            String macAddress = args.getString(0);
            UUID serviceUUID = uuidFromString(args.getString(1));
            UUID characteristicUUID = uuidFromString(args.getString(2));
            byte[] data = args.getArrayBuffer(3);
            int type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type);

        } else if (action.equals(WRITE_WITHOUT_RESPONSE)) {

            String macAddress = args.getString(0);
            UUID serviceUUID = uuidFromString(args.getString(1));
            UUID characteristicUUID = uuidFromString(args.getString(2));
            byte[] data = args.getArrayBuffer(3);
            int type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type);

        } else if (action.equals(START_NOTIFICATION)) {

            String macAddress = args.getString(0);
            UUID serviceUUID = uuidFromString(args.getString(1));
            UUID characteristicUUID = uuidFromString(args.getString(2));
            registerNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);

        } else if (action.equals(STOP_NOTIFICATION)) {

          String macAddress = args.getString(0);
          UUID serviceUUID = uuidFromString(args.getString(1));
          UUID characteristicUUID = uuidFromString(args.getString(2));
          removeNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);
        }

        else if (action.equals(IS_ENABLED)) {

            if (bluetoothAdapter.isEnabled()) {
                callbackContext.success();
            } else {
                callbackContext.error("Bluetooth is disabled.");
            }

        } else if (action.equals(IS_CONNECTED)) {

            String macAddress = args.getString(0);

            if (peripherals.containsKey(macAddress) && peripherals.get(macAddress).isConnected()) {
                callbackContext.success();
            } else {
                callbackContext.error("Not connected.");
            }

        } else if (action.equals(SETTINGS)) {

            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            cordova.getActivity().startActivity(intent);
            callbackContext.success();

        } else if (action.equals(ENABLE)) {

            enableBluetoothCallback = callbackContext;
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);

        } else if (action.equals(BMTU)){

            String macAddress = args.getString(0);
            int mtu = args.getInt(1);
            bumpMTU(callbackContext, macAddress, mtu);
        }else {

            validAction = false;

        }

        return validAction;
    }

    private void removeNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

         Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {

             peripheral.queueRemoveNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

         } else {

             callbackContext.error("Peripheral " + macAddress + " not found");

          }
    }

    private UUID[] parseServiceUUIDList(JSONArray jsonArray) throws JSONException {
        List<UUID> serviceUUIDs = new ArrayList<UUID>();

        for(int i = 0; i < jsonArray.length(); i++){
            String uuidString = jsonArray.getString(i);
            serviceUUIDs.add(uuidFromString(uuidString));
        }

        return serviceUUIDs.toArray(new UUID[jsonArray.length()]);
    }

    private void connect(CallbackContext callbackContext, String macAddress) {

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {
            peripheral.connect(callbackContext, cordova.getActivity());
        } else {
            callbackContext.error("Peripheral " + macAddress + " not found.");
        }

    }

    private void disconnect(CallbackContext callbackContext, String macAddress) {

        Peripheral peripheral = peripherals.get(macAddress);
        
        
        if (peripheral != null) {
            peripheral.disconnect();
        }
        callbackContext.success();

    }

    private void read(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }

        //peripheral.readCharacteristic(callbackContext, serviceUUID, characteristicUUID);
        peripheral.queueRead(callbackContext, serviceUUID, characteristicUUID);

    }

    private void write(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID,
                       byte[] data, int writeType) {

        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }

        //peripheral.writeCharacteristic(callbackContext, serviceUUID, characteristicUUID, data, writeType);
        peripheral.queueWrite(callbackContext, serviceUUID, characteristicUUID, data, writeType);

    }

    private void registerNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {

            //peripheral.setOnDataCallback(serviceUUID, characteristicUUID, callbackContext);
            peripheral.queueRegisterNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

        } else {

            callbackContext.error("Peripheral " + macAddress + " not found");

        }

    }

    private void findLowEnergyDevices(CallbackContext callbackContext, UUID[] serviceUUIDs, int scanSeconds) {

        // TODO skip if currently scanning

        // clear non-connected cached peripherals
        for(Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Peripheral> entry = iterator.next();
            if(!entry.getValue().isConnected()) {
                iterator.remove();
            }
        }

        discoverCallback = callbackContext;

        if (serviceUUIDs.length > 0) {
              bluetoothAdapter.startLeScan(serviceUUIDs, this);
        } else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
              LOG.d("FLED", "Scanning with new API");

              final ScanCallback callback = new ScanCallback() {
                   @Override
                   public void onScanResult(int callbackType, ScanResult result) {
                     LOG.d("onScanResult", "In scanCallback");

                     BluetoothDevice device = result.getDevice();
                     String address = device.getAddress();

                     if (!peripherals.containsKey(address)) {

                        LOG.d("onScanResult", "Adding peripheral & returning result to JS land");
                         Peripheral peripheral = new Peripheral(device, result.getRssi(), result.getScanRecord().getBytes());
                         peripherals.put(address, peripheral);

                         if (discoverCallback != null) {
                             PluginResult presult = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
                             presult.setKeepCallback(true);
                             discoverCallback.sendPluginResult(presult);
                         }
                     }
                   }
                   @Override
                   public void onScanFailed(int errorCode) {
                       super.onScanFailed(errorCode);
                       LOG.v("ScanTask", "Some error occurred");
                  }
              };

              BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();

              scanner.startScan(callback);
            }else{
              bluetoothAdapter.startLeScan(this);
            }

        }

        if (scanSeconds > 0) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    LOG.d(TAG, "Stopping Scan");
                    BLECentralPlugin.this.bluetoothAdapter.stopLeScan(BLECentralPlugin.this);
                }
            }, scanSeconds * 1000);
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void listKnownDevices(CallbackContext callbackContext) {

        JSONArray json = new JSONArray();

        for (Map.Entry<String, Peripheral> entry : peripherals.entrySet()) {
            Peripheral peripheral = entry.getValue();
            json.put(peripheral.device.getAddress());
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK, json);
        callbackContext.sendPluginResult(result);
    }

    private void bumpMTU(CallbackContext callbackContext, String macAddress, int mtu) {

      Peripheral peripheral = peripherals.get(macAddress);

      if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
        callbackContext.error("Device not supported. Requires Lollipop or newer.");
        return;
      }

      if(peripheral == null){
        callbackContext.error("Peripheral not connected");
      }else{
        peripheral.bumpMTU(callbackContext, mtu);
      }
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

        String address = device.getAddress();

        if (!peripherals.containsKey(address)) {

            Peripheral peripheral = new Peripheral(device, rssi, scanRecord);
            peripherals.put(device.getAddress(), peripheral);

            if (discoverCallback != null) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
                result.setKeepCallback(true);
                discoverCallback.sendPluginResult(result);
            }

        } else {
            // this isn't necessary
            Peripheral peripheral = peripherals.get(address);
            peripheral.updateRssi(rssi);
        }

        // TODO offer option to return duplicates

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                LOG.d(TAG, "User enabled Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.success();
                }
            } else {
                LOG.d(TAG, "User did *NOT* enable Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.error("User did not enable Bluetooth");
                }
            }

            enableBluetoothCallback = null;
        }
    }

    private UUID uuidFromString(String uuid) {
        return UUIDHelper.uuidFromString(uuid);
    }

}
