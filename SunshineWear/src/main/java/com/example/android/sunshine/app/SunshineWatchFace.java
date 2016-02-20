/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.example.android.sunshine.app.data.MobileDataListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private Bitmap mWeatherBitmap;
    private final int[] mClockNumbers = new int[]{12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public int mClockNumberPadding = 30;
    public int mIconScaleFactor = 6;
    public int mSunOffset = 60;
    public final String LOG_TAG = SunshineWatchFace.class.getSimpleName();
    public static float DEFAULT_LATLONG = 0F;
    public Double mWeatherID;
    public String mDailyHighTemperature;
    public String mDailyLowTemperature;
    public int mTempHighLowOffset = 50;
    public int weatherIconID = 0; //default to sun
    public int mWatchHandDarkColor;
    public int mWatchHandLightColor;
    Resources mResources;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements MobileDataListener.dataRetrievedFromMobileListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Paint mNumbersPaint;
        Paint mTemperaturePaint;
        boolean mAmbient;
        Time mTime;
        MobileDataListener mDataListener;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mResources = SunshineWatchFace.this.getResources();
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mResources.getColor(R.color.primary_sunny));

            mHandPaint = new Paint();
            //mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(mResources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mNumbersPaint = new Paint();
            mNumbersPaint.setColor(mResources.getColor(R.color.watch_number_color));
            mNumbersPaint.setStrokeWidth(mResources.getDimension(R.dimen.analog_hand_stroke));
            mNumbersPaint.setTextSize(20);

            mTemperaturePaint = new Paint();
            mTemperaturePaint.setColor(mResources.getColor(R.color.watch_number_color));
            mTemperaturePaint.setTextSize(20);

            mTime = new Time();

            mBackgroundPaint.setColor(mResources.getColor(R.color.primary_sunny));
            mWatchHandDarkColor = R.color.watch_hands_sunshine_yellow;
            mWatchHandLightColor = R.color.watch_hands_sunshine_yellow_light;
            mHandPaint.setColor(mResources.getColor(R.color.watch_number_color));
            mWeatherBitmap = BitmapFactory.decodeResource(mResources, 800); //clear icon to start

            mDataListener = new MobileDataListener(this);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mDataListener.mDataListener = null;
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    //Todo: On tap change the current day while the count is less than seven after that we go back to the first day
                    // The user has completed the tap gesture.
