/*
view with the map, handles touch events
 */
package com.aqoleg.cat;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import com.aqoleg.cat.app.App;
import com.aqoleg.cat.data.Files;
import com.aqoleg.cat.data.Track;

import java.util.Iterator;

public class ActivityView extends View implements View.OnTouchListener {
    private final Path path = new Path();
    private final Paint locationPaint = new Paint();
    private final Paint redTrackPaint = new Paint();
    private final Paint selectedTracksPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final String nearPointText;
    private final String farPointText;
    // layout constants
    private int xPxCenter;
    private int yPxCenter;
    private int xPxRight; // width
    private int yPxBottom; // height
    private int tileRounds; // the biggest number of tiles from the center to the edge of screen
    // map parameters, tile size 256px
    private int totalTiles;
    private int pxTotal; // totalTiles * tileSize
    // center, location, points coordinates, from 0 to 1, in the current projection
    private double xCenter;
    private double yCenter;
    private double xLocation;
    private double yLocation;
    private boolean hasPoint;
    private double xPoint;
    private double yPoint;
    private float sinPoint;
    private float cosPoint;
    private String textPoint;
    private float xPxWidthTextPoint;
    // touch events
    private boolean isMoving;
    private long touchStartTime;
    private float xPxTouchStart;
    private float yPxTouchStart;
    private double xCenterTouchStart;
    private double yCenterTouchStart;

