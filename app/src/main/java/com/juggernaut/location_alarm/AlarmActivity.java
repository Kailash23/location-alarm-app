package com.juggernaut.location_alarm;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class AlarmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        this.setFinishOnTouchOutside(false);
        View dismissButton = findViewById(R.id.dismiss_btn);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(AlarmActivity.this, LocationUpdatesService.class);
                stopService(stopIntent);
                LocationUpdatesService.stopAlarm();
                MapsActivity.mMap.clear();
                Intent locationAlarmIntent = new Intent(AlarmActivity.this, MapsActivity.class);
                locationAlarmIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(locationAlarmIntent);
                finish();
            }
        });
    }
}
