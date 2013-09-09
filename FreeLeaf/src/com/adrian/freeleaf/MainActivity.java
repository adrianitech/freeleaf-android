package com.adrian.freeleaf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.Resources;
import android.graphics.*;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;
import com.adrian.freeleaf.Utils.DiscoveryThread;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ImageView pulseImage;
    private ImageButton buttonActivate;
    private SlidingPaneLayout slidingPane;
    private ListView listActions;

    private DiscoveryThread discoveryThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Get the controls
        slidingPane = (SlidingPaneLayout)findViewById(R.id.slidingPane);
        listActions = (ListView)findViewById(R.id.listActions);
        pulseImage = (ImageView)findViewById(R.id.pulseImage);
        buttonActivate = (ImageButton)findViewById(R.id.buttonActivate);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Set control options
        slidingPane.setCoveredFadeColor(Color.TRANSPARENT);
        slidingPane.setSliderFadeColor(Color.TRANSPARENT);
        slidingPane.setParallaxDistance(100);


        final Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        final int rad = size.x / 2;

        Bitmap bmp = Bitmap.createBitmap(2*rad, 2*rad, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setDither(true);

        p.setColor(Color.parseColor("#d3f696"));
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(rad, rad, rad, p);

        p.setColor(Color.parseColor("#b1df63"));
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(rad, rad, rad, p);

        pulseImage.setImageBitmap(bmp);


        buttonActivate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                discoveryThread.setIsActive(!discoveryThread.getIsActive());
                CheckButtonState();
            }
        });


        final Resources res = getResources();
        String[] actions = res.getStringArray(R.array.menu_values);

        listActions.setAdapter(new MenuAdapter1(this, android.R.layout.preference_category, actions));
        listActions.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(i == 1) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } else if(i == 2) {

                } else if(i == 4) {

                } else if(i == 5) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Do you really want to exit?")
                            .setMessage("If you exit now all the active file transfers will be intrerupted")
                            .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    System.exit(0);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                }
            }
        });

        discoveryThread = new DiscoveryThread(this);

        final String refreshRate = prefs.getString("discovery_refresh", "3000");
        discoveryThread.setRefreshRate(Integer.parseInt(refreshRate));

        discoveryThread.setIsActive(prefs.getBoolean("auto_discovery", true));
        CheckButtonState();

        discoveryThread.setUsername(prefs.getString("discovery_name", "Unknown"));

        discoveryThread.setDataSentEventListener(new DiscoveryThread.OnDataSentEventListener() {
            @Override
            public void onDataSent() {
                AnimatePulse();
            }
        });

        discoveryThread.start();





        /*IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(rec, ifilter);
        rec.onReceive(this, batteryStatus);


        File path = Environment.getExternalStorageDirectory();
        stat = new StatFs(path.getPath());


        wifiManager = (WifiManager)MainActivity.this.getSystemService(Context.WIFI_SERVICE);
        rssi = wifiManager.getConnectionInfo().getRssi();  */





    }

    public void CheckButtonState() {
        if(discoveryThread.getIsActive()) {
            pulseImage.setVisibility(View.VISIBLE);
            buttonActivate.setImageResource(R.drawable.ic_android_green);
        } else {
            pulseImage.setVisibility(View.INVISIBLE);
            buttonActivate.setImageResource(R.drawable.ic_android_red);
        }
    }

    private void AnimatePulse() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pulseImage.clearAnimation();
                final Animation pulseAnimation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.pulse_animation);
                pulseImage.setAnimation(pulseAnimation);
            }
        });
    }

    /*BroadcastReceiver rec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batt = (int)(100 * level / (float)scale);
        }
    }; */

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if(s.equals("discovery_refresh")) {
            final String value = sharedPreferences.getString("discovery_refresh", "3000");
            discoveryThread.setRefreshRate(Integer.parseInt(value));
        } else if(s.equals("discovery_name")) {
            discoveryThread.setUsername(sharedPreferences.getString("discovery_name", "Unknown"));
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if(slidingPane.isOpen()) {
                slidingPane.closePane();
            } else {
                slidingPane.openPane();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private class MenuAdapter1 extends ArrayAdapter<String> {

        private LayoutInflater mInflater;

        private MenuAdapter1(Context context, int textViewResourceId, String[] objects) {
            super(context, textViewResourceId, objects);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !getItem(position).startsWith("*");
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).startsWith("*") ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        private class ViewHolder {
            public TextView text;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            ViewHolder holder;
            String item = getItem(position);

            if (rowView == null) {
                holder = new ViewHolder();
                if(item.startsWith("*")) {
                    rowView = mInflater.inflate(android.R.layout.preference_category, null);
                    holder.text = (TextView)rowView.findViewById(android.R.id.title);
                    item = item.substring(1);
                } else {
                    rowView = mInflater.inflate(android.R.layout.simple_list_item_1, null);
                    holder.text = (TextView)rowView.findViewById(android.R.id.text1);
                }
                rowView.setTag(holder);
            } else {
                holder = (ViewHolder)rowView.getTag();
            }

            holder.text.setText(item);

            return rowView;
        }
    }
}
