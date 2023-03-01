/*
the app main center, one single instance

tile's parameters:
z - zoom from 0 to 17, displayed zoom = (z + 1)
x, y - tiles number, tile(0, 0) is the top left tile, from 0 to (2^z - 1)
 */
package com.aqoleg.cat.app;

import android.graphics.Bitmap;
import android.location.Location;
import com.aqoleg.cat.ActivityMain;
import com.aqoleg.cat.ActivityView;
import com.aqoleg.cat.DialogExtra;
import com.aqoleg.cat.data.Files;
import com.aqoleg.cat.data.Map;
import com.aqoleg.cat.data.Track;
import com.aqoleg.cat.data.UriData;
import com.aqoleg.cat.utils.Projection;

import java.util.ArrayList;
import java.util.Iterator;

public class App {
    private static Location lastLocation; // non-filtered last update or null at start
    // service
    private static long lastUpdateTime; // unix timestamp
    private static Location lastTrackLocation;
    private static Location bestTrackLocation;
    private static Track.Current currentTrack;
    private static float totalDistance;
    private static float localDistance;
    // activity
    private static ActivityMain activityMain;
    private static ActivityView activityView;
    private static Map map;
    private static int z;
    private static Tiles tiles;
    private static Location viewLocation;
    private static Track.Opened openedTrack; // null if there is no opened track
    private static Tracks selectedTracks;
    private static Location pointLocation; // null if there is no point
    private static boolean isVisible;
    // tracks
    private static int tracksPosition;
    // extra
    private static DialogExtra dialogExtra;

    // service

    // call once at start service
    public static void beginTrackLog() {
        lastUpdateTime = 0;
        lastTrackLocation = null;
        bestTrackLocation = null;
        currentTrack = Track.loadCurrent();
        totalDistance = 0;
        localDistance = 0;
    }

    // call at each location update (1 Hz average)
    public static void updateLocation(Location location) {
        lastLocation = location;
        boolean toRefresh = false;
        // current track
        if (lastUpdateTime == 0) {
            lastUpdateTime = location.getTime();
        }
        if (bestTrackLocation == null) {
            bestTrackLocation = location;
        }
        int timeSincePreviousUpdate = (int) (location.getTime() - lastUpdateTime);
        if (timeSincePreviousUpdate > 60000) {
            lastUpdateTime = location.getTime();
            bestTrackLocation = location;
            currentTrack.addSegmentDelimiter();
        } else if (timeSincePreviousUpdate > 8000) {
            if (location.getAccuracy() < bestTrackLocation.getAccuracy()) {
                bestTrackLocation = location;
            }
            float distance = lastTrackLocation == null ? 0 : lastTrackLocation.distanceTo(bestTrackLocation);
            if (lastTrackLocation != null && distance < 50) {
                lastUpdateTime = location.getTime() - 4000;
                bestTrackLocation = location;
            } else if (lastTrackLocation == null || distance > bestTrackLocation.getAccuracy() * 4) {
                lastUpdateTime = bestTrackLocation.getTime();
                currentTrack.addPoint(
                        bestTrackLocation.getLongitude(),
                        bestTrackLocation.getLatitude(),
                        (int) bestTrackLocation.getAltitude(),
                        bestTrackLocation.getTime()
                );
                lastTrackLocation = bestTrackLocation;
                bestTrackLocation = location;
                if (distance > 0) {
                    totalDistance += distance;
                    localDistance += distance;
                    if (isVisible) {
                        activityMain.printDistance(totalDistance, localDistance);
                    }
                }
                toRefresh = true;
            }
        } else if (timeSincePreviousUpdate > 4000 && location.getAccuracy() < bestTrackLocation.getAccuracy()) {
            bestTrackLocation = location;
        }
        // activity
        if (isVisible) {
            if (lastLocation.distanceTo(viewLocation) > 20) {
                viewLocation = lastLocation;
                activityView.setLocation(
                        Projection.getX(viewLocation.getLongitude()),
                        Projection.getY(viewLocation.getLatitude(), map.ellipsoid)
                );
                if (pointLocation != null) {
                    activityView.setBearingAndDistance(
                            pointLocation.bearingTo(viewLocation),
                            viewLocation.bearingTo(pointLocation),
                            viewLocation.distanceTo(pointLocation));
                }
                toRefresh = true;
            }
        }

        if (toRefresh && isVisible) {
            activityView.draw();
        }
        if (dialogExtra != null) {
            dialogExtra.updateLocation(location);
        }
    }

