package com.kestrel.lag;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST = 1001;
    private static final int OVERLAY_REQUEST = 1002;

    private Button toggleBtn;
    private Button overlayBtn;
    private TextView statusTxt;
    private TextView lagValTxt;
    private TextView openValTxt;
    private SeekBar lagBar;
    private SeekBar openBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleBtn = findViewById(R.id.toggle_btn);
        overlayBtn = findViewById(R.id.overlay_btn);
        statusTxt = findViewById(R.id.status_txt);
        lagBar = findViewById(R.id.lag_duration);
        openBar = findViewById(R.id.open_duration);
        lagValTxt = findViewById(R.id.lag_val);
        openValTxt = findViewById(R.id.open_val);

        lagBar.setMax(2000);
        lagBar.setProgress(Prefs.lagMs(this));
        openBar.setMax(2000);
        openBar.setProgress(Prefs.openMs(this));

        lagValTxt.setText(Prefs.lagMs(this) + "ms");
        openValTxt.setText(Prefs.openMs(this) + "ms");

        lagBar.setOnSeekBarChangeListener(new Seek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                Prefs.setLagMs(MainActivity.this, p);
                lagValTxt.setText(p + "ms");
                notifyService();
            }
        });

        openBar.setOnSeekBarChangeListener(new Seek() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                Prefs.setOpenMs(MainActivity.this, p);
                openValTxt.setText(p + "ms");
                notifyService();
            }
        });

        toggleBtn.setOnClickListener(v -> toggleVpn());
        overlayBtn.setOnClickListener(v -> requestOverlay());

        updateUI();
    }

    private void toggleVpn() {
        if (Prefs.enabled(this)) {
            Intent i = new Intent(this, LagVpnService.class);
            i.setAction("STOP");
            startService(i);
            Prefs.setEnabled(this, false);
            updateUI();
        } else {
            Intent prep = VpnService.prepare(this);
            if (prep != null) {
                startActivityForResult(prep, VPN_REQUEST);
            } else {
                startVpn();
            }
        }
    }

    private void startVpn() {
        Intent i = new Intent(this, LagVpnService.class);
        i.setAction("START");
        i.putExtra("lag_ms", Prefs.lagMs(this));
        i.putExtra("open_ms", Prefs.openMs(this));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Prefs.setEnabled(this, true);
        updateUI();
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, OVERLAY_REQUEST);
                return;
            }
        }
        startFloating();
    }

    private void startFloating() {
        Intent i = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "Floating button ON", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void notifyService() {
        if (Prefs.enabled(this)) {
            Intent i = new Intent(this, LagVpnService.class);
            i.setAction("UPDATE");
            i.putExtra("lag_ms", Prefs.lagMs(this));
            i.putExtra("open_ms", Prefs.openMs(this));
            startService(i);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == VPN_REQUEST) {
            if (res == RESULT_OK) startVpn();
            else Toast.makeText(this, "VPN denied", Toast.LENGTH_SHORT).show();
        }
        if (req == OVERLAY_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                startFloating();
            } else {
                Toast.makeText(this, "Overlay denied", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(req, res, data);
    }

    private void updateUI() {
        boolean on = Prefs.enabled(this);
        statusTxt.setText(on ? "RUNNING" : "STOPPED");
        toggleBtn.setText(on ? "STOP" : "START");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private abstract static class Seek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
