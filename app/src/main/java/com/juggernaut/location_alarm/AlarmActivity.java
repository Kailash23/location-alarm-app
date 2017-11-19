package com.juggernaut.location_alarm;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmActivity extends AppCompatActivity {
    private View dismissButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        dismissButton = findViewById(R.id.dismiss_btn);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(AlarmActivity.this, LocationService.class);
                stopService(stopIntent);
                LocationService.stopPlayer();
                MapsActivity.mMap.clear();
                Intent locationAlarmIntent = new Intent(AlarmActivity.this, MapsActivity.class);
                locationAlarmIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(locationAlarmIntent);
                finish();
            }
        });
    }
}