    // call once at destroy service
    public static void endTrackLog() {
        lastUpdateTime = 0;
        lastTrackLocation = null;
        bestTrackLocation = null;
        if (currentTrack != null) {
            currentTrack.close();
        }
        currentTrack = null;
        totalDistance = 0;
        localDistance = 0;
    }

    // activityMain

    // call start() after this
    public static void load(
            ActivityMain activityMain,
            ActivityView activityView,
            String mapName, // can be null
            int z,
            double centerLongitude,
            double centerLatitude,
            float locationLongitude,
            float locationLatitude,
            String openedTrack, // can be null
            String[] selectedTracks,
            boolean hasPoint,
            double pointLongitude,
            double pointLatitude,
            int tracksPosition,
            UriData uriData // can be null
    ) {
        if (lastLocation == null) {
            lastLocation = new Location("");
            lastLocation.setLongitude(locationLongitude);
            lastLocation.setLatitude(locationLatitude);
        }
        App.activityMain = activityMain;
        App.activityView = activityView;

        if (uriData != null) {
            if (uriData.newMapName != null) {
                String newMapName = Map.save(uriData.newMapName, uriData.newMapUrl, uriData.newMapProjection);
                if (newMapName != null) {
                    mapName = newMapName;
                }
            } else if (uriData.mapName != null) {
                mapName = uriData.mapName;
            }
            if (uriData.hasZ) {
                z = uriData.z;
            }
            if (uriData.track != null) {
                openedTrack = uriData.track;
            }
            if (uriData.hasPoint) {
                hasPoint = true;
                pointLongitude = uriData.pointLongitude;
                pointLatitude = uriData.pointLatitude;
                centerLongitude = (float) pointLongitude;
                centerLatitude = (float) pointLatitude;
            }
        }

        map = Map.load(mapName);
        App.z = z;
        tiles = new Tiles(activityMain.getWindowManager().getDefaultDisplay());
        App.openedTrack = Track.open(openedTrack);
        App.selectedTracks = Tracks.load(selectedTracks);
        if (hasPoint) {
            pointLocation = new Location("");
            pointLocation.setLongitude(pointLongitude);
            pointLocation.setLatitude(pointLatitude);
        } else {
            pointLocation = null;
        }
        App.tracksPosition = tracksPosition;
        isVisible = false;

        activityMain.printZoom(z);
        activityView.setZoom(z);
        if (uriData != null && uriData.track != null && !uriData.hasPoint && App.openedTrack != null) {
            activityView.setCenter(App.openedTrack.getEndX(), App.openedTrack.getEndY(map.ellipsoid));
        } else {
            activityView.setCenter(Projection.getX(centerLongitude), Projection.getY(centerLatitude, map.ellipsoid));
        }
        if (pointLocation != null) {
            activityView.setPoint(
                    Projection.getX(pointLocation.getLongitude()),
                    Projection.getY(pointLocation.getLatitude(), map.ellipsoid)
            );
        }
    }

    public static void open(UriData uriData) {
        if (uriData == null) {
            return;
        }
        String mapName = null;
        if (uriData.newMapName != null) {
            mapName = Map.save(uriData.newMapName, uriData.newMapUrl, uriData.newMapProjection);
        } else if (uriData.mapName != null) {
            mapName = uriData.mapName;
        }
        boolean prevEllipsoid = map.ellipsoid;
        if (mapName != null) {
            map = Map.load(mapName);
        }
        if (uriData.hasZ) {
            z = uriData.z;
            activityMain.printZoom(z);
            activityView.setZoom(z);
        }
        if (uriData.track != null) {
            openedTrack = Track.open(uriData.track);
            if (openedTrack != null && !uriData.hasPoint) {
                activityView.setCenter(openedTrack.getEndX(), openedTrack.getEndY(map.ellipsoid));
            }
        }
        if (uriData.hasPoint) {
            pointLocation = new Location("");
            pointLocation.setLongitude(uriData.pointLongitude);
            pointLocation.setLatitude(uriData.pointLatitude);
            double x = Projection.getX(uriData.pointLongitude);
            double y = Projection.getY(uriData.pointLatitude, map.ellipsoid);
            activityView.setCenter(x, y);
            activityView.setPoint(x, y);
        }
        if (map.ellipsoid != prevEllipsoid) {
            activityView.setLocation(
                    Projection.getX(viewLocation.getLongitude()),
                    Projection.getY(viewLocation.getLatitude(), map.ellipsoid)
            );
            if ((uriData.track == null || openedTrack == null) && !uriData.hasPoint) {
                activityView.setCenter(
                        activityView.getXCenter(),
                        Projection.getY(Projection.getLatitude(activityView.getYCenter(), prevEllipsoid), map.ellipsoid)
                );
            }
        }
    }

