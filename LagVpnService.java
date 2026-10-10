package com.kestrel.lag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class LagVpnService extends VpnService implements Runnable {

    private static final String TAG = "KestrelLag";
    private static final String CHANNEL = "kestrel_lag";

    private ParcelFileDescriptor vpnInterface;
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int lagMs = 300;
    private volatile int openMs = 700;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("STOP".equals(action)) {
                stopVpn();
                return START_NOT_STICKY;
            }
            if (intent.hasExtra("lag_ms")) lagMs = intent.getIntExtra("lag_ms", 300);
            if (intent.hasExtra("open_ms")) openMs = intent.getIntExtra("open_ms", 700);
            if ("START".equals(action)) startVpn();
        }
        return START_STICKY;
    }

    private void startVpn() {
        if (running.get()) return;
        try {
            Builder b = new Builder();
            b.setSession("Kestrel Lag");
            b.addAddress("10.8.0.1", 32);
            b.addRoute("0.0.0.0", 0);
            b.addDnsServer("1.1.1.1");

            try { b.addAllowedApplication("com.dts.freefireth"); } catch (Exception ignored) {}
            try { b.addAllowedApplication("com.dts.freefiremax"); } catch (Exception ignored) {}

            vpnInterface = b.establish();
            if (vpnInterface == null) { Log.e(TAG, "establish failed"); return; }

            running.set(true);
            worker = new Thread(this);
            worker.start();
            startForegroundNotification();
            Log.i(TAG, "VPN started lag=" + lagMs + " open=" + openMs);
        } catch (Exception e) {
            Log.e(TAG, "startVpn", e);
        }
    }

    private void stopVpn() {
        running.set(false);
        if (worker != null) { worker.interrupt(); worker = null; }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception ignored) {}
            vpnInterface = null;
        }
        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN stopped");
    }

    @Override
    public void run() {
        if (vpnInterface == null) return;
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        ByteBuffer buf = ByteBuffer.allocate(32767);

        while (running.get()) {
            try {
                buf.clear();
                int len = in.read(buf.array());
                if (len <= 0) { Thread.sleep(10); continue; }
                buf.limit(len);
                byte[] data = new byte[len];
                buf.get(data);

                if (isOutgoing(data)) {
                    Thread.sleep(lagMs);
                    if (!running.get()) break;
                    out.write(data);
                    out.flush();
                    Thread.sleep(openMs);
                } else {
                    out.write(data);
                    out.flush();
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "loop", e);
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }
    }

    private boolean isOutgoing(byte[] p) {
        if (p.length < 20) return false;
        int v = (p[0] >> 4) & 0xF;
        if (v == 4) {
            int src = ((p[12] & 0xFF) << 24) | ((p[13] & 0xFF) << 16)
                    | ((p[14] & 0xFF) << 8) | (p[15] & 0xFF);
            return (src & 0xFF000000) == 0x0A080000;
        }
        return false;
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Kestrel Lag", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Kestrel Lag")
                .setContentText("Lag switch running")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
