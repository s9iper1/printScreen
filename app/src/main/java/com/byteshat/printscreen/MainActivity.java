package com.byteshat.printscreen;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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
    private MediaProjectionManager mgr;
    private static final int REQUEST_SCREENSHOT=59706;


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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK) {
                Intent i =
                        new Intent(this, BackgroundService.class)
                                .putExtra(BackgroundService.EXTRA_RESULT_CODE, resultCode)
                                .putExtra(BackgroundService.EXTRA_RESULT_INTENT, data);

                startService(i);
            }
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.print_screen_button:
                takeScreenshot();
                break;
            case R.id.print_screen_30:
                if (isStoragePermissionGranted()) {
                    mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mgr.createScreenCaptureIntent(),
                            REQUEST_SCREENSHOT);
                }
                break;
            case R.id.print_screen_stop:
                Intent stopIntent = new Intent(MainActivity.this, BackgroundService.class);
                startService(stopIntent);
                unregisterReceiver(receiver);
                break;

        }

    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        unregisterReceiver(receiver);
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
