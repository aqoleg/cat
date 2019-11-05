/*
fragment with detailed information about satellites and location
 */
package space.aqoleg.cat;

import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.*;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import static android.content.Context.LOCATION_SERVICE;

@SuppressWarnings("deprecation")
public class DialogSatellites extends DialogFragment implements LocationListener, GpsStatus.Listener {
    private LocationManager locationManager; // null if listeners has removed
    private GpsStatus gpsStatus;

    private SatellitesView satellitesView;

    static DialogSatellites newInstance() {
        DialogSatellites dialog = new DialogSatellites();
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_satellites, container, false);
        satellitesView = new SatellitesView(getActivity().getApplicationContext());
        ((FrameLayout) view.findViewById(R.id.frame)).addView(satellitesView);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        locationManager = ((LocationManager) getActivity().getSystemService(LOCATION_SERVICE));
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locationManager.addGpsStatusListener(this);
    }

    @Override
    public void onStop() {
        locationManager.removeUpdates(this);
        locationManager.removeGpsStatusListener(this);
        locationManager = null;
        super.onStop();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (locationManager == null) {
            return;
        }
        String text = String.format(
                getString(R.string.locationText),
                location.getLongitude(),
                location.getLatitude(),
                Math.round(location.getAltitude()),
                Math.round(location.getAccuracy()),
                Math.round(location.getSpeed() * 3.6f)
        );
        ((TextView) getView().findViewById(R.id.coordinates)).setText(text);
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

    @Override
    public void onGpsStatusChanged(int event) {
        if (locationManager == null) {
            return;
        }
        gpsStatus = locationManager.getGpsStatus(gpsStatus);
        satellitesView.invalidate();
    }

    class SatellitesView extends View {
        private final Paint grid;
        private final Paint usedSatellite;
        private final Paint notUsedSatellite;

        private int xCenterPx;
        private int yCenterPx;
        private float outerRadiusPx;
        private float middleRadiusPx;
        private float innerRadiusPx;
        private float positionScalePx;
        private float usedSatelliteRadiusScalePx;
        private float notUsedSatelliteRadiusPx;

        SatellitesView(Context context) {
            super(context);

            grid = new Paint();
            grid.setStyle(Paint.Style.STROKE);
            grid.setStrokeWidth(1);
            grid.setColor(Color.BLACK);
            grid.setAntiAlias(true);

            usedSatellite = new Paint();
            usedSatellite.setStyle(Paint.Style.FILL_AND_STROKE);
            usedSatellite.setColor(Color.GREEN);
            usedSatellite.setAntiAlias(true);

            notUsedSatellite = new Paint();
            notUsedSatellite.setStyle(Paint.Style.FILL_AND_STROKE);
            notUsedSatellite.setColor(Color.RED);
            notUsedSatellite.setAntiAlias(true);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (!changed) {
                return;
            }
            xCenterPx = getWidth() >> 1;
            yCenterPx = getHeight() >> 1;
            outerRadiusPx = (getHeight() / 2) - 10;
            middleRadiusPx = outerRadiusPx * 2 / 3;
            innerRadiusPx = outerRadiusPx / 3;
            positionScalePx = outerRadiusPx / 90;
            usedSatelliteRadiusScalePx = outerRadiusPx / 280;
            notUsedSatelliteRadiusPx = outerRadiusPx / 60;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawCircle(xCenterPx, yCenterPx, outerRadiusPx, grid);
            canvas.drawCircle(xCenterPx, yCenterPx, middleRadiusPx, grid);
            canvas.drawCircle(xCenterPx, yCenterPx, innerRadiusPx, grid);
            canvas.drawLine(xCenterPx, yCenterPx - innerRadiusPx, xCenterPx, yCenterPx - outerRadiusPx, grid);
            canvas.drawLine(xCenterPx, yCenterPx + innerRadiusPx, xCenterPx, yCenterPx + outerRadiusPx, grid);
            canvas.drawLine(xCenterPx - innerRadiusPx, yCenterPx, xCenterPx - outerRadiusPx, yCenterPx, grid);
            canvas.drawLine(xCenterPx + innerRadiusPx, yCenterPx, xCenterPx + outerRadiusPx, yCenterPx, grid);

            if (gpsStatus != null) {
                int satellitesN = 0;
                int satellitesUsed = 0;
                Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
                for (GpsSatellite satellite : satellites) {
                    satellitesN++;
                    float radian = (-satellite.getAzimuth() + 90) * (float) Math.PI / 180;
                    float fromCenterPx = (-satellite.getElevation() + 90) * positionScalePx;
                    float xPx = xCenterPx + (float) Math.cos(radian) * fromCenterPx;
                    float yPx = yCenterPx - (float) Math.sin(radian) * fromCenterPx;
                    if (satellite.usedInFix()) {
                        satellitesUsed++;
                        canvas.drawCircle(xPx, yPx, satellite.getSnr() * usedSatelliteRadiusScalePx, usedSatellite);
                    } else {
                        canvas.drawCircle(xPx, yPx, notUsedSatelliteRadiusPx, notUsedSatellite);
                    }
                }
                String text = String.format(getString(R.string.satellitesText), satellitesN, satellitesUsed);
                ((TextView) getView().findViewById(R.id.satellites)).setText(text);
            }
        }
    }
}