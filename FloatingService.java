package com.kestrel.lag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class FloatingService extends Service {

    private static final String CHANNEL = "kestrel_float";
    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNotification();
        showFloating();
        return START_STICKY;
    }

    private void showFloating() {
        if (floatView != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        floatView.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float touchX, touchY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - touchX);
                        int dy = (int)(e.getRawY() - touchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                        params.x = initX + dx;
                        params.y = initY + dy;
                        wm.updateViewLayout(floatView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggleLag();
                        return true;
                }
                return false;
            }
        });

        wm.addView(floatView, params);
    }

    private void toggleLag() {
        if (Prefs.enabled(this)) {
            Intent i = new Intent(this, LagVpnService.class);
            i.setAction("STOP");
            startService(i);
            Prefs.setEnabled(this, false);
        } else {
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
        }
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Kestrel Float", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Kestrel Float")
                .setContentText("Overlay active")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
        startForeground(2, n);
    }

    @Override
    public void onDestroy() {
        if (floatView != null && wm != null) {
            try { wm.removeView(floatView); } catch (Exception ignored) {}
            floatView = null;
        }
        super.onDestroy();
    }
}