/*                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ? R.color.background : R.color.background2));*/
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            //move the sun more offscreen to indicate the setting sun so 80 will be noon or when the sun is fully up

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            if (mWeatherBitmap != null)
                mWeatherBitmap = Bitmap.createScaledBitmap(mWeatherBitmap, canvas.getWidth() / mIconScaleFactor, canvas.getHeight() / mIconScaleFactor, false);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 40;
            float minLength = centerX - 60;
            float hrLength = centerX - 100;

            for (int i = 0; i < mClockNumbers.length; i++) {
                float clockNumbersX = GetCos(i * 30 + 270) * (centerX - 20);
                float clockNumbersY = GetSin(i * 30 + 270) * (centerY - mClockNumberPadding);
                canvas.drawText(String.valueOf(mClockNumbers[i]), clockNumbersX + centerX, clockNumbersY + centerY, mNumbersPaint);
            }

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                mHandPaint.setShader(new LinearGradient(centerX + secX, centerY + secY, centerX, centerY, mResources.getColor(mWatchHandDarkColor), mResources.getColor(mWatchHandLightColor), Shader.TileMode.MIRROR));
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            mHandPaint.setShader(new LinearGradient(centerX + minX, centerY + minY, centerX, centerY, mResources.getColor(mWatchHandDarkColor), mResources.getColor(mWatchHandLightColor), Shader.TileMode.MIRROR));
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            mHandPaint.setShader(new LinearGradient(centerX + hrX, centerY + hrY, centerX, centerY, mResources.getColor(mWatchHandDarkColor), mResources.getColor(mWatchHandLightColor), Shader.TileMode.MIRROR));
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);

            float sunshineBitmapX = canvas.getWidth() - mSunOffset;
            float sunshineBitmapY = canvas.getHeight() - mSunOffset;

            if (mWeatherBitmap != null) {
                canvas.drawBitmap(mWeatherBitmap, sunshineBitmapX, sunshineBitmapY, null);
            }

            if (mDailyHighTemperature != null) {
                canvas.drawText(mDailyHighTemperature + (char) 0x00B0, (centerX - 25) - mTempHighLowOffset, centerY + mTempHighLowOffset, mTemperaturePaint);
                canvas.drawText(mDailyLowTemperature + (char) 0x00B0, (centerX - 25) + mTempHighLowOffset, centerY + mTempHighLowOffset, mTemperaturePaint);
            }
        }

        public float GetSin(float degAngle) {
            return (float) Math.sin(Math.PI * degAngle / 180);
        }

        public float GetCos(float degAngle) {
            return (float) Math.cos(Math.PI * degAngle / 180);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void updateWatchfaceUIWithData(double weatherID, String highTemp, String lowTemp) {
            mWeatherID = weatherID;
            mDailyHighTemperature = highTemp;
            mDailyLowTemperature = lowTemp;
            int weatherIconId = getIconResourceForWeatherCondition(mWeatherID);
            if (weatherIconId != -1)
                mWeatherBitmap = BitmapFactory.decodeResource(mResources, weatherIconId);

        }

        public int getIconResourceForWeatherCondition(double weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                setWeatherUI(Constants.WeatherTypes.Stormy);
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                setWeatherUI(Constants.WeatherTypes.Rainy);
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                setWeatherUI(Constants.WeatherTypes.Rainy);
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                setWeatherUI(Constants.WeatherTypes.Snowing);
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                setWeatherUI(Constants.WeatherTypes.Rainy);
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                setWeatherUI(Constants.WeatherTypes.Snowing);
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                setWeatherUI(Constants.WeatherTypes.Foggy);
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                setWeatherUI(Constants.WeatherTypes.Stormy);
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                setWeatherUI(Constants.WeatherTypes.Clear);
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                setWeatherUI(Constants.WeatherTypes.LightCloudy);
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                setWeatherUI(Constants.WeatherTypes.Cloudy);
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

        /* Sets the weather UI components (Ex: watchface, hands, and number colors) for each different weather types */
        public void setWeatherUI(Constants.WeatherTypes weathertype) {
            if (weathertype == Constants.WeatherTypes.Clear) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_sunny));
                mWatchHandDarkColor = R.color.watch_hands_sunshine_yellow;
                mWatchHandLightColor = R.color.watch_hands_sunshine_yellow_light;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_color));
            } else if (weathertype == Constants.WeatherTypes.Rainy) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_rainy));
                mWatchHandDarkColor = R.color.watch_hands_rain_blue;
                mWatchHandLightColor = R.color.watch_hands_rain_blue_light;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_rainy_color));
            } else if (weathertype == Constants.WeatherTypes.Stormy) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_stormy));
                mWatchHandDarkColor = R.color.watch_hands_storm_grey;
                mWatchHandLightColor = R.color.watch_hands_storm_white;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_stormy_color));
            } else if (weathertype == Constants.WeatherTypes.Cloudy) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_cloudy));
                mWatchHandDarkColor = R.color.watch_hands_cloudy_grey;
                mWatchHandLightColor = R.color.watch_hands_cloudy_white;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_cloudy_color));
            } else if (weathertype == Constants.WeatherTypes.Foggy) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_foggy));
                mWatchHandDarkColor = R.color.watch_hands_foggy_white;
                mWatchHandLightColor = R.color.watch_hands_foggy_white_light;
                mHandPaint.setColor(mResources.getColor(R.color.watch_hands_foggy_white));
            } else if (weathertype == Constants.WeatherTypes.Snowing) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_snowing));
                mWatchHandDarkColor = R.color.watch_hands_snowing_white;
                mWatchHandLightColor = R.color.watch_hands_snowing_white_light;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_snowing_color));
            } else if (weathertype == Constants.WeatherTypes.LightCloudy) {
                mBackgroundPaint.setColor(mResources.getColor(R.color.primary_cloudy));
                mWatchHandDarkColor = R.color.watch_hands_cloudy_grey;
                mWatchHandLightColor = R.color.watch_hands_cloudy_white;
                mHandPaint.setColor(mResources.getColor(R.color.watch_number_cloudy_color));
            }
        }
    }
}
