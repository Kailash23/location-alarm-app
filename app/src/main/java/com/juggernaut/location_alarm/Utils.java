package com.juggernaut.location_alarm;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods used in this application.
 */

class Utils {

    private static final String KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates";

    /**
     * Returns the location object as a human readable string.
     */
    static String getLocationCoordinate(Location location) {
        return location == null ? "Unknown location" :
                "(" + location.getLatitude() + ", " + location.getLongitude() + ")";
    }

    static String getLocationTitle(Context context) {
        return context.getString(R.string.current_location);
    }

    static String getLocationName(Location location, Context context) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        String result = null;
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                // sending back first address line
                result = address.getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The Context.
     */
    static boolean requestingLocationUpdates(Context context) {

        boolean check = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false);
        Log.i(LocationUpdatesService.TAG, "Checking whether requesting updates or not - " + check);
        return  check;
    }

    /**
     * Stores the location updates state in SharedPreferences.
     *
     * @param requestingLocationUpdates The location updates state.
     */
    static void setRequestingLocationUpdates(Context context, boolean requestingLocationUpdates) {
        Log.i(LocationUpdatesService.TAG, "Stores the location updates state in SharedPreferences - " + requestingLocationUpdates );
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates)
                .apply();
    }
}
