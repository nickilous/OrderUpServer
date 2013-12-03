package com.nickilous.orderupserver;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.orderupserver.R;
import com.nickilous.OrderMessage;
import com.nickilous.ui.SystemUIHider;

public class OrderUpServerMainActivity extends Activity {
    private View mFullScreenContentView;
    private ListView mOrderNumberListView;
    private ListView mOrderTimeListView;
    private ArrayAdapter<String> mOrderNumberListAdapter;
    private ArrayAdapter<String> mOrderTimeListAdapter;
    private Handler mUpdateHandler;
    WifiStateBroadcastReceiver mWifiStateBroadcastReceiver;
    IntentFilter mWifiIntentFilter = new IntentFilter();

    private FragmentManager mFragmentManager = getFragmentManager();
    private MyInstructionDialog mMyInstructionDialog;

    private SystemUIHider mSystemUIHider;
    private static final int HIDER_FLAGS = SystemUIHider.FLAG_HIDE_NAVIGATION;

    public static final String TAG = "OrderUpServerMainActivity";

    private OrderUpConnection mConnection;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        mOrderNumberListView = (ListView) findViewById(R.id.order_number_list);
        mOrderNumberListAdapter = new ArrayAdapter<String>(this, R.layout.list_view_center_layout, R.id.textItem);
        mOrderNumberListView.setAdapter(mOrderNumberListAdapter);

        mOrderTimeListView = (ListView) findViewById(R.id.order_time_list);
        mOrderTimeListAdapter = new ArrayAdapter<String>(this, R.layout.list_view_center_layout, R.id.textItem);
        mOrderTimeListView.setAdapter(mOrderTimeListAdapter);

        mFullScreenContentView = (View) findViewById(R.id.full_screen_content);

        mUpdateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {


                if (msg.arg1 == OrderUpConnection.CONNECTED) {
                    dismissInstructionDialog();
                    Toast.makeText(getApplicationContext(), "You Have Connected", Toast.LENGTH_LONG).show();
                    mSystemUIHider.hide();

                } else if(msg.arg1  == OrderUpMessageType.ADD_ORDER) {
                    OrderMessage orderMessage = (OrderMessage) msg.getData().getSerializable("orderMessage");
                    addOrder(orderMessage);
                } else if(msg.arg1 == OrderUpMessageType.REMOVE_ORDER){
                    OrderMessage orderMessage = (OrderMessage) msg.getData().getSerializable("orderMessage");
                    removeOrder(orderMessage);
                }

            }
        };

        mConnection = new OrderUpConnection(mUpdateHandler);
        mWifiIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);



        mWifiStateBroadcastReceiver = new WifiStateBroadcastReceiver();

        registerReceiver(mWifiStateBroadcastReceiver, mWifiIntentFilter);


        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUIHider = SystemUIHider.getInstance(this, mFullScreenContentView, HIDER_FLAGS);
        mSystemUIHider.setup();
        mSystemUIHider.hide();

        getActionBar().hide();





    }



    public void addOrder(OrderMessage orderMessage) {
        mOrderNumberListAdapter.add(orderMessage.getOrderInfo().getmOrderNumber());
        mOrderNumberListAdapter.notifyDataSetChanged();
        mOrderTimeListAdapter.add(orderMessage.getOrderInfo().getmOrderTime());
        mOrderTimeListAdapter.notifyDataSetChanged();
        mOrderNumberListView.smoothScrollToPosition(mOrderNumberListAdapter.getCount());
        mOrderTimeListView.smoothScrollToPosition(mOrderNumberListAdapter.getCount());
    }

    public void removeOrder(OrderMessage orderMessage){
        int position = mOrderNumberListAdapter.getPosition(orderMessage.getOrderInfo().getmOrderNumber());
        mOrderNumberListAdapter.remove(orderMessage.getOrderInfo().getmOrderNumber());
        mOrderTimeListAdapter.remove(mOrderTimeListAdapter.getItem(position));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mWifiStateBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mWifiStateBroadcastReceiver, mWifiIntentFilter);

    }

    @Override
    protected void onDestroy() {
        mConnection.tearDown();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public static class MyInstructionDialog extends DialogFragment {
        String ipAddress;

        public MyInstructionDialog(String ipAddress) {
            // Empty constructor required for DialogFragment
            this.ipAddress = ipAddress;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_instructions_layout, container);
            TextView ipAddressTextView = (TextView) view.findViewById(R.id.ip_address_txt);


            ipAddressTextView.setText(ipAddress);
            getDialog().setTitle("Instructions");


            return view;
        }
    }

    public class WifiStateBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager conMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] netInf = conMgr.getAllNetworkInfo();
            for(NetworkInfo inf : netInf){
                if(inf.getTypeName().contains("WIFI"))
                {
                    if(inf.isConnected()){
                        //Toast.makeText(getApplicationContext(), "WiFi is connected.",Toast.LENGTH_LONG).show();
                        showInstructionDialog(mConnection.getIPAddress(true));

                    }
                    else{
                        Toast.makeText(getApplicationContext(), "WiFi NOT connected.",Toast.LENGTH_LONG).show();

                    }
                }
            }

        }
    }

    private void showInstructionDialog(String ipAddress) {
        if(mMyInstructionDialog == null){
            mMyInstructionDialog = new MyInstructionDialog(ipAddress);
            mMyInstructionDialog.show(mFragmentManager, "fragment_instructions_layout");
        }

    }

    private void dismissInstructionDialog() {
        mMyInstructionDialog.dismiss();

    }


}