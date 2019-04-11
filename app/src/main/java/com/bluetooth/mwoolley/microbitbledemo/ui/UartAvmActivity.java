package com.bluetooth.mwoolley.microbitbledemo.ui;
/*
 * Author: Martin Woolley
 * Twitter: @bluetooth_mdw
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bluetooth.mwoolley.microbitbledemo.Constants;
import com.bluetooth.mwoolley.microbitbledemo.MicroBit;
import com.bluetooth.mwoolley.microbitbledemo.R;
import com.bluetooth.mwoolley.microbitbledemo.Utility;
import com.bluetooth.mwoolley.microbitbledemo.bluetooth.BleAdapterService;
import com.bluetooth.mwoolley.microbitbledemo.bluetooth.ConnectionStatusListener;

import java.nio.charset.StandardCharsets;

public class UartAvmActivity extends AppCompatActivity implements ConnectionStatusListener, SensorEventListener {

    private BleAdapterService bluetooth_le_adapter;

    private boolean exiting = false;
    private boolean indications_on = false;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(Constants.TAG, "onServiceConnected");
            bluetooth_le_adapter = ((BleAdapterService.LocalBinder) service).getService();
            bluetooth_le_adapter.setActivityHandler(mMessageHandler);

            if (bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), true)) {
                showMsg(Utility.htmlColorGreen("UART TX indications ON"));
            } else {
                showMsg(Utility.htmlColorRed("Failed to set UART TX indications ON"));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetooth_le_adapter = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_uart_avm);
        getSupportActionBar().setTitle(R.string.screen_title_UART_AVM);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // read intent data
        final Intent intent = getIntent();
        MicroBit.getInstance().setConnection_status_listener(this);
        myOnCreate();
        // connect to the Bluetooth smart service
        Intent gattServiceIntent = new Intent(this, BleAdapterService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        Log.d(Constants.TAG, "onDestroy");
        super.onDestroy();
        if (indications_on) {
            exiting = true;
            bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), false);
        }
        try {
            // may already have unbound. No API to check state so....
            unbindService(mServiceConnection);
        } catch (Exception e) {
        }
    }

    public void onBackPressed() {
        Log.d(Constants.TAG, "onBackPressed");
        if (MicroBit.getInstance().isMicrobit_connected() && indications_on) {
            exiting = true;
            bluetooth_le_adapter.setIndicationsState(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID), false);
        }
        exiting = true;
        if (!MicroBit.getInstance().isMicrobit_connected()) {
            try {
                // may already have unbound. No API to check state so....
                unbindService(mServiceConnection);
            } catch (Exception e) {
            }
        }
        finish();
        exiting = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_uart_avm, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.menu_uart_avm_new_game) {
            return true;
        }

        if (id == R.id.menu_uart_avm_help) {
            Intent intent = new Intent(UartAvmActivity.this, HelpActivity.class);
            intent.putExtra(Constants.URI, Constants.UART_AVM_HELP);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //region Service message handler
    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle;
            String service_uuid = "";
            String characteristic_uuid = "";
            String descriptor_uuid = "";
            byte[] b = null;
            TextView value_text = null;

            switch (msg.what) {
                case BleAdapterService.GATT_CHARACTERISTIC_WRITTEN:
                    Log.d(Constants.TAG, "Handler received characteristic written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    Log.d(Constants.TAG, "characteristic " + characteristic_uuid + " of service " + service_uuid + " written OK");
                    showMsg(Utility.htmlColorGreen("Ready"));
                    break;
                case BleAdapterService.GATT_DESCRIPTOR_WRITTEN:
                    Log.d(Constants.TAG, "Handler received descriptor written result");
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    descriptor_uuid = bundle.getString(BleAdapterService.PARCEL_DESCRIPTOR_UUID);
                    Log.d(Constants.TAG, "descriptor " + descriptor_uuid + " of characteristic " + characteristic_uuid + " of service " + service_uuid + " written OK");
                    if (!exiting) {
                        showMsg(Utility.htmlColorGreen("UART TX indications ON"));
                        indications_on = true;
                    } else {
                        showMsg(Utility.htmlColorGreen("UART TX indications OFF"));
                        indications_on = false;
                        finish();
                    }
                    break;

                case BleAdapterService.NOTIFICATION_OR_INDICATION_RECEIVED:
                    bundle = msg.getData();
                    service_uuid = bundle.getString(BleAdapterService.PARCEL_SERVICE_UUID);
                    characteristic_uuid = bundle.getString(BleAdapterService.PARCEL_CHARACTERISTIC_UUID);
                    b = bundle.getByteArray(BleAdapterService.PARCEL_VALUE);
                    Log.d(Constants.TAG, "Value=" + Utility.byteArrayAsHexString(b));
                    if (characteristic_uuid.equalsIgnoreCase((Utility.normaliseUUID(BleAdapterService.UART_TX_CHARACTERISTIC_UUID)))) {
                        String ascii = "";
                        Log.d(Constants.TAG, "UART TX received");
                        ascii = new String(b, StandardCharsets.US_ASCII);
                        Log.d(Constants.TAG, "micro:bit answer: " + ascii);
                        if (!ascii.equals(Constants.AVM_CORRECT_RESPONSE)) {
                            showAnswer(ascii);
                        } else {

                        }
                    }
                    break;
                case BleAdapterService.MESSAGE:
                    bundle = msg.getData();
                    String text = bundle.getString(BleAdapterService.PARCEL_TEXT);
                    showMsg(Utility.htmlColorRed(text));
            }
        }
    };
    //endregion

    private void showMsg(final String msg) {
        Log.d(Constants.TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) UartAvmActivity.this.findViewById(R.id.message)).setText(Html.fromHtml(msg));
            }
        });
    }

    private void showAnswer(String answer) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Answer");
        builder.setMessage(answer);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }


    @Override
    public void connectionStatusChanged(boolean connected) {
        if (connected) {
            showMsg(Utility.htmlColorGreen("Connected"));
        } else {
            showMsg(Utility.htmlColorRed("Disconnected"));
        }
    }

    @Override
    public void serviceDiscoveryStatusChanged(boolean new_state) {
    }

    public void SendText(String text) {

        byte[] ascii_bytes = text.getBytes(StandardCharsets.US_ASCII);
        Log.d(Constants.TAG, "ASCII bytes: 0x" + Utility.byteArrayAsHexString(ascii_bytes));
        bluetooth_le_adapter.writeCharacteristic(Utility.normaliseUUID(BleAdapterService.UARTSERVICE_SERVICE_UUID), Utility.normaliseUUID(BleAdapterService.UART_RX_CHARACTERISTIC_UUID), ascii_bytes);


    }

    //MYCODE------------------------------------------------------------------------------------------------------------------
    private int max = 5;
    private final float MIN_ACTIVATE = 1.3f;
    SensorManager sensorManager;
    TextView disp1, disp2, sensyDisp;
    SeekBar sensy;

    void myOnCreate() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        disp1 = (TextView) findViewById(R.id.tv);
        disp2 = (TextView) findViewById(R.id.tv2);
        sensyDisp = (TextView) findViewById(R.id.tv3);
        sensy = (SeekBar) findViewById(R.id.sb);
        sensy.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                max = progress;
                sensyDisp.setText("Érzékenyég: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        motorCalculator(Limiter(event.values[0]), Limiter(-event.values[1]));//Y
    }

    private float Limiter(float a) {
        if (a < MIN_ACTIVATE && a > -MIN_ACTIVATE) a = 0;
        if (a > max) a = max;
        if (a < -max) a = -max;
        a = a / max;
        return a;
    }

    private void motorCalculator(float turn, float forward) {

        float left = forward;
        float right = forward;
        if (turn < 0)
            if (forward >= 0)
                right = right + turn;
            else
                right = right - turn;
        else if (turn > 0)
            if (forward >= 0)
                left = left - turn;
            else
                left = left + turn;

        //Innen már csak kiírás
        String L = String.valueOf(Math.round(left * 100)), R = String.valueOf(Math.round(right * 100));
        disp1.setText(L + "%");
        disp2.setText(R + "%");
        //string előkészítése
        String Kessz = PrepareString(L) + PrepareString(R);
        Log.d("eredmeny", "Késsz: " + Kessz);
        try {
            SendText(":" + Kessz + ":");
        } catch (Exception e) {
            Log.d("cucc", e.getMessage());
        }

    }

    //A beérkező stringet 4 hosszúvá lakakítja
    private String PrepareString(String a) {
        if (a.length() < 4) {
            byte lenght = (byte) a.length();
            if (a.charAt(0) == '-') {
                a = a.substring(1);
                for (int i = 0; i < 4 - lenght; i++) {
                    a = "0" + a;
                }
                a = "-" + a;
            } else {
                for (int i = 0; i < 4 - lenght; i++) {
                    a = "0" + a;
                }
            }
        }
        return a;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor gyorsulas = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, gyorsulas, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SendText(":00000000:");
        sensorManager.unregisterListener(this);
    }

}
//asd