package com.example.android.sunshine.app;

/**
 * Created by Douglas on 2/10/2016.
 */
public class Constants {

    public enum WeatherTypes{
        Clear(0),
        Rainy(1),
        Stormy(2),
        Cloudy(3),
        Foggy(4),
        Snowing(5),
        LightCloudy(6);

        public int mCode;

        private WeatherTypes(int code){mCode = code;}
        public int getCode() {
            return mCode;
        }

    }
}