    @SuppressWarnings("deprecation")
    ActivityView(Context context) {
        super(context);
        locationPaint.setColor(context.getResources().getColor(R.color.pointerTransparent));
        locationPaint.setAntiAlias(true);
        redTrackPaint.setStyle(Paint.Style.STROKE);
        redTrackPaint.setStrokeWidth(2);
        redTrackPaint.setColor(context.getResources().getColor(R.color.mainRed));
        redTrackPaint.setAntiAlias(true);
        selectedTracksPaint.setStyle(Paint.Style.STROKE);
        selectedTracksPaint.setStrokeWidth(2);
        selectedTracksPaint.setColor(context.getResources().getColor(R.color.tracksTransparentPurple));
        selectedTracksPaint.setAntiAlias(true);
        textPaint.setColor(context.getResources().getColor(R.color.mainRed));
        textPaint.setTextSize(16);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);
        nearPointText = context.getString(R.string.nearSelection);
        farPointText = context.getString(R.string.farSelection);
        setOnTouchListener(this);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            super.onLayout(changed, left, top, right, bottom);
            if (changed) {
                xPxCenter = getWidth() / 2;
                yPxCenter = getHeight() / 2;
                xPxRight = getWidth();
                yPxBottom = getHeight();
                tileRounds = (int) ((float) Math.max(xPxCenter, yPxCenter) / 256) + 1;
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
            drawTiles(canvas);
            drawTracks(canvas);
            drawPoint(canvas);
            drawLocation(canvas);
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        try {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartTime = SystemClock.elapsedRealtime();
                    xPxTouchStart = event.getX();
                    yPxTouchStart = event.getY();
                    xCenterTouchStart = xCenter;
                    yCenterTouchStart = yCenter;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    xCenter = xCenterTouchStart + ((double) (xPxTouchStart - event.getX()) / pxTotal);
                    if (xCenter < 0) {
                        xCenter += 1 + (int) -xCenter;
                    } else if (xCenter > 1) {
                        xCenter -= (int) xCenter;
                    }
                    yCenter = yCenterTouchStart + ((double) (yPxTouchStart - event.getY()) / pxTotal);
                    if (yCenter < 0) {
                        yCenter = 0;
                    } else if (yCenter > 1) {
                        yCenter = 1;
                    }
                    isMoving = true;
                    draw();
                    return true;
                case MotionEvent.ACTION_UP:
                    isMoving = false;
                    long timeSinceTouchStart = SystemClock.elapsedRealtime() - touchStartTime;
                    float xPxDistance = Math.abs(xPxTouchStart - event.getX());
                    float yPxDistance = Math.abs(yPxTouchStart - event.getY());
                    if (timeSinceTouchStart < 300 && xPxDistance < 10 && yPxDistance < 10) {
                        double xPoint = xCenter + (xPxTouchStart - xPxCenter) / pxTotal;
                        if (xPoint < 0) {
                            xPoint += 1 + (int) -xPoint;
                        } else if (xPoint > 1) {
                            xPoint -= (int) xPoint;
                        }
                        double yPoint = yCenter + (yPxTouchStart - yPxCenter) / pxTotal;
                        if (yPoint < 0 || yPoint > 1) {
                            hasPoint = false;
                            textPoint = null;
                            App.deselect();
                            draw();
                            return true;
                        }
                        if (hasPoint) {
                            xPxDistance = (float) Math.abs((xPoint - this.xPoint) * pxTotal);
                            yPxDistance = (float) Math.abs((yPoint - this.yPoint) * pxTotal);
                            if (xPxDistance < 32 && yPxDistance < 32) {
                                hasPoint = false;
                                textPoint = null;
                                App.deselect();
                                draw();
                                return true;
                            }
                        }
                        App.select(xPoint, yPoint);
                    }
                    draw();
                    return true;
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
        return false;
    }


    // sets zoom without drawing
    public void setZoom(int z) {
        totalTiles = 1 << z;
        pxTotal = totalTiles * 256;
    }

    // sets center coordinates in the current projection without drawing
    public void setCenter(double xCenter, double yCenter) {
        this.xCenter = xCenter;
        this.yCenter = yCenter;
    }

    // sets location coordinates in the current projection without drawing
    public void setLocation(double xLocation, double yLocation) {
        this.xLocation = xLocation;
        this.yLocation = yLocation;
    }

    // sets point coordinates in the current projection without drawing
    public void setPoint(double xPoint, double yPoint) {
        hasPoint = true;
        this.xPoint = xPoint;
        this.yPoint = yPoint;
    }

    // sets angles and distance of the point without drawing
    public void setBearingAndDistance(float bearingFromSelection, float bearingToSelection, float distance) {
        cosPoint = (float) Math.cos(Math.toRadians(90 - bearingFromSelection));
        sinPoint = (float) Math.sin(Math.toRadians(90 - bearingFromSelection));
        if (bearingToSelection < 0) {
            bearingToSelection += 360;
        }
        if (distance >= 10000) {
            textPoint = String.format(farPointText, Math.round(bearingToSelection), distance / 1000);
        } else {
            textPoint = String.format(nearPointText, Math.round(bearingToSelection), Math.round(distance));
        }
        xPxWidthTextPoint = textPaint.measureText(textPoint);
    }

    // plans to onDraw() on the main thread
    public void draw() {
        invalidate();
    }

    public double getXCenter() {
        return xCenter;
    }

    public double getYCenter() {
        return yCenter;
    }

    public int getXTileCenter() {
        int xTile = (int) (xCenter * totalTiles);
        if (xTile < 0) {
            do {
                xTile += totalTiles;
            } while (xTile < 0);
        } else if (xTile >= totalTiles) {
            do {
                xTile -= totalTiles;
            } while (xTile >= totalTiles);
        }
        return xTile;
    }

    public int getYTileCenter() {
        int yTile = (int) (yCenter * totalTiles);
        if (yTile < 0) {
            yTile = 0;
        } else if (yTile >= totalTiles) {
            yTile = totalTiles - 1;
        }
        return yTile;
    }

    // returns Boundaries of the visible part of the map in the current projection
    public Boundaries getBoundaries() {
        return new Boundaries();
    }


    private void drawTiles(Canvas canvas) {
        // center tile
        int xTile = (int) (xCenter * totalTiles);
        int yTile = (int) (yCenter * totalTiles);
        int xPxLeft = xPxCenter - (int) Math.round((xCenter * totalTiles - xTile) * 256);
        int yPxTop = yPxCenter - (int) Math.round((yCenter * totalTiles - yTile) * 256);
        drawTile(canvas, xTile, yTile, xPxLeft, yPxTop);
        // clockwise from the top
        int sideSize = 1;
        for (int round = 0; round < tileRounds; round++) {
            sideSize += 2;
            xTile--;
            yTile--;
            xPxLeft -= 256;
            yPxTop -= 256;
            for (int i = 1; i < sideSize; i++) {
                xTile++;
                xPxLeft += 256;
                drawTile(canvas, xTile, yTile, xPxLeft, yPxTop);
            }
            for (int i = 1; i < sideSize; i++) {
                yTile++;
                yPxTop += 256;
                drawTile(canvas, xTile, yTile, xPxLeft, yPxTop);
            }
            for (int i = 1; i < sideSize; i++) {
                xTile--;
                xPxLeft -= 256;
                drawTile(canvas, xTile, yTile, xPxLeft, yPxTop);
            }
            for (int i = 1; i < sideSize; i++) {
                yTile--;
                yPxTop -= 256;
                drawTile(canvas, xTile, yTile, xPxLeft, yPxTop);
            }
        }
    }

    private void drawTile(Canvas canvas, int xTile, int yTile, int xPxLeft, int yPxTop) {
        if (xPxLeft < -256 || xPxLeft > xPxRight || yPxTop < -256 || yPxTop > yPxBottom) {
            return;
        }
        if (xTile < 0) {
            do {
                xTile += totalTiles;
            } while (xTile < 0);
        } else if (xTile >= totalTiles) {
            do {
                xTile -= totalTiles;
            } while (xTile >= totalTiles);
        }
        if (yTile < 0 || yTile >= totalTiles) {
            return;
        }
        Bitmap bitmap = App.getBitmap(yTile, xTile);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, xPxLeft, yPxTop, null);
        }
    }

    private void drawTracks(Canvas canvas) {
        drawTrack(canvas, App.getOpenedTrackIterator(), false);
        Iterator<Track> iterator = App.getTrackIterator();
        long startTimestamp = SystemClock.elapsedRealtime();
        while (iterator.hasNext()) {
            drawTrack(canvas, iterator.next(), true);
            if (isMoving && SystemClock.elapsedRealtime() - startTimestamp > 5) { // keep moving the map smooth
                break;
            }
        }
        drawTrack(canvas, App.getCurrentTrackIterator(), false);
    }

    private void drawTrack(Canvas canvas, Track track, boolean isSelected) {
        if (track == null) {
            return;
        }
        double x, xDeltaFromCenter;
        float xPx, yPx, previousXPx = 0, previousYPx = 0;
        boolean isFirstPoint = true;
        boolean hasPreviousPoint = false;
        boolean isLineStarted = false;
        while (track.next()) {
            x = track.getX();
            if (x != x) { // NaN
                hasPreviousPoint = false;
                isLineStarted = false;
                continue;
            }
            xDeltaFromCenter = x - xCenter;
            if (xDeltaFromCenter > 0.5) {
                xDeltaFromCenter -= 1;
            } else if (xDeltaFromCenter < -0.5) {
                xDeltaFromCenter += 1;
            }
            xPx = (float) (xPxCenter + (xDeltaFromCenter * pxTotal));
            yPx = (float) (yPxCenter + ((track.getY() - yCenter) * pxTotal));
            if (xPx > 0 && xPx < xPxRight && yPx > 0 && yPx < yPxBottom) {
                if (isFirstPoint && !isSelected) {
                    canvas.drawCircle(xPx, yPx, 2, redTrackPaint);
                }
                if (hasPreviousPoint) {
                    if (!isLineStarted) {
                        path.moveTo(previousXPx, previousYPx);
                        isLineStarted = true;
                    }
                    path.lineTo(xPx, yPx);
                } else {
                    path.moveTo(xPx, yPx);
                    hasPreviousPoint = true;
                    isLineStarted = true;
                }
            } else {
                if (isLineStarted) {
                    path.lineTo(xPx, yPx);
                    isLineStarted = false;
                }
                previousXPx = xPx;
                previousYPx = yPx;
                hasPreviousPoint = true;
            }
            isFirstPoint = false;
        }
        canvas.drawPath(path, isSelected ? selectedTracksPaint : redTrackPaint);
        path.reset();
    }

    private void drawPoint(Canvas canvas) {
        if (!hasPoint || textPoint == null) {
            return;
        }
        float yPx = (float) (yPxCenter + ((yPoint - yCenter) * pxTotal));
        if (yPx <= 0 || yPx >= yPxBottom) {
            return;
        }
        double xDeltaFromCenter = xPoint - xCenter;
        if (xDeltaFromCenter > 0.5) {
            xDeltaFromCenter -= 1;
        } else if (xDeltaFromCenter < -0.5) {
            xDeltaFromCenter += 1;
        }
        float xPx = (float) (xPxCenter + xDeltaFromCenter * pxTotal);
        if (xPx <= 0 || xPx >= xPxRight) {
            return;
        }
        canvas.drawCircle(xPx, yPx, 8, redTrackPaint);
        canvas.drawLine(
                xPx + cosPoint * 8,
                yPx - sinPoint * 8,
                xPx + cosPoint * 16,
                yPx - sinPoint * 16,
                redTrackPaint
        );
        xPx -= xPxWidthTextPoint / 2;
        if (xPx < 10) {
            xPx = 10;
        } else if (xPx > xPxRight - xPxWidthTextPoint - 10) {
            xPx = xPxRight - xPxWidthTextPoint - 10;
        }
        if (yPx < yPxCenter) {
            yPx += 31;
        } else {
            yPx -= 18;
        }
        canvas.drawText(textPoint, xPx, yPx, textPaint);
    }

    private void drawLocation(Canvas canvas) {
        float yPx = (float) (yPxCenter + ((yLocation - yCenter) * pxTotal));
        if (yPx <= 0 || yPx >= yPxBottom) {
            return;
        }
        double xDeltaFromCenter = xLocation - xCenter;
        if (xDeltaFromCenter > 0.5) {
            xDeltaFromCenter -= 1;
        } else if (xDeltaFromCenter < -0.5) {
            xDeltaFromCenter += 1;
        }
        float xPx = (float) (xPxCenter + xDeltaFromCenter * pxTotal);
        if (xPx < 0 || xPx > xPxRight) {
            return;
        }
        canvas.drawCircle(xPx, yPx, 12, locationPaint);
        canvas.drawCircle(xPx, yPx, 8, redTrackPaint);
        canvas.drawLine(xPx, yPx + 8, xPx, yPx + 4, redTrackPaint);
        canvas.drawLine(xPx, yPx - 8, xPx, yPx - 4, redTrackPaint);
        canvas.drawLine(xPx + 8, yPx, xPx + 4, yPx, redTrackPaint);
        canvas.drawLine(xPx - 8, yPx, xPx - 4, yPx, redTrackPaint);
    }


    public class Boundaries {
        public final double xLeft; // xLeft > xRight, if includes longitude 180
        public final double xRight;
        public final double yTop; // yTop < yBottom
        public final double yBottom;

        private Boundaries() {
            if (xPxRight > pxTotal) {
                xLeft = 0;
                xRight = 1;
            } else {
                xLeft = (xCenter - (double) xPxCenter / pxTotal + 1) % 1;
                xRight = (xCenter + (double) xPxCenter / pxTotal) % 1;
            }
            yTop = Math.max(0, yCenter - (double) yPxCenter / pxTotal);
            yBottom = Math.min(1, yCenter + (double) yPxCenter / pxTotal);
        }
    }
}