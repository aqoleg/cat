/*
view with the map, handles touch events
 */
package space.aqoleg.cat;

import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;

public class MapView extends View implements View.OnTouchListener, BitmapCache.Callback {
    private final Path path = new Path();
    private final Paint mainPaint = new Paint();
    private final Paint secondaryPaint = new Paint();
    private final Paint textPaint = new Paint();

    private final String selectionText;
    private BitmapCache bitmapCache;
    private CurrentTrack currentTrack;
    private SavedTracks savedTrack;
    // layout constants
    private float xCenterPx;
    private float yCenterPx;
    private int xRightPx;
    private int yBottomPx;
    // map parameters
    private int mapN;
    private int z;
    private boolean isEllipsoid;
    private int tileSizePx;
    private int maxTileN;
    private double wholeSizePx; // tileSizePx * tilesN
    // center and location coordinates, from 0 to 1, in the current projection
    private Location location = new Location("");
    private double xLocation1;
    private double yLocation1;
    private double xCenter1;
    private double yCenter1;
    // touch events
    private long onTouchTimestampMs;
    private float xTouchStartPx;
    private float yTouchStartPx;
    private double xCenter1TouchStart;
    private double yCenter1TouchStart;
    // selection
    private final Location selectionLocation = new Location("");
    private boolean hasSelection;
    private double xSelection1;
    private double ySelection1;

