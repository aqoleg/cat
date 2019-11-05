/*
saved tracks singleton
creates list of tracks, searches current track by its name on the app start,
asynchronous parses .gpx file and keeps points of the track
 */
package space.aqoleg.cat;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

class SavedTracks {
    private static final SavedTracks savedTracks = new SavedTracks();

    private final ArrayList<String> nameList = new ArrayList<>();
    private ArrayList<Double> xList = new ArrayList<>();
    private ArrayList<Double> ySphericalList = new ArrayList<>();
    private ArrayList<Double> yEllipsoidList = new ArrayList<>();
    private Loader loader;

    private SavedTracks() {
    }

    static SavedTracks getInstance() {
        return savedTracks;
    }

    // returns number of the track with this searchingTrackName or -1
    int loadTracks(String searchingTrackName) {
        clear();
        String currentTrackName = CurrentTrack.getInstance().getName();
        String[] list = Data.getInstance().getTracks().list();
        Arrays.sort(list);
        int trackN = -1;
        int i = 0;
        for (String string : list) {
            if (!string.equals(currentTrackName)) { // can be null
                if (string.equals(searchingTrackName)) {
                    trackN = i;
                }
                nameList.add(string);
                i++;
            }
        }
        return trackN;
    }

    void clear() {
        nameList.clear();
        xList.clear();
        ySphericalList.clear();
        yEllipsoidList.clear();
        if (loader != null) {
            loader.cancel(true);
            loader = null;
        }
    }

    String getTrackName(int trackN) {
        return trackN == -1 ? "" : nameList.get(trackN);
    }

    // for fragment's adapter
    ArrayList<String> getList() {
        return nameList;
    }

    int getPointsNumber() {
        return xList.size();
    }

    double getX(int pointN) {
        return xList.get(pointN);
    }

    double getY(int pointN, boolean isEllipsoid) {
        if (isEllipsoid) {
            return yEllipsoidList.get(pointN);
        } else {
            return ySphericalList.get(pointN);
        }
    }

    void loadTrack(int trackN, boolean centerMap, Callback callback) {
        if (loader != null) {
            loader.cancel(true);
            loader = null;
        }
        if (trackN == -1) {
            xList.clear();
            ySphericalList.clear();
            yEllipsoidList.clear();
            callback.onTrackLoad(-1, false);
        } else {
            loader = new Loader(trackN, centerMap, callback);
            loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    interface Callback {
        void onTrackLoad(int trackN, boolean centerMap);
    }

    class Loader extends AsyncTask<Void, Void, Void> {
        private final int trackN;
        private final boolean centerMap;
        private final Callback callback;
        private final ArrayList<Double> loaderXList = new ArrayList<>();
        private final ArrayList<Double> loaderYSphericalList = new ArrayList<>();
        private final ArrayList<Double> loaderYEllipsoidList = new ArrayList<>();

        Loader(int trackN, boolean centerMap, Callback callback) {
            this.trackN = trackN;
            this.centerMap = centerMap;
            this.callback = callback;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                File file = new File(Data.getInstance().getTracks(), nameList.get(trackN));
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                String trackPoint = null; // the whole string <trkpt lat="0" lon="0">
                String line;
                int startIndex, stopIndex;
                double longitude, latitude;
                do {
                    line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (trackPoint == null) {
                        if (line.contains("<trkpt")) {
                            trackPoint = line;
                        } else {
                            continue;
                        }
                    } else {
                        trackPoint = trackPoint.concat(line);
                    }
                    if (!line.contains(">")) {
                        continue;
                    }

                    startIndex = trackPoint.indexOf("lat");
                    startIndex = trackPoint.indexOf('"', startIndex) + 1;
                    stopIndex = trackPoint.indexOf('"', startIndex);
                    latitude = Double.valueOf(trackPoint.substring(startIndex, stopIndex));

                    startIndex = trackPoint.indexOf("lon");
                    startIndex = trackPoint.indexOf('"', startIndex) + 1;
                    stopIndex = trackPoint.indexOf('"', startIndex);
                    longitude = Double.valueOf(trackPoint.substring(startIndex, stopIndex));

                    trackPoint = null;
                    loaderYSphericalList.add(Projection.getY(latitude, false));
                    loaderYEllipsoidList.add(Projection.getY(latitude, true));
                    loaderXList.add(Projection.getX(longitude));
                } while (true);
                reader.close();
            } catch (Exception exception) {
                Data.getInstance().writeLog("can not read track " + exception.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            loader = null;
            if (loaderXList.size() > 0) {
                xList = loaderXList;
                ySphericalList = loaderYSphericalList;
                yEllipsoidList = loaderYEllipsoidList;
                callback.onTrackLoad(trackN, centerMap);
            } else {
                xList.clear();
                ySphericalList.clear();
                yEllipsoidList.clear();
                callback.onTrackLoad(trackN, false);
            }
        }
    }
}