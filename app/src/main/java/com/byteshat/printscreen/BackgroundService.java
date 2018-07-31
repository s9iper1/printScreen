package com.byteshat.printscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.ToneGenerator;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundService extends Service {

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_RESULT_INTENT = "resultIntent";

    private static final int NOTIFY_ID = 9906;
    static final String ACTION_RECORD = BuildConfig.APPLICATION_ID + ".RECORD";
    static final String ACTION_SHUTDOWN = BuildConfig.APPLICATION_ID + ".SHUTDOWN";
    static final int VIRT_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    final private HandlerThread handlerThread = new HandlerThread(getClass().getSimpleName(),
            android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler handler;
    private MediaProjectionManager mgr;
    private WindowManager wmgr;
    private ImageTransmogrifier it;
    private int resultCode;
    private Intent resultData;
    final private ToneGenerator beeper = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
    public String mPath;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_FOREGROUND_SERVICE, "My foreground service onCreate()");
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        scheduleTimer();
        Date now = new Date();
        String folder_main = "NewFolder";
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
        try {
            mPath = Environment.getExternalStorageDirectory() + "/" + folder_main + "/" + now + ".jpg";
            String Path = Environment.getExternalStorageDirectory() + "/" + folder_main;
            File folder = new File(Path);
            File imageFile = new File(mPath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            if (!imageFile.exists()) {
                imageFile.createNewFile();
            }
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public BackgroundService() {
    }

    private void scheduleTimer() {
        Timer gameTimer = new Timer();
        TimerTask doThis;

        int delay = 200;   // delay for 5 sec.
        int period = 1000;  // repeat every sec.
        doThis = new TimerTask() {
            public void run() {
                startCapture();
            }
        };
        gameTimer.scheduleAtFixedRate(doThis, delay, period);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String startAction = intent.getAction();
            if (startAction != null) {
                resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 1337);
                resultData = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
                switch (startAction) {
                    case ACTION_START_FOREGROUND_SERVICE:
                        startForegroundService();
                        Toast.makeText(getApplicationContext(), "Foreground service is started.", Toast.LENGTH_LONG).show();
                        break;
                    case ACTION_STOP_FOREGROUND_SERVICE:
                        stopForegroundService();
                        Toast.makeText(getApplicationContext(), "Foreground service is stopped.", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundService() {
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");

        // Create notification default intent.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        Notification.Builder notification = new Notification.Builder(this, getPackageName())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Honor the shul - merit long life. ~Megillah 27b")
                .setContentIntent(pendingIntent);
        notification.setSound(null);
        notification.setPriority(Notification.PRIORITY_HIGH);
        notification.setVibrate(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(getPackageName());
        }
        createNotificationChannel();
        startForeground(121, notification.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getPackageName();
            String description = "this is foreground ";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(getPackageName(), name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void stopForegroundService() {
        Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.");
        stopForeground(true);
        stopSelf();
    }

    void processImage(final byte[] png) {
        new Thread() {
            @Override
            public void run() {
                File output = new File(Environment.getExternalStorageDirectory() + "screenshot.png");

                try {
                    FileOutputStream fos = new FileOutputStream(output);

                    fos.write(png);
                    fos.flush();
                    fos.getFD().sync();
                    fos.close();

                    MediaScannerConnection.scanFile(BackgroundService.this,
                            new String[]{output.getAbsolutePath()},
                            new String[]{"image/png"},
                            null);
                } catch (Exception e) {
                    Log.e(getClass().getSimpleName(), "Exception writing out screenshot", e);
                }
            }
        }.start();

        beeper.startTone(ToneGenerator.TONE_PROP_ACK);
        stopCapture();
    }

    private void stopCapture() {
        if (projection != null) {
            projection.stop();
            vdisplay.release();
            projection = null;
        }
    }

    private void startCapture() {
        projection = mgr.getMediaProjection(resultCode, resultData);
        it = new ImageTransmogrifier(BackgroundService.this);
        MediaProjection.Callback cb = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                vdisplay.release();
            }
        };

        vdisplay = projection.createVirtualDisplay("andshooter",
                it.getWidth(), it.getHeight(),
                getResources().getDisplayMetrics().densityDpi,
                VIRT_DISPLAY_FLAGS, it.getSurface(), null, handler);
        projection.registerCallback(cb, handler);
    }

    Handler getHandler() {
        return (handler);
    }

    WindowManager getWindowManager() {
        return (wmgr);
    }
}