    MapView(Context context) {
        super(context);
        selectionText = context.getString(R.string.selection);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            xCenterPx = getWidth() / 2;
            yCenterPx = getHeight() / 2;
            xRightPx = getWidth();
            yBottomPx = getHeight();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawTiles(canvas);
        drawPointers(canvas);
        if (z > 4) {
            drawCurrentPath(canvas, currentTrack.getPointsNumber());
        }
        if (z > 4) {
            drawSavedPath(canvas, savedTrack.getPointsNumber());
        }
        if (hasSelection) {
            drawSelections(canvas);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onTouchTimestampMs = System.currentTimeMillis();
                xTouchStartPx = event.getX();
                yTouchStartPx = event.getY();
                xCenter1TouchStart = xCenter1;
                yCenter1TouchStart = yCenter1;
                break;
            case MotionEvent.ACTION_MOVE:
                xCenter1 = xCenter1TouchStart + ((xTouchStartPx - event.getX()) / wholeSizePx);
                yCenter1 = yCenter1TouchStart + ((yTouchStartPx - event.getY()) / wholeSizePx);
                // normalize
                if (xCenter1 < 0) {
                    xCenter1 += 1 + (int) -xCenter1;
                } else if (xCenter1 > 1) {
                    xCenter1 -= (int) xCenter1;
                }
                if (yCenter1 < 0) {
                    yCenter1 = 0;
                } else if (yCenter1 >= 1) {
                    yCenter1 = 1;
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (System.currentTimeMillis() - onTouchTimestampMs < 300 &&
                        Math.abs(xTouchStartPx - event.getX()) < 10 && Math.abs(yTouchStartPx - event.getY()) < 10) {
                    onShortTouch();
                }
                break;
        }
        return true;
    }

    @Override
    public void onBitmapCacheLoad() {
        invalidate();
    }

    void set(
            int mapN,
            int z,
            float centerLongitude,
            float centerLatitude,
            boolean hasSelection,
            float selectedLongitude,
            float selectedLatitude
    ) {
        bitmapCache = new BitmapCache(this);
        currentTrack = CurrentTrack.getInstance();
        savedTrack = SavedTracks.getInstance();

        mainPaint.setColor(Color.rgb(0xFA, 0x05, 0x05));
        mainPaint.setStyle(Paint.Style.STROKE);
        mainPaint.setStrokeWidth(2);
        mainPaint.setAntiAlias(true);
        secondaryPaint.setColor(Color.rgb(0xEA, 0x04, 0xBC));
        secondaryPaint.setStyle(Paint.Style.STROKE);
        secondaryPaint.setStrokeWidth(2);
        secondaryPaint.setAntiAlias(true);
        textPaint.setColor(Color.rgb(0xFA, 0x05, 0x05));
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(16);

        this.mapN = mapN;
        this.z = z;
        isEllipsoid = Maps.getInstance().isEllipsoid(mapN);
        tileSizePx = Maps.getInstance().getSize(mapN);
        maxTileN = (1 << z) - 1;
        wholeSizePx = tileSizePx * (1 << z);

        xCenter1 = Projection.getX(centerLongitude);
        yCenter1 = Projection.getY(centerLatitude, isEllipsoid);

        this.hasSelection = hasSelection;
        if (hasSelection) {
            selectionLocation.setLongitude(selectedLongitude);
            selectionLocation.setLatitude(selectedLatitude);
            xSelection1 = Projection.getX(selectedLongitude);
            ySelection1 = Projection.getY(selectedLatitude, isEllipsoid);
        }

        setOnTouchListener(this);
    }

    void setLocation(float locationLongitude, float locationLatitude) {
        location.setLongitude(locationLongitude);
        location.setLatitude(locationLatitude);
        xLocation1 = Projection.getX(locationLongitude);
        yLocation1 = Projection.getY(locationLatitude, isEllipsoid);
        invalidate();
    }

    void refreshLocation(Location location) {
        if (location.distanceTo(this.location) < 20) {
            return;
        }
        this.location = new Location(location);
        xLocation1 = Projection.getX(location.getLongitude());
        yLocation1 = Projection.getY(location.getLatitude(), isEllipsoid);
        invalidate();
    }

    void refresh(boolean centerMap) {
        if (centerMap && savedTrack.getPointsNumber() > 0) {
            xCenter1 = savedTrack.getX(0);
            yCenter1 = savedTrack.getY(0, isEllipsoid);
        }
        invalidate();
    }

    void changeMap(int mapN) {
        this.mapN = mapN;
        boolean isEllipsoid = Maps.getInstance().isEllipsoid(mapN);
        if (this.isEllipsoid != isEllipsoid) {
            yLocation1 = Projection.getY(location.getLatitude(), isEllipsoid);
            yCenter1 = Projection.getY(Projection.getLatitude(yCenter1, this.isEllipsoid), isEllipsoid);
            if (hasSelection) {
                ySelection1 = Projection.getY(selectionLocation.getLatitude(), isEllipsoid);
            }
            this.isEllipsoid = isEllipsoid;
        }
        tileSizePx = Maps.getInstance().getSize(mapN);
        wholeSizePx = tileSizePx * (1 << z);
        invalidate();
    }

    void changeZoom(int z) {
        this.z = z;
        maxTileN = (1 << z) - 1;
        wholeSizePx = (1 << z) * tileSizePx;
        invalidate();
    }

    void center() {
        xCenter1 = xLocation1;
        yCenter1 = yLocation1;
        invalidate();
    }

    float getLocationLongitude() {
        return (float) location.getLongitude();
    }

    float getLocationLatitude() {
        return (float) location.getLatitude();
    }

    float getCenterLongitude() {
        return (float) Projection.getLongitude(xCenter1);
    }

    float getCenterLatitude() {
        return (float) Projection.getLatitude(yCenter1, isEllipsoid);
    }

    boolean hasSelection() {
        return hasSelection;
    }

    float getSelectedLongitude() {
        return (float) selectionLocation.getLongitude();
    }

    float getSelectedLatitude() {
        return (float) selectionLocation.getLatitude();
    }

    private void drawTiles(Canvas canvas) {
        // center tile
        int xTileN = (int) (xCenter1 * (1 << z));
        int yTileN = (int) (yCenter1 * (1 << z));
        int xLeftPx = (int) (xCenterPx - ((xCenter1 * (1 << z) - xTileN) * tileSizePx));
        int yTopPx = (int) (yCenterPx - ((yCenter1 * (1 << z) - yTileN) * tileSizePx));
        drawTile(canvas, xTileN, yTileN, xLeftPx, yTopPx);
        // clockwise from the top
        int rounds = (int) (Math.max(xCenterPx, yCenterPx) / tileSizePx) + 1;
        int step = 0;
        for (int round = 0; round < rounds; round++) {
            step += 2;
            xTileN--;
            yTileN--;
            xLeftPx -= tileSizePx;
            yTopPx -= tileSizePx;
            for (int i = 0; i < step; i++) {
                xTileN++;
                xLeftPx += tileSizePx;
                drawTile(canvas, xTileN, yTileN, xLeftPx, yTopPx);
            }
            for (int i = 0; i < step; i++) {
                yTileN++;
                yTopPx += tileSizePx;
                drawTile(canvas, xTileN, yTileN, xLeftPx, yTopPx);
            }
            for (int i = 0; i < step; i++) {
                xTileN--;
                xLeftPx -= tileSizePx;
                drawTile(canvas, xTileN, yTileN, xLeftPx, yTopPx);
            }
            for (int i = 0; i < step; i++) {
                yTileN--;
                yTopPx -= tileSizePx;
                drawTile(canvas, xTileN, yTileN, xLeftPx, yTopPx);
            }
        }
    }

    private void drawTile(Canvas canvas, int tileX, int tileY, int leftPx, int topPx) {
        if (leftPx < -tileSizePx || leftPx > xRightPx || topPx < -tileSizePx || topPx > yBottomPx) {
            return;
        }
        if (tileX < 0) {
            tileX += maxTileN + 1;
            if (tileX < 0) {
                return;
            }
        } else if (tileX > maxTileN) {
            tileX -= maxTileN + 1;
            if (tileX > maxTileN) {
                return;
            }
        }
        if (tileY < 0 || tileY > maxTileN) {
            return;
        }
        Bitmap bitmap = bitmapCache.getBitmap(mapN, z, tileY, tileX);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, leftPx, topPx, null);
        }
    }

    private void drawPointers(Canvas canvas) {
        float yPx = (float) (yCenterPx + ((yLocation1 - yCenter1) * wholeSizePx));
        if (yPx <= 0 || yPx >= yBottomPx) {
            return;
        }
        float xPx = (float) (xCenterPx + ((xLocation1 - xCenter1) * wholeSizePx));
        drawPointer(canvas, xPx, yPx);
        drawPointer(canvas, xPx + (float) wholeSizePx, yPx);
        drawPointer(canvas, xPx - (float) wholeSizePx, yPx);
    }

    private void drawPointer(Canvas canvas, float xPx, float yPx) {
        if (xPx < 0 || xPx > xRightPx) {
            return;
        }
        canvas.drawCircle(xPx, yPx, 8, mainPaint);
        canvas.drawLine(xPx, yPx + 8, xPx, yPx + 4, mainPaint);
        canvas.drawLine(xPx, yPx - 8, xPx, yPx - 4, mainPaint);
        canvas.drawLine(xPx + 8, yPx, xPx + 4, yPx, mainPaint);
        canvas.drawLine(xPx - 8, yPx, xPx - 4, yPx, mainPaint);
    }

    private void drawCurrentPath(Canvas canvas, int pointsN) {
        if (pointsN <= 0) {
            return;
        }
        double xDeltaFromCenter1 = currentTrack.getX(0) - xCenter1;
        if (xDeltaFromCenter1 > 0.5) {
            xDeltaFromCenter1 -= 1;
        } else if (xDeltaFromCenter1 < -0.5) {
            xDeltaFromCenter1 += 1;
        }
        float xPx = (float) (xCenterPx + (xDeltaFromCenter1 * wholeSizePx));
        float yPx = (float) (yCenterPx + ((currentTrack.getY(0, isEllipsoid) - yCenter1) * wholeSizePx));
        float previousXPx = xPx;
        float previousYPx = yPx;
        boolean hasStarted = false;
        if (xPx > 0 && xPx < xRightPx && yPx > 0 && yPx < yBottomPx) {
            canvas.drawCircle(xPx, yPx, 2, mainPaint);
            hasStarted = true;
            path.moveTo(xPx, yPx);
        }
        for (int i = 1; i < pointsN; i++) {
            xDeltaFromCenter1 = currentTrack.getX(i) - xCenter1;
            if (xDeltaFromCenter1 > 0.5) {
                xDeltaFromCenter1 -= 1;
            } else if (xDeltaFromCenter1 < -0.5) {
                xDeltaFromCenter1 += 1;
            }
            xPx = (float) (xCenterPx + (xDeltaFromCenter1 * wholeSizePx));
            yPx = (float) (yCenterPx + ((currentTrack.getY(i, isEllipsoid) - yCenter1) * wholeSizePx));
            if (xPx > 0 && xPx < xRightPx && yPx > 0 && yPx < yBottomPx) {
                if (!hasStarted) {
                    hasStarted = true;
                    path.moveTo(previousXPx, previousYPx);
                }
                path.lineTo(xPx, yPx);
            } else {
                if (hasStarted) {
                    hasStarted = false;
                    path.lineTo(xPx, yPx);
                }
                previousXPx = xPx;
                previousYPx = yPx;
            }
        }
        canvas.drawPath(path, mainPaint);
        path.reset();
    }

    private void drawSavedPath(Canvas canvas, int pointsN) {
        if (pointsN <= 0) {
            return;
        }
        double xDeltaFromCenter1 = savedTrack.getX(0) - xCenter1;
        if (xDeltaFromCenter1 > 0.5) {
            xDeltaFromCenter1 -= 1;
        } else if (xDeltaFromCenter1 < -0.5) {
            xDeltaFromCenter1 += 1;
        }
        float xPx = (float) (xCenterPx + (xDeltaFromCenter1 * wholeSizePx));
        float yPx = (float) (yCenterPx + ((savedTrack.getY(0, isEllipsoid) - yCenter1) * wholeSizePx));
        float previousXPx = xPx;
        float previousYPx = yPx;
        boolean hasStarted = false;
        if (xPx > 0 && xPx < xRightPx && yPx > 0 && yPx < yBottomPx) {
            canvas.drawCircle(xPx, yPx, 2, secondaryPaint);
            hasStarted = true;
            path.moveTo(xPx, yPx);
        }
        for (int i = 1; i < pointsN; i++) {
            xDeltaFromCenter1 = savedTrack.getX(i) - xCenter1;
            if (xDeltaFromCenter1 > 0.5) {
                xDeltaFromCenter1 -= 1;
            } else if (xDeltaFromCenter1 < -0.5) {
                xDeltaFromCenter1 += 1;
            }
            xPx = (float) (xCenterPx + (xDeltaFromCenter1 * wholeSizePx));
            yPx = (float) (yCenterPx + ((savedTrack.getY(i, isEllipsoid) - yCenter1) * wholeSizePx));
            if (xPx > 0 && xPx < xRightPx && yPx > 0 && yPx < yBottomPx) {
                if (!hasStarted) {
                    hasStarted = true;
                    path.moveTo(previousXPx, previousYPx);
                }
                path.lineTo(xPx, yPx);
            } else {
                if (hasStarted) {
                    hasStarted = false;
                    path.lineTo(xPx, yPx);
                }
                previousXPx = xPx;
                previousYPx = yPx;
            }
        }
        canvas.drawPath(path, secondaryPaint);
        path.reset();
    }

    private void drawSelections(Canvas canvas) {
        float yPx = (float) (yCenterPx + ((ySelection1 - yCenter1) * wholeSizePx));
        if (yPx <= 0 || yPx >= yBottomPx) {
            return;
        }
        float xPx = (float) (xCenterPx + ((xSelection1 - xCenter1) * wholeSizePx));
        boolean hasText = drawSelection(canvas, xPx, yPx, true);
        hasText |= drawSelection(canvas, xPx - (float) wholeSizePx, yPx, !hasText);
        drawSelection(canvas, xPx + (float) wholeSizePx, yPx, !hasText);
    }

    private boolean drawSelection(Canvas canvas, float xPx, float yPx, boolean withText) {
        if (xPx < 0 || xPx > xRightPx) {
            return false;
        }
        canvas.drawCircle(xPx, yPx, 8, mainPaint);
        double rad = Math.toRadians(90 - selectionLocation.bearingTo(location));
        canvas.drawLine(
                (float) (xPx + Math.cos(rad) * 8),
                (float) (yPx - Math.sin(rad) * 8),
                (float) (xPx + Math.cos(rad) * 16),
                (float) (yPx - Math.sin(rad) * 16),
                mainPaint
        );
        if (!withText) {
            return false;
        }

        float distance = location.distanceTo(selectionLocation);
        float bearing = location.bearingTo(selectionLocation);
        if (bearing < 0) {
            bearing += 360;
        }
        String text = String.format(selectionText, Math.round(bearing), distance / 1000);
        float halfTextWidth = textPaint.measureText(text) / 2;
        if (xPx < halfTextWidth + 10) {
            xPx = 10;
        } else if (xPx > xRightPx - halfTextWidth - 10) {
            xPx = xRightPx - 2 * halfTextWidth - 10;
        } else {
            xPx -= halfTextWidth;
        }
        if (yPx < yCenterPx) {
            yPx += 28;
        } else {
            yPx -= 18;
        }
        canvas.drawText(text, xPx, yPx, textPaint);
        return true;
    }

    private void onShortTouch() {
        if (hasSelection) {
            // if touch current selection, deselect and return
            float xSelectionPx = (float) (xCenterPx + ((xSelection1 - xCenter1) * wholeSizePx));
            float ySelectionPx = (float) (yCenterPx + ((ySelection1 - yCenter1) * wholeSizePx));
            if (Math.abs(yTouchStartPx - ySelectionPx) < 16) {
                if (Math.abs(xTouchStartPx - xSelectionPx) < 16 ||
                        Math.abs(xTouchStartPx - xSelectionPx - wholeSizePx) < 16 ||
                        Math.abs(xTouchStartPx - xSelectionPx + wholeSizePx) < 16) {
                    hasSelection = false;
                    invalidate();
                    return;
                }
            }
        }
        ySelection1 = yCenter1 + ((yTouchStartPx - yCenterPx) / wholeSizePx);
        if (ySelection1 < 0 || ySelection1 > 1) {
            // out of map, deselect and return
            hasSelection = false;
            invalidate();
            return;
        }
        xSelection1 = xCenter1 + ((xTouchStartPx - xCenterPx) / wholeSizePx);
        // normalize
        if (xSelection1 < 0) {
            xSelection1 += 1 + (int) -xSelection1;
        } else if (xSelection1 > 1) {
            xSelection1 -= (int) xSelection1;
        }
        // set new selection
        hasSelection = true;
        selectionLocation.setLongitude(Projection.getLongitude(xSelection1));
        selectionLocation.setLatitude(Projection.getLatitude(ySelection1, isEllipsoid));
        invalidate();
    }
}