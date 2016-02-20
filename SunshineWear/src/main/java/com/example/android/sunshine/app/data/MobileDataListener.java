package com.example.android.sunshine.app.data;

import com.example.android.sunshine.app.Constants;
import com.example.sharedassets.SharedUtility;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Douglas on 2/19/2016.
 */
public class MobileDataListener implements DataApi.DataListener {

    public dataRetrievedFromMobileListener mDataListener;

    public MobileDataListener(dataRetrievedFromMobileListener listener){
        mDataListener = listener;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent event : dataEvents) {
            DataItem dataItem = event.getDataItem();
            if(SharedUtility.FORECAST_PATH.equals(dataItem.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                Double weatherID = dataMap.getDouble(SharedUtility.WEATHER_ID);
                String dailyHigh = String.valueOf(dataMap.getDouble(SharedUtility.HIGH_TEMP_KEY));
                String dailyLow = String.valueOf(dataMap.getDouble(SharedUtility.LOW_TEMP_KEY));
                mDataListener.updateWatchfaceUIWithData(weatherID,dailyHigh,dailyLow);
            }
        }
    }

    public interface dataRetrievedFromMobileListener {
        public void updateWatchfaceUIWithData(double weatherID,String dailyHigh, String dailyLow);
    }
}
