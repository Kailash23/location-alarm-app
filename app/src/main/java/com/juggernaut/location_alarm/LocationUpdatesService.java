package com.juggernaut.location_alarm;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;


/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This application show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification associated with that service is removed.
 */
public class LocationUpdatesService extends Service {

    public static final String TAG = LocationUpdatesService.class.getSimpleName();

    /**
     * trigger alarm when MAX_DISTANCE_RANGE away from destination.
     */
    public final static int MAX_DISTANCE_RANGE = 200;

    private static final String PACKAGE_NAME = "com.juggernaut.location_alarm";
    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";

    /**
     * The name of the channel for notifications.
     */
    private static final String CHANNEL_ID = "channel_01";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
            ".started_from_notification";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

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

    /**
     * Vibrate for 1000 milliseconds
     */
    private final static int DURATION_OF_VIBRATION = 1000;
    /**
     * Time period between two vibration events
     */
    private final static int VIBRATE_DELAY_TIME = 2000;

    /**
     * Increase alarm volume gradually every 600ms
     */
    private final static int VOLUME_INCREASE_DELAY = 600;

    /**
     * Volume level increasing step
     */
    private final static float VOLUME_INCREASE_STEP = 0.01f;

    /**
     * Max player volume level
     */
    private final static float MAX_VOLUME = 1.0f;

    /**
     * MediaPlayer class can be used to control playback of audio
     */
    private static MediaPlayer mPlayer;

    /**
     * A Handler allows you to send and process Message and Runnable objects associated with a
     * thread's MessageQueue.
     * Here used to schedule alarm volume increase gradually and vibrate periodically.
     */
    private static Handler mHandler = new Handler();

    /**
     * Tracks whether an alarm is ringing or not.
     */
    private static boolean isAlarmRinging = false;

    /**
     * When creating a service that provides binding, you must provide an IBinder that provides the
     * programming interface that clients can use to interact with the service.
     */
    private final IBinder mBinder = new LocalBinder();

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */

    private NotificationManager mNotificationManager;

    /**
     * LocationRequest objects are used to request a quality of service for location updates from the
     * FusedLocationProviderApi.
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
     * thread's message queue.
     */
    private Handler mServiceHandler;

    /**
     * The current location.
     */
    private Location mLocation;

    /**
     * Initial volume level is 0.
     */
    private float mVolumeLevel = 0;

    /**
     * operates the vibrator on the device.
     */
    private Vibrator mVibrator;

    /**
     * When an object implementing interface Runnable is used to create a thread, starting the thread
     * causes the object's run method to be called in that separately executing thread.
     */
    private Runnable mVibrationRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Vibrating!");

