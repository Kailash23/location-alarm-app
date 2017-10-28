package com.juggernaut.location_alarm;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import static com.juggernaut.location_alarm.MapsActivity.REQUEST_LOCATION_CODE;

/**
 * Created by Kailash on 10/27/2017.
 */

public class Permissions extends AppCompatActivity{
    public static Boolean checkLocationPermission(Context context) {
        //If permission is not granted then we ask for permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            /*shouldShowRequestPermissionRationale - This method return true if app had requested permission previously and
              and user denied the request*/
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            //Return false if user has chosen don't ask again method when previously asked for permission
            return false;
        } else
            return true;
    }

}
