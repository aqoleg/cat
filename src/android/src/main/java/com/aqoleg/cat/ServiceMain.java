/*
single foreground service
*/
package com.aqoleg.cat;

import android.app.*;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import com.aqoleg.cat.app.App;
import com.aqoleg.cat.data.Files;

public class ServiceMain extends Service implements LocationListener {
    private LocationManager locationManager;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        try {
            super.onCreate();
            Intent startActivityIntent = new Intent(getApplicationContext(), ActivityMain.class);
            Notification.Builder builder = new Notification.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.notification)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.icon))
                    .setContentTitle(getString(R.string.serviceDescription))
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, startActivityIntent, 0));
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel("cat", "cat", NotificationManager.IMPORTANCE_LOW);
                getApplicationContext().getSystemService(NotificationManager.class).createNotificationChannel(channel);
                builder.setChannelId("cat");
            }
            startForeground(1, builder.getNotification());

            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            // loses satellites if delay > 0
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

            App.beginTrackLog();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            super.onDestroy();
            locationManager.removeUpdates(this);
            App.endTrackLog();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        try {
            App.updateLocation(location);
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}