package com.example.anitaimani.mymiddleware;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.IntentFilter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import com.example.anitaimani.mymiddleware.WiFiDirectServicesList.DeviceClickListener;
import com.example.anitaimani.mymiddleware.WiFiDirectServicesList.WiFiDevicesAdapter;

public class MainActivity extends Activity implements WifiP2pManager.ConnectionInfoListener, DeviceClickListener {

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private WifiP2pManager manager;

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "_wifidemotest";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    private WiFiDirectServicesList servicesList;

    private TextView statusTxtView;

    public static final String TAG = "wifidirectdiscovery";
    private WifiP2pDnsSdServiceRequest serviceRequest;

    private BroadcastReceiver receiver = null;

    static final int SERVER_PORT = 4545;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        statusTxtView = (TextView) findViewById(R.id.status_text);

       startService(new Intent(this, FirstService.class));
       // serviceIntent = new Intent(getApplicationContext(),MyService.class);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        startRegistrationAndDiscovery();

        servicesList = new WiFiDirectServicesList();
        getFragmentManager().beginTransaction()
                .add(R.id.container_root, servicesList, "services").commit();




//
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//
//
//
//            }
//        });
        //  isMyServiceRunning();
//        if(!isMyServiceRunning())
//        {
//            Toast.makeText(getBaseContext(), "There is no service running, starting service..", Toast.LENGTH_SHORT).show();
//            //startService(service);
//            startService(new Intent(this, FirstService.class));
//        }else
//        {
//            Toast.makeText(getBaseContext(), "Service is already running", Toast.LENGTH_SHORT).show();
//        }

    }



    @Override
    protected void onRestart() {
        Fragment frag = getFragmentManager().findFragmentByTag("services");
        if (frag != null) {
            getFragmentManager().beginTransaction().remove(frag).commit();
        }
        super.onRestart();
    }

    @Override
    protected void onStop() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                }

            });
        }
        super.onStop();
    }


    /**
     * Registers a local service and then initiates a service discovery
     */
    private void startRegistrationAndDiscovery() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                appendStatus("Failed to add a service");
            }
        });

        discoverService();

    }


    private void discoverService() {

        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                        // A service has been discovered. Is this our app?

                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {

                            // update the UI and add the item the discovered
                            // device.
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                                    .findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDirectServicesList.WiFiDevicesAdapter adapter = ((WiFiDirectServicesList.WiFiDevicesAdapter) fragment
                                        .getListAdapter());
                                WiFiP2pService service = new WiFiP2pService();
                                service.device = srcDevice;
                                service.instanceName = instanceName;
                                service.serviceRegistrationType = registrationType;
                                adapter.add(service);
                                adapter.notifyDataSetChanged();
                                Log.d(TAG, "onBonjourServiceAvailable "
                                        + instanceName);
                            }
                        }

                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {

                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d(TAG,
                                device.deviceName + " is "
                                        + record.get(TXTRECORD_PROP_AVAILABLE));
                    }
                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        appendStatus("Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        appendStatus("Failed adding service discovery request");
                    }
                });
        manager.discoverServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                appendStatus("Service discovery failed");

            }
        });
    }


    public void connectP2p(WiFiP2pService service) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest,
                    new WifiP2pManager.ActionListener() {

                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFailure(int arg0) {
                        }
                    });

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Connecting to service");
            }

            @Override
            public void onFailure(int errorCode) {
                appendStatus("Failed connecting to service");
            }
        });
        finish();
    }


    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {

        Thread handler = null;



        // After the group negotiation, we can determine the group owner.
        if (p2pInfo.groupFormed && p2pInfo.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a server thread and accepting
            // incoming connections.
        } else if (p2pInfo.groupFormed) {
            // The other device acts as the client. In this case,
            // you'll want to create a client thread that connects to the group
            // owner.
        }
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */

//        if (p2pInfo.isGroupOwner) {
//            Log.d(TAG, "Connected as group owner");
//            try {
//                handler = new GroupOwnerSocketHandler(
//                        ((MessageTarget) this).getHandler());
//                handler.start();
//            } catch (IOException e) {
//                Log.d(TAG,
//                        "Failed to create a server thread - " + e.getMessage());
//                return;
//            }
//        } else {
//            Log.d(TAG, "Connected as peer");
//            handler = new ClientSocketHandler(
//                    ((MessageTarget) this).getHandler(),
//                    p2pInfo.groupOwnerAddress);
//            handler.start();
//        }
    }

    public void appendStatus(String status) {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }


    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            String temp = service.service.getClassName();
            if ("com.example.anitaimani.mymiddleware.FirstService".equals(temp)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case FirstService.CLOSE_ACTION:
                exit();
                break;
        }
    }

    private void exit() {
        stopService(new Intent(this, FirstService.class));

        //  isMyServiceRunning();
        if(!isMyServiceRunning())
        {
            Toast.makeText(getBaseContext(), "There is no service running, starting service..", Toast.LENGTH_SHORT).show();
            //startService(service);
//            startService(new Intent(this, FirstService.class));
        }
       finish();
    }

}
