/*
one single activity, handles permissions, intents, state, lifecycle, buttons
launches fragments and service

lifecycle:
 launch - onCreate, onStart
 invisible, home, screen off - onSaveInstanceState, onStop
 visible again - onStart
 rotate - onSaveInstanceState, onStop, onDestroy, onCreate, onStart
 close - onStop, onDestroy
 */
package com.aqoleg.cat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.aqoleg.cat.app.App;
import com.aqoleg.cat.data.Files;
import com.aqoleg.cat.data.UriData;

public class ActivityMain extends Activity implements View.OnClickListener, View.OnLongClickListener {
    private static final String prefs = "prefs";
    private static final String prefsMap = "map";
    private static final String prefsZ = "zoom";
    private static final String prefsCenterLongitude = "centerLon";
    private static final String prefsCenterLatitude = "centerLat";
    private static final String prefsLocationLongitude = "locationLon";
    private static final String prefsLocationLatitude = "locationLat";

    private static final String stateOpenedTrack = "openedTrack";
    private static final String stateSelectedTracks = "selectedTracks";
    private static final String stateHasPoint = "hasPoint";
    private static final String statePointLongitude = "pointLon";
    private static final String statePointLatitude = "pointLat";
    private static final String stateTracksPosition = "tracksPosition";

    private boolean hasPermissions;
    private long lastClickTime; // debounce

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    finish();
                    return;
                }
            }
            recreate();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            // permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int permissions = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions |= checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
                permissions |= checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
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
            // get state
            SharedPreferences sharedPrefs = getSharedPreferences(prefs, MODE_PRIVATE);
            String map = sharedPrefs.getString(prefsMap, null);
            int z = sharedPrefs.getInt(prefsZ, 2);
            float centerLongitude = sharedPrefs.getFloat(prefsCenterLongitude, 0);
            float centerLatitude = sharedPrefs.getFloat(prefsCenterLatitude, 0);
            String openedTrack = null;
            String[] selectedTracks = new String[0];
            boolean hasPoint = false;
            double pointLongitude = 0, pointLatitude = 0;
            int tracksPosition = 0;
            UriData uriData = null;
            // if stop app before permission had been granted, savedInstanceState became not null and empty
            if (savedInstanceState != null && savedInstanceState.containsKey(stateSelectedTracks)) {
                openedTrack = savedInstanceState.getString(stateOpenedTrack);
                selectedTracks = savedInstanceState.getStringArray(stateSelectedTracks);
                hasPoint = savedInstanceState.getBoolean(stateHasPoint);
                pointLongitude = savedInstanceState.getDouble(statePointLongitude);
                pointLatitude = savedInstanceState.getDouble(statePointLatitude);
                tracksPosition = savedInstanceState.getInt(stateTracksPosition);
            } else if (getIntent().getData() != null) {
                uriData = UriData.parse(getIntent().getData());
            }
            // load views, app
            setContentView(R.layout.activity_main);
            ActivityView activityView = new ActivityView(this);
            ((FrameLayout) findViewById(R.id.mapView)).addView(activityView);
            findViewById(R.id.close).setOnClickListener(this);
            findViewById(R.id.localDistance).setOnClickListener(this);
            findViewById(R.id.zPlus).setOnClickListener(this);
            findViewById(R.id.zPlus).setOnLongClickListener(this);
            findViewById(R.id.zMinus).setOnClickListener(this);
            findViewById(R.id.zMinus).setOnLongClickListener(this);
            findViewById(R.id.center).setOnClickListener(this);
            findViewById(R.id.tracks).setOnClickListener(this);
            findViewById(R.id.tracks).setOnLongClickListener(this);
            findViewById(R.id.maps).setOnClickListener(this);
            findViewById(R.id.extra).setOnClickListener(this);

            App.load(
                    this,
                    activityView,
                    map,
                    z,
                    centerLongitude,
                    centerLatitude,
                    sharedPrefs.getFloat(prefsLocationLongitude, 0),
                    sharedPrefs.getFloat(prefsLocationLatitude, 0),
                    openedTrack,
                    selectedTracks,
                    hasPoint,
                    pointLongitude,
                    pointLatitude,
                    tracksPosition,
                    uriData
            );

            getApplicationContext().startService(new Intent(getApplicationContext(), ServiceMain.class));
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        try {
            super.onNewIntent(intent);
            if (intent.getData() != null) {
                App.open(UriData.parse(intent.getData()));
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onStart() {
        try {
            super.onStart();
            if (hasPermissions) {
                App.setVisible(true);
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            if (!hasPermissions) {
                return;
            }
            outState.putString(stateOpenedTrack, App.getOpenedTrack());
            outState.putStringArray(stateSelectedTracks, App.getSelectedTracks());
            outState.putBoolean(stateHasPoint, App.hasPoint());
            outState.putDouble(statePointLongitude, App.getPointLongitude());
            outState.putDouble(statePointLatitude, App.getPointLatitude());
            outState.putInt(stateTracksPosition, App.getTracksPosition());
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onStop() {
        try {
            super.onStop();
            if (!hasPermissions) {
                return;
            }
            getSharedPreferences(prefs, MODE_PRIVATE)
                    .edit()
                    .putString(prefsMap, App.getMapName())
                    .putInt(prefsZ, App.getZ())
                    .putFloat(prefsCenterLongitude, App.getCenterLongitude())
                    .putFloat(prefsCenterLatitude, App.getCenterLatitude())
                    .putFloat(prefsLocationLongitude, App.getLocationLongitude())
                    .putFloat(prefsLocationLatitude, App.getLocationLatitude())
                    .apply();
            App.setVisible(false);
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            if (hasPermissions) {
                App.unload();
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onClick(View view) {
        try {
            boolean doubleClick = (SystemClock.elapsedRealtime() - lastClickTime) < 1000;
            lastClickTime = SystemClock.elapsedRealtime();

            switch (view.getId()) {
                case R.id.close:
                    if (!doubleClick) {
                        stopService(new Intent(getApplicationContext(), ServiceMain.class));
                        finish();
                    }
                    break;
                case R.id.localDistance:
                    App.clearLocalDistance();
                    break;
                case R.id.zPlus:
                    App.changeZoom(1);
                    break;
                case R.id.zMinus:
                    App.changeZoom(-1);
                    break;
                case R.id.center:
                    App.center();
                    break;
                case R.id.maps:
                    if (!doubleClick) {
                        DialogMaps.newInstance().show(getFragmentManager(), null);
                    }
                    break;
                case R.id.tracks:
                    if (!doubleClick) {
                        DialogTracks.newInstance().show(getFragmentManager(), null);
                    }
                    break;
                case R.id.extra:
                    if (!doubleClick) {
                        DialogExtra.newInstance().show(getFragmentManager(), null);
                    }
                    break;
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        try {
            if (SystemClock.elapsedRealtime() - lastClickTime < 300) {
                return false;
            }
            lastClickTime = SystemClock.elapsedRealtime();

            switch (view.getId()) {
                case R.id.zPlus:
                    App.changeZoom(3);
                    return true;
                case R.id.zMinus:
                    App.changeZoom(-3);
                    return true;
                case R.id.tracks:
                    Toast.makeText(getApplicationContext(), R.string.searchingTracks, Toast.LENGTH_SHORT).show();
                    App.searchVisibleTracks();
                    return true;
            }
            return false;
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
        return false;
    }


    public void printDistance(float totalDistance, float localDistance) {
        String format = getString(R.string.km);
        ((TextView) findViewById(R.id.totalDistance)).setText(String.format(format, totalDistance / 1000));
        ((TextView) findViewById(R.id.localDistance)).setText(String.format(format, localDistance / 1000));
    }

    public void printZoom(int z) {
        ((TextView) findViewById(R.id.zoom)).setText("z".concat(String.valueOf(z + 1)));
    }

    public void changeTrackButtonAvailability(boolean available) {
        findViewById(R.id.tracks).setEnabled(available);
        findViewById(R.id.tracks).setAlpha(available ? 1 : 0.2f);
    }
}