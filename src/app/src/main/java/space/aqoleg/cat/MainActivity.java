/*
one single activity, handles state of the app, permissions, buttons and starts service
 */
package space.aqoleg.cat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements View.OnClickListener, LocationListener, SavedTracks.Callback {
    private static final String preferences = "preferences";
    private static final String preferencesMapDirectoryName = "currentMapDirectoryName";
    private static final String preferencesZ = "zoomStartsFromZero";
    private static final String preferencesLocationLongitude = "lastLocationLongitude";
    private static final String preferencesLocationLatitude = "lastLocationLatitude";

    private static final String stateSavedTrackName = "trackName";
    private static final String stateCenterLongitude = "centerLongitude";
    private static final String stateCenterLatitude = "centerLatitude";
    private static final String stateHasSelection = "hasSelection";
    private static final String stateSelectionLongitude = "selectionLongitude";
    private static final String stateSelectionLatitude = "selectionLatitude";

    private boolean hasPermissions;
    private LocationManager locationManager; // null if listener has removed
    private MapView mapView; // null if listener has removed
    private int mapN;
    private int z;
    private int trackN;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                finish();
                return;
            }
        }
        recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        if (!hasPermissions) {
            return;
        }
        // map and zoom from preferences
        SharedPreferences sharedPreferences = getSharedPreferences(preferences, MODE_PRIVATE);
        String mapDirectoryName = sharedPreferences.getString(preferencesMapDirectoryName, "");
        z = sharedPreferences.getInt(preferencesZ, 5);
        // selected track and point and screen position
        String trackName = "";
        float centerLongitude, centerLatitude;
        boolean hasSelection = false;
        float selectedLongitude = 0;
        float selectedLatitude = 0;
        if (savedInstanceState != null) {
            trackName = savedInstanceState.getString(stateSavedTrackName);
            centerLongitude = savedInstanceState.getFloat(stateCenterLongitude);
            centerLatitude = savedInstanceState.getFloat(stateCenterLatitude);
            hasSelection = savedInstanceState.getBoolean(stateHasSelection);
            selectedLongitude = savedInstanceState.getFloat(stateSelectionLongitude);
            selectedLatitude = savedInstanceState.getFloat(stateSelectionLatitude);
        } else {
            float[] intentData = getDataFromIntent();
            if (intentData != null) {
                centerLongitude = intentData[0];
                centerLatitude = intentData[1];
                hasSelection = true;
                selectedLongitude = centerLongitude;
                selectedLatitude = centerLatitude;
            } else {
                Location location = CurrentTrack.getInstance().getLastLocation();
                if (location != null) {
                    centerLongitude = (float) location.getLongitude();
                    centerLatitude = (float) location.getLatitude();
                } else {
                    centerLongitude = sharedPreferences.getFloat(preferencesLocationLongitude, 0);
                    centerLatitude = sharedPreferences.getFloat(preferencesLocationLatitude, 0);
                }
            }
        }
        // load data
        mapN = Maps.getInstance().loadMaps(mapDirectoryName);
        trackN = SavedTracks.getInstance().loadTracks(trackName);
        if (trackN != -1) {
            SavedTracks.getInstance().loadTrack(trackN, false, this);
        }
        // set views
        setContentView(R.layout.activity_main);
        findViewById(R.id.localDistance).setOnClickListener(this);
        findViewById(R.id.zPlus).setOnClickListener(this);
        findViewById(R.id.zMinus).setOnClickListener(this);
        findViewById(R.id.exit).setOnClickListener(this);
        findViewById(R.id.satellites).setOnClickListener(this);
        findViewById(R.id.selectTrack).setOnClickListener(this);
        findViewById(R.id.selectMap).setOnClickListener(this);
        findViewById(R.id.center).setOnClickListener(this);
        if (CurrentTrack.getInstance().getLocalDistance() != CurrentTrack.getInstance().getTotalDistance()) {
            findViewById(R.id.totalDistance).setVisibility(View.VISIBLE);
        }
        printZoom();
        // set mapView
        mapView = new MapView(this);
        mapView.set(mapN, z, centerLongitude, centerLatitude, hasSelection, selectedLongitude, selectedLatitude);
        ((FrameLayout) findViewById(R.id.mapView)).addView(mapView);

        startService(new Intent(getApplicationContext(), MainService.class));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (hasPermissions) {
            printDistance();
            float locationLongitude, locationLatitude;
            Location location = CurrentTrack.getInstance().getLastLocation();
            if (location != null) {
                locationLongitude = (float) location.getLongitude();
                locationLatitude = (float) location.getLatitude();
            } else {
                SharedPreferences sharedPreferences = getSharedPreferences(preferences, MODE_PRIVATE);
                locationLongitude = sharedPreferences.getFloat(preferencesLocationLongitude, 0);
                locationLatitude = sharedPreferences.getFloat(preferencesLocationLatitude, 0);
            }
            mapView.setLocation(locationLongitude, locationLatitude);
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (hasPermissions) {
            locationManager.removeUpdates(this);
            locationManager = null;
            getSharedPreferences(preferences, MODE_PRIVATE)
                    .edit()
                    .putString(preferencesMapDirectoryName, Maps.getInstance().getDirectoryName(mapN))
                    .putInt(preferencesZ, z)
                    .putFloat(preferencesLocationLongitude, mapView.getLocationLongitude())
                    .putFloat(preferencesLocationLatitude, mapView.getLocationLatitude())
                    .apply();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (hasPermissions) {
            outState.putString(stateSavedTrackName, SavedTracks.getInstance().getTrackName(trackN));
            outState.putFloat(stateCenterLongitude, mapView.getCenterLongitude());
            outState.putFloat(stateCenterLatitude, mapView.getCenterLatitude());
            outState.putBoolean(stateHasSelection, mapView.hasSelection());
            outState.putFloat(stateSelectionLongitude, mapView.getSelectedLongitude());
            outState.putFloat(stateSelectionLatitude, mapView.getSelectedLatitude());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hasPermissions) {
            SavedTracks.getInstance().clear();
            mapView = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.localDistance:
                if (CurrentTrack.getInstance().getLocalDistance() != 0) {
                    findViewById(R.id.totalDistance).setVisibility(View.VISIBLE);
                }
                CurrentTrack.getInstance().resetLocalDistance();
                printDistance();
                break;
            case R.id.zPlus:
                if (z == 18) {
                    return;
                }
                z++;
                printZoom();
                mapView.changeZoom(z);
                break;
            case R.id.zMinus:
                if (z == 0) {
                    return;
                }
                z--;
                printZoom();
                mapView.changeZoom(z);
                break;
            case R.id.exit:
                stopService(new Intent(getApplicationContext(), MainService.class));
                finish();
                break;
            case R.id.satellites:
                DialogSatellites.newInstance().show(getFragmentManager(), null);
                break;
            case R.id.selectTrack:
                DialogTracks.newInstance(trackN).show(getFragmentManager(), null);
                break;
            case R.id.selectMap:
                DialogMaps.newInstance(mapN).show(getFragmentManager(), null);
                break;
            case R.id.center:
                mapView.center();
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (locationManager != null) {
            printDistance();
            mapView.refreshLocation(location);
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }

    @Override
    public void onTrackLoad(int trackN, boolean centerMap) {
        if (mapView != null) {
            this.trackN = trackN;
            mapView.refresh(centerMap);
        }
    }

    void selectMap(int mapN) {
        this.mapN = mapN;
        mapView.changeMap(mapN);
    }

    private float[] getDataFromIntent() {
        if (getIntent() == null || getIntent().getData() == null || getIntent().getData().toString() == null) {
            return null;
        }
        try {
            // geo:lat,lon or geo:lat,lon?z=zoom or geo:0,0?q=lat,lon(label)
            String path = getIntent().getData().toString();
            float latitude;
            float longitude;
            if (path.contains("0,0?q=")) {
                int index = path.indexOf(",", 6);
                latitude = Float.parseFloat(path.substring(path.indexOf("=") + 1, index));
                longitude = Float.parseFloat(path.substring(index + 1, path.indexOf("(", index)));
            } else {
                int index = path.indexOf(",");
                latitude = Float.parseFloat(path.substring(4, index));
                if (path.contains("?")) {
                    longitude = Float.parseFloat(path.substring(index + 1, path.indexOf("?")));
                    z = Integer.parseInt(path.substring(path.indexOf("?z=") + 3)) - 1;
                    if (z < 0) {
                        z = 0;
                    } else if (z > 18) {
                        z = 18;
                    }
                } else {
                    longitude = Float.parseFloat(path.substring(index + 1));
                }
            }
            if (latitude < -90) {
                latitude = -90;
            } else if (latitude > 90) {
                latitude = 90;
            }
            if (longitude < -180) {
                longitude = -180;
            } else if (longitude > 180) {
                longitude = 180;
            }
            return new float[]{longitude, latitude};
        } catch (Exception exception) {
            Data.getInstance().writeLog("can not parse geo: " + exception.toString());
            return null;
        }
    }

    private void printDistance() {
        String format = getString(R.string.distanceKm);
        float distance = CurrentTrack.getInstance().getTotalDistance() / 1000;
        ((TextView) findViewById(R.id.totalDistance)).setText(String.format(format, distance));
        distance = CurrentTrack.getInstance().getLocalDistance() / 1000;
        ((TextView) findViewById(R.id.localDistance)).setText(String.format(format, distance));
    }

    private void printZoom() {
        ((TextView) findViewById(R.id.zoom)).setText("z".concat(String.valueOf(z + 1)));
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permissions = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) +
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) +
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permissions != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        0
                );
                return;
            }
        }
        hasPermissions = true;
    }
}