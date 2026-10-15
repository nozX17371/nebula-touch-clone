package com.zamzzz.ztouch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class TouchService extends AccessibilityService {

    private int tapDelay = 100;
    private int tapDuration = 50;
    private int swipeDuration = 300;
    private int swipeDistance = 500;

    private WindowManager windowManager;
    private View overlayView;
    private View crosshairView;
    private boolean autoTapRunning = false;
    private boolean autoSwipeRunning = false;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Thread autoTapThread;
    private Thread autoSwipeThread;
    private Thread playThread;

    private int tapX = 500;
    private int tapY = 500;

    private long recordStartTime = 0;

    private final List<MacroEvent> recordedEvents = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    static class MacroEvent {
        long time;
        int x, y;
        int x2, y2;
        boolean isSwipe;
        MacroEvent(long t, int x, int y) {
            this.time = t; this.x = x; this.y = y; this.isSwipe = false;
        }
        MacroEvent(long t, int x, int y, int x2, int y2) {
            this.time = t; this.x = x; this.y = y; this.x2 = x2; this.y2 = y2; this.isSwipe = true;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showOverlay();
        Toast.makeText(this, "Z Touch FULL aktif", Toast.LENGTH_SHORT).show();
    }

    private void showOverlay() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        Button btnTap = overlayView.findViewById(R.id.btn_tap);
        Button btnSwipe = overlayView.findViewById(R.id.btn_swipe);
        Button btnRec = overlayView.findViewById(R.id.btn_rec);
        Button btnPlay = overlayView.findViewById(R.id.btn_play);
        Button btnCross = overlayView.findViewById(R.id.btn_cross);
        Button btnSet = overlayView.findViewById(R.id.btn_set);
        Button btnClose = overlayView.findViewById(R.id.btn_close);

        btnTap.setOnClickListener(v -> {
            if (autoTapRunning) {
                stopAutoTap();
                btnTap.setText("TAP");
            } else {
                startAutoTap();
                btnTap.setText("STOP");
            }
        });

        btnSwipe.setOnClickListener(v -> {
            if (autoSwipeRunning) {
                stopAutoSwipe();
                btnSwipe.setText("SWIPE");
            } else {
                startAutoSwipe();
                btnSwipe.setText("STOP");
            }
        });

        btnRec.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                btnRec.setText("REC");
                Toast.makeText(this, "Rekaman selesai: " + recordedEvents.size() + " event", Toast.LENGTH_SHORT).show();
            } else {
                startRecording();
                btnRec.setText("STOP");
            }
        });

        btnPlay.setOnClickListener(v -> {
            if (isPlaying) {
                stopPlaying();
                btnPlay.setText("PLAY");
            } else {
                if (recordedEvents.isEmpty()) {
                    Toast.makeText(this, "Belum ada macro", Toast.LENGTH_SHORT).show();
                    return;
                }
                startPlaying();
                btnPlay.setText("STOP");
            }
        });

        btnCross.setOnClickListener(v -> showCrosshair());

        btnSet.setOnClickListener(v -> showSettings());

        btnClose.setOnClickListener(v -> {
            stopAll();
            if (overlayView != null) windowManager.removeView(overlayView);
            if (crosshairView != null) windowManager.removeView(crosshairView);
            overlayView = null;
            crosshairView = null;
        });

        enableDrag(overlayView, params);
        windowManager.addView(overlayView, params);
    }

    private void enableDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float touchX, touchY;
            boolean dragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragging = true;
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(view, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) view.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void showCrosshair() {
        if (crosshairView != null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        crosshairView = LayoutInflater.from(this).inflate(R.layout.crosshair_layout, null);

        View target = crosshairView.findViewById(R.id.crosshair_target);
        WindowManager.LayoutParams targetParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        targetParams.gravity = Gravity.TOP | Gravity.START;
        targetParams.x = 300;
        targetParams.y = 300;

        enableDrag(target, targetParams);

        crosshairView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                tapX = (int) event.getRawX();
                tapY = (int) event.getRawY();
                Toast.makeText(this, "Titik tap: " + tapX + ", " + tapY, Toast.LENGTH_SHORT).show();
                windowManager.removeView(crosshairView);
                crosshairView = null;
            }
            return true;
        });

        windowManager.addView(crosshairView, params);
    }

    private void startAutoTap() {
        autoTapRunning = true;
        autoTapThread = new Thread(() -> {
            while (autoTapRunning) {
                performTap(tapX, tapY, tapDuration);
                try { Thread.sleep(tapDelay); } catch (InterruptedException e) { break; }
            }
        });
        autoTapThread.start();
    }

    private void stopAutoTap() {
        autoTapRunning = false;
        if (autoTapThread != null) {
            autoTapThread.interrupt();
            autoTapThread = null;
        }
    }

    private void startAutoSwipe() {
        autoSwipeRunning = true;
        autoSwipeThread = new Thread(() -> {
            while (autoSwipeRunning) {
                int startX = tapX;
                int startY = tapY;
                int endX = tapX;
                int endY = tapY + swipeDistance;
                performSwipe(startX, startY, endX, endY, swipeDuration);
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
        });
        autoSwipeThread.start();
    }

    private void stopAutoSwipe() {
        autoSwipeRunning = false;
        if (autoSwipeThread != null) {
            autoSwipeThread.interrupt();
            autoSwipeThread = null;
        }
    }

    private void startRecording() {
        recordedEvents.clear();
        recordStartTime = System.currentTimeMillis();
        isRecording = true;
        Toast.makeText(this, "Mulai rekam. Tap di layar.", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        isRecording = false;
    }

    private void startPlaying() {
        isPlaying = true;
        playThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            for (MacroEvent ev : recordedEvents) {
                if (!isPlaying) break;
                long wait = ev.time - (System.currentTimeMillis() - start);
                if (wait > 0) {
                    try { Thread.sleep(wait); } catch (InterruptedException e) { break; }
                }
                if (ev.isSwipe) {
                    performSwipe(ev.x, ev.y, ev.x2, ev.y2, swipeDuration);
                } else {
                    performTap(ev.x, ev.y, tapDuration);
                }
            }
            isPlaying = false;
            uiHandler.post(() -> {
                if (overlayView != null) {
                    Button btnPlay = overlayView.findViewById(R.id.btn_play);
                    if (btnPlay != null) btnPlay.setText("PLAY");
                }
            });
        });
        playThread.start();
    }

    private void stopPlaying() {
        isPlaying = false;
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
    }

    private void showSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);
        layout.setBackgroundColor(0xEE000000);

        TextView tv = new TextView(this);
        tv.setText("SETTING\nTap delay: " + tapDelay + "ms\nTap duration: " + tapDuration +
                "ms\nSwipe duration: " + swipeDuration + "ms\nSwipe distance: " + swipeDistance + "px");
        tv.setTextColor(0xFFFFFFFF);
        tv.setPadding(0, 0, 0, 30);
        layout.addView(tv);

        Button btnSpeedUp = new Button(this);
        btnSpeedUp.setText("Tap Delay -20ms (lebih cepat)");
        btnSpeedUp.setOnClickListener(v -> {
            tapDelay = Math.max(20, tapDelay - 20);
            tv.setText("SETTING\nTap delay: " + tapDelay + "ms\nTap duration: " + tapDuration +
                    "ms\nSwipe duration: " + swipeDuration + "ms\nSwipe distance: " + swipeDistance + "px");
        });
        layout.addView(btnSpeedUp);

        Button btnSpeedDown = new Button(this);
        btnSpeedDown.setText("Tap Delay +20ms (lebih lambat)");
        btnSpeedDown.setOnClickListener(v -> {
            tapDelay += 20;
            tv.setText("SETTING\nTap delay: " + tapDelay + "ms\nTap duration: " + tapDuration +
                    "ms\nSwipe duration: " + swipeDuration + "ms\nSwipe distance: " + swipeDistance + "px");
        });
        layout.addView(btnSpeedDown);

        Button btnSwipeDistUp = new Button(this);
        btnSwipeDistUp.setText("Swipe Distance +100px");
        btnSwipeDistUp.setOnClickListener(v -> {
            swipeDistance += 100;
            tv.setText("SETTING\nTap delay: " + tapDelay + "ms\nTap duration: " + tapDuration +
                    "ms\nSwipe duration: " + swipeDuration + "ms\nSwipe distance: " + swipeDistance + "px");
        });
        layout.addView(btnSwipeDistUp);

        Button btnSwipeDistDown = new Button(this);
        btnSwipeDistDown.setText("Swipe Distance -100px");
        btnSwipeDistDown.setOnClickListener(v -> {
            swipeDistance = Math.max(100, swipeDistance - 100);
            tv.setText("SETTING\nTap delay: " + tapDelay + "ms\nTap duration: " + tapDuration +
                    "ms\nSwipe duration: " + swipeDuration + "ms\nSwipe distance: " + swipeDistance + "px");
        });
        layout.addView(btnSwipeDistDown);

        Button btnClose = new Button(this);
        btnClose.setText("TUTUP");
        layout.addView(btnClose);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        windowManager.addView(layout, params);
        btnClose.setOnClickListener(v -> windowManager.removeView(layout));
    }

    private void performTap(int x, int y, int duration) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void performSwipe(int x1, int y1, int x2, int y2, int duration) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void stopAll() {
        stopAutoTap();
        stopAutoSwipe();
        stopRecording();
        stopPlaying();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isRecording && event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            long t = System.currentTimeMillis() - recordStartTime;
            recordedEvents.add(new MacroEvent(t, tapX, tapY));
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        stopAll();
        if (overlayView != null) windowManager.removeView(overlayView);
        if (crosshairView != null) windowManager.removeView(crosshairView);
        super.onDestroy();
    }
}
