package com.juggernaut.location_alarm;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


/**
 * Created by Kailash on 10/30/2017.
 */

public class LocationService extends Service {

    /**
     * The name of the channel for notifications.
     */
    private static final String CHANNEL_ID = "channel_01";

    public final static int MAX_DISTANCE_RANGE = 200;   // trigger alarm when 200m away from destination

    private static final String PACKAGE_NAME = "com.juggernaut.location_alarm";

    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";

    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";

    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
            ".started_from_notification";

    private static final String TAG = LocationService.class.getSimpleName();

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 30000;

    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value.
     */
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private static final int NOTIFICATION_ID = 12345678;

    private final static int DURATION_OF_VIBRATION = 1000;

    private final static int VIBRATE_DELAY_TIME = 2000;

    private final static int VOLUME_INCREASE_DELAY = 600;

    /**
     * Volume level increasing step
     */
    private final static float VOLUME_INCREASE_STEP = 0.01f;

    /**
     * Max player volume level
     */
    private final static float MAX_VOLUME = 1.0f;

    private static MediaPlayer mPlayer;

    private static Handler mHandler = new Handler();

    private static boolean isAlarmRinging = false;

    /**
     * When creating a service that provides binding, you must provide an IBinder
     * that provides the programming interface that clients can use to interact with the service.
     */
    private final IBinder mBinder = new LocalBinder();

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private boolean mChangingConfiguration = false;

    private NotificationManager mNotificationManager;

    /**
     * Contains parameters used by FusedLocationProviderApi.
     */
    private LocationRequest mLocationRequest;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Callback for changes in location.
     */
    private LocationCallback mLocationCallback;

    /**
     * A Handler allows you to send and process Message and Runnable objects associated with a
     * thread's MessageQueue. Each Handler instance is associated with a single thread and that
     * thread's message queue
     */
    private Handler mServiceHandler;

    /**
     * The current location.
     */
    private Location mLocation;

    private float mVolumeLevel = 0;

    private Vibrator mVibrator;

    private Runnable mVibrationRunnable = new Runnable() {
        @Override
        public void run() {
            mVibrator.vibrate(DURATION_OF_VIBRATION);
            // Provide loop for vibration
            mHandler.postDelayed(mVibrationRunnable,
                    DURATION_OF_VIBRATION + VIBRATE_DELAY_TIME);
        }
    };

    private Runnable mVolumeRunnable = new Runnable() {
        @Override
        public void run() {
            // increase volume level until reach max value
            if (mPlayer != null && mVolumeLevel < MAX_VOLUME) {
                mVolumeLevel += VOLUME_INCREASE_STEP;
                mPlayer.setVolume(mVolumeLevel, mVolumeLevel);
                // set next increase in 600ms
                mHandler.postDelayed(mVolumeRunnable, VOLUME_INCREASE_DELAY);
            }
        }
    };

    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            stopPlayer();
            LocationService.this.stopSelf();
            return true;
        }
    };

    public LocationService() {
    }

    static void stopPlayer() {
        isAlarmRinging = false;
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onCreate() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();
        getLastLocation();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();

        mServiceHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.ECLAIR)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false);

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates();
            MapsActivity.mMap.clear();
            stopSelf();
            stopPlayer();
        }
        // Tells the system not to try to re-create the service after it has been killed.
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.ECLAIR)
    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @RequiresApi(api = Build.VERSION_CODES.ECLAIR)
    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration) {
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Makes a request for location updates.
     */
    public void requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates");

        startService(new Intent(getApplicationContext(), LocationService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    /**
     * Removes location updates.
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            stopSelf();
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private Notification getNotification() {
        Intent intent = new Intent(this, LocationService.class);

        CharSequence text = Utils.getLocationText(mLocation);

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        Intent resumeIntent = new Intent(this, MapsActivity.class);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }

        return builder.build();
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void onNewLocation(Location location) {
        Log.i(TAG, "New location: " + location);
        mLocation = location;
        float[] results = new float[3];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), MapsActivity.getLatitude(), MapsActivity.getLongitude(), results);
        if (results[0] < MAX_DISTANCE_RANGE) {
            Toast.makeText(getApplicationContext(), "Destination Reached", Toast.LENGTH_SHORT).show();
            if (!isAlarmRinging) {
                startAlarm();
                Intent intent = new Intent(this, AlarmActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        // Notify anyone listening for broadcasts about the new location.
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(NOTIFICATION_ID, getNotification());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void startAlarm() {
        mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(mErrorListener);

        try {
            isAlarmRinging = true;
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            mHandler.post(mVibrationRunnable);
            String ringtone;
            ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString();
            mPlayer.setDataSource(this, Uri.parse(ringtone));
            mPlayer.setLooping(true);
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mPlayer.setVolume(mVolumeLevel, mVolumeLevel);
            mPlayer.prepare();
            mPlayer.start();
            removeLocationUpdates();
            LocationService.this.stopSelf();
            mPlayer.setVolume(MAX_VOLUME, MAX_VOLUME);

            Notification notification = new Notification.Builder(getApplicationContext())
                    .setContentTitle("Location Reached")
                    .setContentText("You reached Destination.")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .build();
            NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            if (manager != null) {
                manager.notify(0, notification);
            }
        } catch (Exception e) {
            isAlarmRinging = false;
            if (mPlayer.isPlaying()) {
                mPlayer.stop();
            }
            stopSelf();
        }
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Returns true if this is a foreground service.
     */
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                    Integer.MAX_VALUE)) {
                if (getClass().getName().equals(service.service.getClassName())) {
                    if (service.foreground) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

}





