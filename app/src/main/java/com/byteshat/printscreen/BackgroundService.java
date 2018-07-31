package com.byteshat.printscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundService extends Service {
    
    private static final int PORT = 42380;
    private static final int TIMEOUT = 1000;
    private static final int NOTIFY_ID = 9906;
    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_RESULT_INTENT = "resultIntent";
    static final String ACTION_RECORD =
            BuildConfig.APPLICATION_ID + ".RECORD";
    static final String ACTION_SHUTDOWN =
            BuildConfig.APPLICATION_ID + ".SHUTDOWN";
    static final int VIRT_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    final private HandlerThread handlerThread =
            new HandlerThread(getClass().getSimpleName(),
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler handler;
    private MediaProjectionManager mgr;
    private WindowManager wmgr;
    private ImageTransmogrifier it;
    private int resultCode;
    private Intent resultData;
    private WindowManager.LayoutParams params;
    private boolean clicked = false;
    private String nameOfTheFile = "";
    private static Context sContext;

    public BackgroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Inflate the floating view layout we created
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        //Add the view to the window.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);

        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.LEFT;        //Initially view will be added to top-left corner
        params.x = 0;
        params.y = 100;


        Timer T=new Timer();
        T.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                startCapture(getCurrentTimeAndDate());
            }
        }, 2000, 2000);
    }

    public static String getCurrentTimeAndDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("ddMMyyyy-HHmmss");
        return df.format(c.getTime());
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        sContext = getApplicationContext();
        if (i.getAction() == null) {
            resultCode = i.getIntExtra(EXTRA_RESULT_CODE, 1337);
            resultData = i.getParcelableExtra(EXTRA_RESULT_INTENT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService();
            } else {
                foregroundify();
            }
        } else if (ACTION_RECORD.equals(i.getAction())) {
            if (resultData != null) {
            } else {
            }
        } else if (ACTION_SHUTDOWN.equals(i.getAction())) {
            stopForeground(true);
            stopSelf();
        }

        return (START_NOT_STICKY);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    WindowManager getWindowManager() {
        return (wmgr);
    }

    Handler getHandler() {
        return (handler);
    }

    private boolean processingImage = false;

    void processImage(final byte[] png, final String fileName) {
        if (processingImage) {
            return;
        }
        new Thread() {
            @Override
            public void run() {
                processingImage = true;
                String mPath = Environment.getExternalStorageDirectory() + File.separator +
                        getString(R.string.app_name);
                File folder = new File(mPath);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                File file = new File(mPath, ("Screenshot_"+fileName + ".png"));
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                }
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(png);
                    fos.flush();
                    fos.getFD().sync();
                    fos.close();

                    MediaScannerConnection.scanFile(BackgroundService.this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/png"},
                            null);
                } catch (Exception e) {
                    Log.e(getClass().getSimpleName(), "Exception writing out screenshot", e);
                }
                processingImage = false;
            }
        }.start();
        stopCapture();
    }

    private void stopCapture() {
        if (projection != null) {
            projection.stop();
            vdisplay.release();
            projection = null;
        }
    }

    private void startCapture(String fileName) {
        projection = mgr.getMediaProjection(resultCode, resultData);
        it = new ImageTransmogrifier(BackgroundService.this, fileName);
        MediaProjection.Callback cb = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                vdisplay.release();
            }
        };
        vdisplay = projection.createVirtualDisplay("ScreenShot App",
                it.getWidth(), it.getHeight(),
                getResources().getDisplayMetrics().densityDpi,
                VIRT_DISPLAY_FLAGS, it.getSurface(), null, handler);
        projection.registerCallback(cb, handler);
    }

    private void foregroundify() {
        String channelId = getPackageName();
        NotificationChannel channel;
        NotificationCompat.Builder b =
                new NotificationCompat.Builder(this, channelId);
        b.setSound(null);
        b.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);
        b.setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(getString(R.string.app_name));
        b.addAction(R.mipmap.ic_launcher,
                "Capture",
                buildPendingIntent(ACTION_RECORD));

        b.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                buildPendingIntent(ACTION_SHUTDOWN));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            CharSequence name = getString(R.string.app_name);
            String description = "Service for screenshot";
            final int importance = NotificationManager.IMPORTANCE_NONE;
            channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            // Register the channel with the system
        }

        startForeground(NOTIFY_ID, b.build());
    }

    private void startForegroundService() {
        // Create notification default intent.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        Notification.Builder notification = new Notification.Builder(this, getPackageName())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Print screen service")
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

    private PendingIntent buildPendingIntent(String action) {
        Intent i = new Intent(this, getClass());
        i.setAction(action);
        return (PendingIntent.getService(this, 0, i, 0));
    }
}