    // start/stop refresh with last location
    public static void setVisible(boolean isVisible) {
        if (isVisible) {
            activityMain.printDistance(totalDistance, localDistance);
            viewLocation = new Location(lastLocation);
            activityView.setLocation(
                    Projection.getX(viewLocation.getLongitude()),
                    Projection.getY(viewLocation.getLatitude(), map.ellipsoid)
            );
            if (pointLocation != null) {
                activityView.setBearingAndDistance(
                        pointLocation.bearingTo(viewLocation),
                        viewLocation.bearingTo(pointLocation),
                        viewLocation.distanceTo(pointLocation)
                );
            }
            activityView.draw();
        }
        App.isVisible = isVisible;
    }

    // clear resources
    public static void unload() {
        activityMain = null;
        activityView = null;
        map = null;
        tiles.unload();
        tiles = null;
        viewLocation = null;
        openedTrack = null;
        selectedTracks.unload();
        selectedTracks = null;
        pointLocation = null;
        isVisible = false;
    }

    public static void clearLocalDistance() {
        localDistance = 0;
        activityMain.printDistance(totalDistance, localDistance);
    }

    public static void changeZoom(int delta) {
        int newZ = z + delta;
        if (newZ > 17) {
            newZ = 17;
        } else if (newZ < 0) {
            newZ = 0;
        }
        z = newZ;
        activityMain.printZoom(z);
        activityView.setZoom(z);
        activityView.draw();
    }

    public static void center() {
        viewLocation = new Location(lastLocation);
        double x = Projection.getX(viewLocation.getLongitude());
        double y = Projection.getY(viewLocation.getLatitude(), map.ellipsoid);
        activityView.setCenter(x, y);
        activityView.setLocation(x, y);
        if (pointLocation != null) {
            activityView.setBearingAndDistance(
                    pointLocation.bearingTo(viewLocation),
                    viewLocation.bearingTo(pointLocation),
                    viewLocation.distanceTo(pointLocation)
            );
        }
        activityView.draw();
    }

    public static void searchVisibleTracks() {
        activityMain.changeTrackButtonAvailability(false);
        selectedTracks.searchVisible(null, -1, activityView.getBoundaries(), map.ellipsoid);
    }

    public static String getMapName() {
        return map.name;
    }

    public static int getZ() {
        return z;
    }

    public static float getCenterLongitude() {
        return (float) Projection.getLongitude(activityView.getXCenter());
    }

    public static float getCenterLatitude() {
        return (float) Projection.getLatitude(activityView.getYCenter(), map.ellipsoid);
    }

    public static float getLocationLongitude() {
        return (float) lastLocation.getLongitude();
    }

    public static float getLocationLatitude() {
        return (float) lastLocation.getLatitude();
    }

    public static String getOpenedTrack() {
        return openedTrack == null ? null : openedTrack.pathOrEncoded;
    }

    public static String[] getSelectedTracks() {
        return selectedTracks.getTrackNames();
    }

    public static boolean hasPoint() {
        return pointLocation != null;
    }

    public static double getPointLongitude() {
        return pointLocation == null ? 0 : pointLocation.getLongitude();
    }

    public static double getPointLatitude() {
        return pointLocation == null ? 0 : pointLocation.getLatitude();
    }

    public static int getTracksPosition() {
        return tracksPosition;
    }

    // activityView