            mVibrator.vibrate(DURATION_OF_VIBRATION);
            // Provide loop for vibration
            mHandler.postDelayed(mVibrationRunnable,
                    DURATION_OF_VIBRATION + VIBRATE_DELAY_TIME);
        }
    };

    private Runnable mVolumeRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Volume increasing!");

            // increase volume level until reach max value
            if (mPlayer != null && mVolumeLevel < MAX_VOLUME) {
                mVolumeLevel += VOLUME_INCREASE_STEP;
                mPlayer.setVolume(mVolumeLevel, mVolumeLevel);
                // set next increase in 500ms
                mHandler.postDelayed(mVolumeRunnable, VOLUME_INCREASE_DELAY);
            }
        }
    };

    /**
     * Interface definition of a callback to be invoked when there has been an error during an
     * asynchronous operation.
     */
    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.i(TAG, "MediaPlayer error!");

            stopAlarm();
            LocationUpdatesService.this.stopSelf();
            return true;
        }
    };

    /**
     * Service class constructor..
     */
    public LocationUpdatesService() {
    }

    /**
     * Called by the system when the service is first created.
     */
    @Override
    public void onCreate() {
        Log.i(TAG, "<onCreate>");

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        /*
          Used for receiving notifications from the FusedLocationProviderApi when the device location
          has changed or can no longer be determined.

          The methods are called if the LocationCallback has been registered with the location client
          using the requestLocationUpdates(LocationRequest, LocationCallback, Looper) method.
         */
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Log.i(TAG, "Location update (LocationCallback)");
                super.onLocationResult(locationResult);
                /*
                  Using the Google Play services location APIs, your app can request the last known location
                  of the user's device. In most cases, you are interested in the user's current location,
                  which is usually equivalent to the last known location of the device.
                 */
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN);
            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    /**
     * Called by the system every time a client explicitly starts the service by calling
     * Context.startService(Intent), providing the arguments it supplied and a unique integer
     * token representing the start request.
     *
     * @param intent  The Intent supplied to Context.startService(Intent)
     * @param flags   Additional data about this start request.
     * @param startId A unique integer representing this specific request to start. Use with
     *                stopSelfResult(int).
     * @return The return value indicates what semantics the system should use for the service's
     * current started state.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started (onStartCommand)");
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false);

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates();
            MapsActivity.mMap.clear();
            stopSelf();
            stopAlarm();
        }
        // Tells the system not to try to re-create the service after it has been killed.
        return START_NOT_STICKY;
    }

    /**
     * Use to stop the alarm.
     */
    static void stopAlarm() {
        Log.i(TAG, "Media player stopped (stopAlarm)");

        isAlarmRinging = false;
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     * The service should clean up any resources it holds (threads, registered receivers, etc) at
     * this point.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "<onDestroy>");
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    /**
     * To provide binding for a service, you must implement the onBind() callback method.
     * This method returns an IBinder object that defines the programming interface that
     * clients can use to interact with the service.
     *
     * @param intent The Intent that was used to bind to this service as given to Context.
     * @return Return the communication channel to the service. May return null if clients can not
     * bind to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "(onBind())");
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.

        stopForeground(true);
        return mBinder;
    }

    /**
     * Called when all clients have disconnected from a particular interface published by the service.
     * The default implementation does nothing and returns false.
     *
     * @param intent The Intent that was used to bind to this service.
     * @return Return true if you would like to have the service's onRebind(Intent) method later
     * called when new clients bind to it.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "(onUnbind())");
        // Last client unbound from service
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.

        if (Utils.requestingLocationUpdates(this)) {
            /*
              Makes service run in the foreground, supplying the ongoing notification to be
              shown to the user while in this state.

              id - The identifier for this notification as per NotificationManager.notify(int, Notification);
              must not be 0.

              notification - Notification: The Notification to be displayed.
             */
            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    /**
     * Called when new clients have connected to the service, after it had previously been notified
     * that all had disconnected in its onUnbind(Intent). This will only be called if the implementation
     * of onUnbind(Intent) was overridden to return true.
     *
     * @param intent The Intent that was used to bind to this service
     */
    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "(onRebind())");

        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.

        stopForeground(true);

        // Remove this service from foreground state, allowing it to be killed if more memory is needed.
        // removeNotification - boolean: If true, the STOP_FOREGROUND_REMOVE flag will be supplied.
        super.onRebind(intent);
    }

    /**
     * Operations to do on getting a new location
     */
    private void onNewLocation(Location location) {
        Log.i(TAG, "New location : " + location);

        mLocation = location;
        float[] results = new float[3];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), MapsActivity.getLatitude(), MapsActivity.getLongitude(), results);
        Log.i(TAG, "Distance: " + String.valueOf(results[0]));
        if (results[0] < MAX_DISTANCE_RANGE) {
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
            Log.i(TAG, "(onNewLocation) : Notification content updated.");
            mNotificationManager.notify(NOTIFICATION_ID, getNotification());
        }
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        Log.i(TAG, "(createLocationRequest) - Setting location request parameter.");

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Use to start the alarm.
     */
    private void startAlarm() {
        Log.i(TAG, "startAlarm");

        mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(mErrorListener);

        try {
            isAlarmRinging = true;
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            mHandler.post(mVibrationRunnable);
            mHandler.post(mVolumeRunnable);
            String ringtone;
            ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString();
            mPlayer.setDataSource(this, Uri.parse(ringtone));
            mPlayer.setLooping(true);
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mPlayer.setVolume(mVolumeLevel, mVolumeLevel);
            mPlayer.prepare();
            mPlayer.start();
            removeLocationUpdates();
            LocationUpdatesService.this.stopSelf();
            mPlayer.setVolume(MAX_VOLUME, MAX_VOLUME);

            Notification notification = new Notification.Builder(getApplicationContext())
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
                            R.mipmap.ic_launcher))
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
                        Log.i(TAG, "Service running in foreground");
                        return true;
                    }
                }
            }
        }
        Log.i(TAG, "Service not running in foreground");
        return false;
    }

    /**
     * Returns the NotificationCompat used as part of the foreground service.
     */
    private Notification getNotification() {
        Log.i(TAG, "Notification build");
        Intent intent = new Intent(this, LocationUpdatesService.class);

        CharSequence text = Utils.getLocationCoordinate(mLocation);

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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),
                        R.mipmap.ic_launcher))
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                        servicePendingIntent)
                .setContentTitle(Utils.getLocationTitle(this))
                .setContentText(Utils.getLocationName(mLocation, this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setWhen(System.currentTimeMillis());

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }

        return builder.build();
    }

    /**
     * Removes location updates.
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "(removeLocationUpdates) - Removing location updates");

        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, true);
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }

    /**
     * Makes a request for location updates.
     */
    public void requestLocationUpdates() {
        Log.i(TAG, "(requestLocationUpdates) - Requesting location updates");

        Utils.setRequestingLocationUpdates(this, true);
        // Start a service by calling startService(), which allows the service to run indefinitely.
        // When the service has been started, the system does not destroy the service when all clients unbind.
        startService(new Intent(getApplicationContext(), LocationUpdatesService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Utils.setRequestingLocationUpdates(this, false);
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    class LocalBinder extends Binder {
        LocationUpdatesService getService() {
            Log.i(TAG, "<getService>");
            return LocationUpdatesService.this;
        }
    }

}





