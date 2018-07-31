package com.byteshat.printscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mPrintScreenButton;
    private Button mPrint30Button;
    private Button mStop30Button;


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPrintScreenButton = findViewById(R.id.print_screen_button);
        mPrint30Button = findViewById(R.id.print_screen_30);
        mStop30Button = findViewById(R.id.print_screen_stop);
        mPrintScreenButton.setOnClickListener(this);
        mPrint30Button.setOnClickListener(this);
        mStop30Button.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.print_screen_button:
                takeScreenshot();
                break;
            case R.id.print_screen_30:
                Intent repeatTimerIntent = new Intent(getApplicationContext(), BackgroundService.class);
                repeatTimerIntent.setAction(BackgroundService.ACTION_START_FOREGROUND_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(repeatTimerIntent);
                    registerReceiver(receiver, new IntentFilter(BackgroundService.ACTION_START_FOREGROUND_SERVICE));
                } else {
                    startService(repeatTimerIntent);
                }
                break;
            case R.id.print_screen_stop:
                Intent stopIntent = new Intent(MainActivity.this, BackgroundService.class);
                stopIntent.setAction(BackgroundService.ACTION_STOP_FOREGROUND_SERVICE);
                startService(stopIntent);
                unregisterReceiver(receiver);
                break;

        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    private void takeScreenshot() {
        Date now = new Date();
        String folder_main = "NewFolder";
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
        try {
            String mPath = Environment.getExternalStorageDirectory() + "/" + folder_main + "/" + now + ".jpg";
            String Path = Environment.getExternalStorageDirectory() + "/" + folder_main;
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);
            File folder = new File(Path);
            File imageFile = new File(mPath);
            if (!folder.exists()) {
                folder.mkdir();
            }
            if (!imageFile.exists()) {
                imageFile.createNewFile();
            }
            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
    }
}
