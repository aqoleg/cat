/*
single foreground service
opens new track and writes filtered track at starts and closes it at destroy
*/
package space.aqoleg.cat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

@SuppressWarnings("deprecation")
public class MainService extends Service implements LocationListener {
    private LocationManager locationManager; // null if listeners has removed
    private CurrentTrack track;
    private long previousLocationTime;
    private Location bestLocation;
    private float bestLocationAccuracy;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // delay 0 for not to loose the satellites
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        Intent startActivityIntent = new Intent(getApplicationContext(), MainActivity.class);
        Notification.Builder builder = new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.notification)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.icon))
                .setContentTitle(getString(R.string.serviceDescription))
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, startActivityIntent, 0));
        startForeground(1, builder.getNotification());

        track = CurrentTrack.getInstance();
        track.open();
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
        super.onDestroy();
        locationManager.removeUpdates(this);
        locationManager = null;
        track.close();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (locationManager == null) {
            return;
        }
        if (previousLocationTime == 0) {
            // first location
            previousLocationTime = location.getTime() - 4000; // no delay
            bestLocation = location;
            bestLocationAccuracy = location.getAccuracy();
        } else {
            long timeSincePreviousLocation = location.getTime() - previousLocationTime;
            if (timeSincePreviousLocation > 10000) {
                // time is out, use the best location
                if (bestLocation == null) {
                    bestLocation = location;
                }
                track.setNewLocation(bestLocation);
                // start searching the next best location
                previousLocationTime = bestLocation.getTime();
                bestLocation = null;
            } else if (timeSincePreviousLocation > 4000) {
                // searching the new best location after delay
                if (bestLocation == null || location.getAccuracy() < bestLocationAccuracy) {
                    // this location is the best
                    bestLocation = location;
                    bestLocationAccuracy = location.getAccuracy();
                }
            }
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