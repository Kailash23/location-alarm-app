package com.juggernaut.location_alarm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;

import java.text.DateFormat;
import java.util.Date;

/**
 * Created by Kailash on 10/31/2017.
 */

public class Utils {
    /**
     * Returns the {@code location} object as a human readable string.
     *
     * @param location The {@link Location}.
     */
    static String getLocationText(Location location) {
        return location == null ? "Unknown location" :
                "(" + location.getLatitude() + ", " + location.getLongitude() + ")";
    }

    @SuppressLint("StringFormatInvalid")
    static String getLocationTitle(Context context) {
        return context.getString(R.string.location_updated,
                DateFormat.getDateTimeInstance().format(new Date()));
    }
}