    // remove selection without drawing
    public static void deselect() {
        pointLocation = null;
    }

    // set selection without drawing
    public static void select(double xSelection, double ySelection) {
        pointLocation = new Location("");
        pointLocation.setLongitude(Projection.getLongitude(xSelection));
        pointLocation.setLatitude(Projection.getLatitude(ySelection, map.ellipsoid));
        activityView.setPoint(xSelection, ySelection);
        activityView.setBearingAndDistance(
                pointLocation.bearingTo(viewLocation),
                viewLocation.bearingTo(pointLocation),
                viewLocation.distanceTo(pointLocation)
        );
    }

    public static Bitmap getBitmap(int y, int x) {
        return tiles.getBitmap(map, z, y, x);
    }

    public static Track getOpenedTrackIterator() {
        if (openedTrack != null) {
            openedTrack.startPointIterator(map.ellipsoid);
        }
        return openedTrack;
    }

    public static Iterator<Track> getTrackIterator() {
        return selectedTracks.getTrackIterator(map.ellipsoid);
    }

    public static Track getCurrentTrackIterator() {
        if (currentTrack != null) {
            currentTrack.startPointIterator(map.ellipsoid);
        }
        return currentTrack;
    }

    // dialogMaps

    public static void selectMap(String mapName) {
        boolean prevEllipsoid = map.ellipsoid;
        map = Map.load(mapName);
        if (map.ellipsoid != prevEllipsoid) {
            activityView.setLocation(
                    Projection.getX(viewLocation.getLongitude()),
                    Projection.getY(viewLocation.getLatitude(), map.ellipsoid)
            );
            activityView.setCenter(
                    activityView.getXCenter(),
                    Projection.getY(Projection.getLatitude(activityView.getYCenter(), prevEllipsoid), map.ellipsoid)
            );
            if (pointLocation != null) {
                activityView.setPoint(
                        Projection.getX(pointLocation.getLongitude()),
                        Projection.getY(pointLocation.getLatitude(), map.ellipsoid)
                );
            }
        }
        activityView.draw();
    }

    // dialogTracks

    public static void setTracksPosition(int position) {
        tracksPosition = position;
    }

    public static int getNumberOfSelectedTracks() {
        return selectedTracks.getSelectedCount();
    }

    public static boolean isTrackSelected(String trackName) {
        return selectedTracks.isSelected(trackName);
    }

    public static void changeTrackVisibility(String trackName) {
        selectedTracks.changeVisibility(trackName);
    }

    public static void deleteSelectedTracks() {
        selectedTracks.deleteSelected();
    }

    public static void deselectAllTracks() {
        selectedTracks.deselectAll();
    }

    public static void searchUpVisibleTracks(ArrayList<String> tracks, int stopPos) {
        activityMain.changeTrackButtonAvailability(false);
        selectedTracks.searchVisible(tracks, stopPos, activityView.getBoundaries(), map.ellipsoid);
    }

    // dialogExtra

    public static void registerForUpdate(DialogExtra dialogExtra) {
        App.dialogExtra = dialogExtra;
    }

    public static void unregisterForUpdates() {
        dialogExtra = null;
    }

    // returns encoded current track or null
    public static String getEncodedTrack() {
        return currentTrack.getEncoded();
    }

    public static int getTileCacheSize() {
        return tiles.cacheSize;
    }

    public static int getTrackCacheSize() {
        return selectedTracks.getCacheSize();
    }

    public static String getCenterTilePath() {
        return Files.getInstance().getTilePathOrName(
                map.name,
                z,
                activityView.getYTileCenter(),
                activityView.getXTileCenter()
        );
    }

    // loader callbacks

    static void refresh() {
        if (activityView != null && isVisible) {
            activityView.draw();
        }
    }

    static void finishLoadingTracks() {
        if (activityMain != null && activityView != null) {
            activityMain.changeTrackButtonAvailability(true);
            if (isVisible) {
                activityView.draw();
            }
        }
    }

    static void centerOnTrack(Track track) {
        if (activityView != null) {
            double x = track.getStartX();
            if (x == x) {
                activityView.setCenter(x, track.getStartY(map.ellipsoid));
            }
            if (isVisible) {
                activityView.draw();
            }
        }
    }
}