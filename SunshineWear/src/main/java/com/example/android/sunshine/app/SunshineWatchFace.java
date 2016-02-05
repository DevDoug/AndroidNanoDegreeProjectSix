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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

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

    private Bitmap mSunshineBitmap;
    private static final int[] mClockNumbers = new int[]{1,2,3,4,5,6,7,8,9,10,11,12};
    private Bitmap[] mWeatherIcons;
    public int mClockNumberPadding = 30;
    public int mIconScaleFactor = 6;
    public boolean mInit = true;

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

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Paint mNumbersPaint;
        boolean mAmbient;
        Time mTime;
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

            Resources resources = SunshineWatchFace.this.getResources();

            mSunshineBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary));

            mHandPaint = new Paint();
            mHandPaint.setShader(new LinearGradient(0, 0, 0, 200, resources.getColor(R.color.sunshine_yellow), resources.getColor(R.color.sunshine_yellow_light), Shader.TileMode.MIRROR));
            //mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mNumbersPaint = new Paint();
            mNumbersPaint.setColor(resources.getColor(R.color.watch_number_color));
            mNumbersPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mNumbersPaint.setTextSize(16);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
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

                    //Todo: On tap change the watch background and icons too match a different kind of weather
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

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            if(mInit){ //if they are just going to the watch face grab our icons if extended this could be where you have different weather icon packs
                mWeatherIcons = new Bitmap[]{
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_clear), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_fog), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_light_clouds), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_light_rain), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_rain), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_snow), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_storm), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_clear), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_fog), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                        Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.art_light_clouds), canvas.getWidth()/mIconScaleFactor, canvas.getHeight()/mIconScaleFactor, false),
                };
                mInit = false;
            }

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;
            float clockNumbersStartY = centerY - secLength;
            //y = GetSin( i * deg + 90) * FaceRadius;

            for(int i = 0; i < mClockNumbers.length; i++){
                float clockNumbersX = GetCos( i * 30 + 90) * (centerY - mClockNumberPadding);
                float clockNumbersY = GetSin(i * 30 + 90) * (centerY - mClockNumberPadding);
                canvas.drawText(String.valueOf(mClockNumbers[i]),clockNumbersX + centerX,clockNumbersY + centerY,mNumbersPaint);
            }

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX , centerY + secY, mHandPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);

            float hrBitmapX = (float) Math.sin(hrRot) * hrLength;
            float hrBitmapY = (float) -Math.cos(hrRot) * hrLength;

            //hrBitmapX = hrBitmapX + mSunshineBitmap.getWidth()/2;//((hrBitmapX > centerX) ? mSunshineBitmap.getWidth()/2 : mSunshineBitmap.getWidth()/2);
            hrBitmapY = hrY +  ((centerY + hrY > centerY) ? mSunshineBitmap.getHeight()/2: -mSunshineBitmap.getHeight()/2); // if the hour is positive then
            canvas.drawBitmap(mWeatherIcons[0], centerX + hrX, centerY + hrY, null); //draw the sunshine icon as the arrow pointer
        }

        public float GetSin(float degAngle){
            return (float) Math.sin(Math.PI * degAngle / 180);
        }

        public float GetCos(float degAngle){
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
    }
}
