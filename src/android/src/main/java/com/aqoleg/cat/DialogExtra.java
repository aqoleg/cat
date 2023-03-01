/*
fragment with some extra info
 */
package com.aqoleg.cat;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.aqoleg.cat.app.App;
import com.aqoleg.cat.data.Files;

import java.util.Locale;

import static android.content.Context.LOCATION_SERVICE;

@SuppressWarnings("deprecation")
public class DialogExtra extends DialogFragment implements GpsStatus.Listener {
    private LocationManager locationManager;
    private GpsStatus gpsStatus;
    private SatellitesView satellitesView;
    private String pointLink;
    private String centerTilePath;

    static DialogExtra newInstance() {
        DialogExtra dialog = new DialogExtra();
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.dialog);
        return dialog;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            locationManager = ((LocationManager) getActivity().getSystemService(LOCATION_SERVICE));
            View view = inflater.inflate(R.layout.dialog_extra, container, false);
            satellitesView = new SatellitesView(getActivity().getApplicationContext());
            ((FrameLayout) view.findViewById(R.id.frame)).addView(satellitesView);

            String copy = getString(R.string.copy);
            String send = getString(R.string.send);
            SpannableString span = new SpannableString(String.format(getString(R.string.track), copy, send));
            span.setSpan(
                    new Clickable("copyTrack"),
                    span.length() - send.length() - 2 - copy.length(),
                    span.length() - send.length() - 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            span.setSpan(
                    new Clickable("sendTrack"),
                    span.length() - send.length(),
                    span.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            TextView textView = view.findViewById(R.id.track);
            textView.setText(span);
            textView.setMovementMethod(LinkMovementMethod.getInstance());

            if (App.hasPoint()) {
                pointLink = String.format(
                        Locale.ENGLISH,
                        "cat.aqoleg.com/app?lon=%1$.6f&lat=%2$.6f&z=%3$d&map=%4$s",
                        App.getPointLongitude(),
                        App.getPointLatitude(),
                        App.getZ() + 1,
                        App.getMapName()
                );
                span = new SpannableString(String.format(getString(R.string.point), pointLink));
                span.setSpan(
                        new Clickable("copyPoint"),
                        span.length() - pointLink.length(),
                        span.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                textView = view.findViewById(R.id.point);
                textView.setText(span);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                view.findViewById(R.id.point).setVisibility(View.GONE);
            }

            centerTilePath = App.getCenterTilePath();
            int indexOfLastSeparator = centerTilePath.lastIndexOf('/');
            span = new SpannableString(String.format(getString(R.string.tile), centerTilePath));
            span.setSpan(
                    new Clickable("openFolder"),
                    span.length() - centerTilePath.length(),
                    span.length() - centerTilePath.length() + indexOfLastSeparator,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            if (centerTilePath.indexOf('.') > indexOfLastSeparator) {
                span.setSpan(
                        new Clickable("openTile"),
                        span.length() - centerTilePath.length() + indexOfLastSeparator + 1,
                        span.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            textView = view.findViewById(R.id.tile);
            textView.setText(span);
            textView.setMovementMethod(LinkMovementMethod.getInstance());

            span = new SpannableString(String.format(getString(R.string.visit), "cat.aqoleg.com"));
            span.setSpan(new Clickable("openWeb"), span.length() - 14, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView = view.findViewById(R.id.website);
            textView.setText(span);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            return view;
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
        return null;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onStart() {
        try {
            super.onStart();
            getDialog().getWindow().setLayout(
                    (int) (192 * getActivity().getResources().getDisplayMetrics().density),
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            getDialog().getWindow().setGravity(Gravity.RIGHT | Gravity.BOTTOM);
            locationManager.addGpsStatusListener(this);
            String text = String.format(getString(R.string.cacheText), App.getTileCacheSize(), App.getTrackCacheSize());
            ((TextView) getView().findViewById(R.id.cache)).setText(text);
            App.registerForUpdate(this);
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onStop() {
        try {
            super.onStop();
            locationManager.removeGpsStatusListener(this);
            gpsStatus = null;
            App.unregisterForUpdates();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        try {
            gpsStatus = locationManager.getGpsStatus(gpsStatus);
            satellitesView.invalidate();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }


    public void updateLocation(Location location) {
        String text = String.format(
                Locale.ENGLISH,
                getString(R.string.location),
                location.getLongitude(),
                location.getLatitude(),
                Math.round(location.getAltitude()),
                Math.round(location.getAccuracy()),
                Math.round(location.getSpeed() * 3.6f)
        );
        ((TextView) getView().findViewById(R.id.location)).setText(text);
    }


    private void onSpanClick(String id) {
        switch (id) {
            case "copyTrack":
                ClipboardManager clipboard = (ClipboardManager) getActivity().getApplicationContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                String track = App.getEncodedTrack();
                String url;
                if (track == null) {
                    url = String.format(
                            Locale.ENGLISH,
                            "https://cat.aqoleg.com/app?lon=%1$.6f&lat=%2$.6f&z=%3$d",
                            App.getLocationLongitude(),
                            App.getLocationLatitude(),
                            App.getZ() + 1
                    );
                } else {
                    url = String.format(
                            Locale.ENGLISH,
                            "https://cat.aqoleg.com/app?track=%1$s&z=%2$d",
                            track,
                            App.getZ() + 1
                    );
                }
                ClipData data = ClipData.newPlainText("cat track", url);
                clipboard.setPrimaryClip(data);
                Toast.makeText(getActivity().getApplicationContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                break;
            case "sendTrack":
                track = App.getEncodedTrack();
                if (track == null) {
                    url = "https://writeme.aqoleg.com/?text=%3Ca%20href%3D%22https%3A%2F%2Fcat.aqoleg.com" +
                            "%2Fapp%3Flon%3D" +
                            String.format(Locale.ENGLISH, "%1$.6f", App.getLocationLongitude()) +
                            "%26lat%3D" +
                            String.format(Locale.ENGLISH, "%1$.6f", App.getLocationLatitude()) +
                            "%26z%3D" +
                            (App.getZ() + 1) +
                            "%22%3Epoint%3C%2Fa%3E";
                } else {
                    url = "https://writeme.aqoleg.com/?text=%3Ca%20href%3D%22https%3A%2F%2Fcat.aqoleg.com" +
                            "%2Fapp%3Ftrack%3D" +
                            track +
                            "%26z%3D" +
                            (App.getZ() + 1) +
                            "%22%3Etrack%3C%2Fa%3E";
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(
                            getActivity().getApplicationContext(),
                            R.string.noBrowser,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                break;
            case "copyPoint":
                clipboard = (ClipboardManager) getActivity().getApplicationContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                data = ClipData.newPlainText("cat point", "https://" + pointLink);
                clipboard.setPrimaryClip(data);
                Toast.makeText(getActivity().getApplicationContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                break;
            case "openFolder":
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(
                        Uri.parse(centerTilePath.substring(0, centerTilePath.lastIndexOf('/'))),
                        "resource/folder"
                );
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(
                            getActivity().getApplicationContext(),
                            R.string.noExplorer,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                break;
            case "openTile":
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(centerTilePath), "image/*");
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(
                            getActivity().getApplicationContext(),
                            R.string.noViewer,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                break;
            case "openWeb":
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://cat.aqoleg.com"));
                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(
                            getActivity().getApplicationContext(),
                            R.string.noBrowser,
                            Toast.LENGTH_SHORT
                    ).show();
                }
                break;
        }
    }


    private class SatellitesView extends View {
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
            try {
                super.onLayout(changed, left, top, right, bottom);
                if (!changed) {
                    return;
                }
                xCenterPx = getWidth() >> 1;
                yCenterPx = getHeight() >> 1;
                outerRadiusPx = yCenterPx - 10;
                middleRadiusPx = outerRadiusPx * 2 / 3;
                innerRadiusPx = outerRadiusPx / 3;
                positionScalePx = outerRadiusPx / 90;
                usedSatelliteRadiusScalePx = outerRadiusPx / 280;
                notUsedSatelliteRadiusPx = outerRadiusPx / 60;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            try {
                super.onDraw(canvas);
                canvas.drawCircle(xCenterPx, yCenterPx, outerRadiusPx, grid);
                canvas.drawCircle(xCenterPx, yCenterPx, middleRadiusPx, grid);
                canvas.drawCircle(xCenterPx, yCenterPx, innerRadiusPx, grid);
                canvas.drawLine(xCenterPx, yCenterPx - innerRadiusPx, xCenterPx, yCenterPx - outerRadiusPx, grid);
                canvas.drawLine(xCenterPx, yCenterPx + innerRadiusPx, xCenterPx, yCenterPx + outerRadiusPx, grid);
                canvas.drawLine(xCenterPx - innerRadiusPx, yCenterPx, xCenterPx - outerRadiusPx, yCenterPx, grid);
                canvas.drawLine(xCenterPx + innerRadiusPx, yCenterPx, xCenterPx + outerRadiusPx, yCenterPx, grid);

                int satellitesN = 0;
                int satellitesUsed = 0;
                if (gpsStatus != null) {
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
                }
                String text = String.format(getString(R.string.satellites), satellitesUsed, satellitesN);
                ((TextView) getView().findViewById(R.id.satellites)).setText(text);
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }

    private class Clickable extends ClickableSpan {
        private final String id;

        private Clickable(String id) {
            this.id = id;
        }

        @Override
        public void onClick(View widget) {
            try {
                onSpanClick(id);
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }
}