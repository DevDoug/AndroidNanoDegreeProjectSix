package com.example.sunshinewearapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Example shell activity which simply broadcasts to our receiver and exits.
 */
public class SunshineWearStubBroadcastActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = new Intent();
        i.setAction("com.example.sunshinewearapplication.SHOW_NOTIFICATION");
        i.putExtra(SunshineWearPostNotificationReceiver.CONTENT_KEY, getString(R.string.title));
        sendBroadcast(i);
        finish();
    }
}
