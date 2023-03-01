/*
reads and writes tracks

precision - 0.000001 degree - 0.1 meters

track gpx file:
<?xml version="1.0" encoding="UTF-8"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="cat.aqoleg.com">
 <trk>
  <trkseg>                                        // open new segment when gps had been lost
   <trkpt lat="0.000001" lon="0.000001">
    <ele>0</ele>                                  // altitude in meters
    <time>2023-01-01T00:00:00Z</time>
   </trkpt>
  </trkseg>
 </trk>
</gpx>

http://www.topografix.com/GPX/1/1

url encoding:
x1.123y-90.0xNaNyNaNx0y0
 */
package com.aqoleg.cat.data;

import com.aqoleg.cat.ActivityView;
import com.aqoleg.cat.utils.Projection;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

public class Track {
    private double[] x; // NaN (x != x) for segment delimiter
    private double[] ySpherical;
    private double[] yEllipsoid;
    private int length; // increasing only after create
    private boolean iteratorEllipsoid;
    private int iteratorPos;

    private Track(int size) {
        x = new double[size];
        ySpherical = new double[size];
        yEllipsoid = new double[size];
    }


    // returns not-null saved track
    public static Track load(String trackName) {
        File file = Files.getInstance().getTrack(trackName);
        Track track = new Track((int) (file.length()) / 108); // size 91 + ele + lat + lon
        track.parse(file);
        track.trim();
        return track;
    }

    // reads and returns opened track or null
    public static Opened open(String pathOrEncoded) {
        if (pathOrEncoded == null) {
            return null;
        }
        Opened track;
        File file = new File(pathOrEncoded);
        if (file.isFile()) {
            track = new Opened(pathOrEncoded, (int) (file.length()) / 108);
            track.parse(file);
        } else {
            track = new Opened(pathOrEncoded, 20);
            track.decode();
        }
        return track.cut();
    }

    // if current track exist, returns it with added segment delimiter, or returns new created current track
    public static Current loadCurrent() {
        File file = Files.getInstance().getLastCurrentTrack();
        if (file != null) {
            Current track = new Current(file, ((int) file.length() / 108)); // size 91 + ele + lat + lon
            track.parse(file);
            track.addSegmentDelimiter();
            return track;
        }
        file = Files.getInstance().createCurrentTrack();
        Current track = new Current(file, 20);
        track.writeOpening();
        return track;
    }

    static void writeDefault(File file) {
        Current track = new Current(file, 3);
        track.writeOpening();
        track.addPoint(-105.0, -63.338986, 0, 0);
        track.addPoint(-60.0, 46.397463, 0, 0);
        track.addPoint(-22.5, -7.478673, 0, 0);
        track.addPoint(22.5, -7.478673, 0, 0);
        track.addPoint(60.0, 46.397463, 0, 0);
        track.addPoint(105.0, -63.338986, 0, 0);
        track.writeClosing();
    }


    // puts iterator in the beginning, use only one instance for drawing
    public void startPointIterator(boolean ellipsoid) {
        iteratorEllipsoid = ellipsoid;
        iteratorPos = -1;
    }

    // moves iterator to the next point, returns false if there are no points left
    public boolean next() {
        return ++iteratorPos < length;
    }

    // returns x at the current position of iterator, NaN for segment delimiter
    public double getX() {
        return x[iteratorPos];
    }

    // returns y for the projection that had been set in startPointIterator() or NaN for segment delimiter
    public double getY() {
        return iteratorEllipsoid ? yEllipsoid[iteratorPos] : ySpherical[iteratorPos];
    }

    // returns x of the the first point, possibly NaN
    public double getStartX() {
        if (length == 0) {
            return Double.NaN;
        }
        return x[0];
    }

    // returns y of the first point
    public double getStartY(boolean ellipsoid) {
        return ellipsoid ? yEllipsoid[0] : ySpherical[0];
    }

    // returns true if boundaries contain this track
    public boolean contains(ActivityView.Boundaries boundaries, boolean ellipsoid) {
        double d;
        if (boundaries.xLeft < boundaries.xRight) {
            for (int i = 0; i < length; i++) {
                d = x[i];
                if (boundaries.xLeft < d && d < boundaries.xRight) { // false for NaN
                    d = ellipsoid ? yEllipsoid[i] : ySpherical[i];
                    if (boundaries.yTop < d && d < boundaries.yBottom) {
                        return true;
                    }
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                d = x[i];
                if (boundaries.xLeft < d || d < boundaries.xRight) { // false for NaN
                    d = ellipsoid ? yEllipsoid[i] : ySpherical[i];
                    if (boundaries.yTop < d && d < boundaries.yBottom) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    @SuppressWarnings("WeakerAccess")
    protected void parse(File file) {
        BufferedReader reader = null;
        int tempIndex, startIndex;
        double latitude, longitude;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            while (line != null) {
                tempIndex = line.indexOf("<trkpt");
                // <trkpt lat="0.0" lon="0.0"   or <trkpt lon="0.0" lat="0.0"
                if (tempIndex >= 0) {
                    tempIndex += 6;
                    startIndex = line.indexOf("lat=\"", tempIndex) + 5;
                    latitude = Double.parseDouble(line.substring(startIndex, line.indexOf('"', startIndex)));
                    startIndex = line.indexOf("lon=\"", tempIndex) + 5;
                    longitude = Double.parseDouble(line.substring(startIndex, line.indexOf('"', startIndex)));
                    addPoint(
                            Projection.getX(longitude),
                            Projection.getY(latitude, false),
                            Projection.getY(latitude, true),
                            40
                    );
                } else if (line.contains("</trkseg>")) {
                    addPoint(Double.NaN, Double.NaN, Double.NaN, 40);
                }
                line = reader.readLine();
            }
        } catch (Exception e) {
            Files.getInstance().log("cannot parse track " + file.getAbsolutePath() + ": " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // returns true if it would be correct to add a segment delimiter in the end
    @SuppressWarnings("WeakerAccess")
    protected boolean endsWithPoint() {
        return length != 0 && x[length - 1] == x[length - 1];
    }


    // thread-safe for reading
    private void addPoint(double x, double ySpherical, double yEllipsoid, int sizeIncrease) {
        if (this.x.length == length) {
            this.x = Arrays.copyOf(this.x, length + sizeIncrease);
            this.ySpherical = Arrays.copyOf(this.ySpherical, length + sizeIncrease);
            this.yEllipsoid = Arrays.copyOf(this.yEllipsoid, length + sizeIncrease);
        }
        this.x[length] = x;
        this.ySpherical[length] = ySpherical;
        this.yEllipsoid[length] = yEllipsoid;
        length++;
    }

    // saves memory
    private void trim() {
        if (x.length - length > 20) { // 480 bytes
            x = Arrays.copyOf(x, length);
            ySpherical = Arrays.copyOf(ySpherical, length);
            yEllipsoid = Arrays.copyOf(yEllipsoid, length);
        }
    }


    public static class Opened extends Track {
        public final String pathOrEncoded;

        private Opened(String pathOrEncoded, int size) {
            super(size);
            this.pathOrEncoded = pathOrEncoded;
        }


        // returns x of the last point
        public double getEndX() {
            return super.x[super.length - 1];
        }

        public double getEndY(boolean ellipsoid) {
            return ellipsoid ? super.yEllipsoid[super.length - 1] : super.ySpherical[super.length - 1];
        }


        private void decode() {
            int xIndex, yIndex;
            double longitude, latitude;
            try {
                xIndex = pathOrEncoded.indexOf('x');
                while (xIndex < pathOrEncoded.length()) {
                    yIndex = pathOrEncoded.indexOf('y', ++xIndex);
                    longitude = Double.parseDouble(pathOrEncoded.substring(xIndex, yIndex));
                    longitude = Projection.normalizeLongitude(longitude);
                    xIndex = pathOrEncoded.indexOf('x', ++yIndex);
                    if (xIndex < 0) {
                        xIndex = pathOrEncoded.length();
                    }
                    latitude = Double.parseDouble(pathOrEncoded.substring(yIndex, xIndex));
                    latitude = Projection.normalizeLatitude(latitude);
                    if (longitude != longitude || latitude != latitude) {
                        super.addPoint(Double.NaN, Double.NaN, Double.NaN, 40);
                    } else {
                        super.addPoint(
                                Projection.getX(longitude),
                                Projection.getY(latitude, false),
                                Projection.getY(latitude, true),
                                40
                        );
                    }
                }
            } catch (Exception e) {
                Files.getInstance().log("cannot decode track " + pathOrEncoded + ": " + e);
            }
        }

        private Opened cut() {
            while (super.length > 0) {
                if (!Double.isNaN(super.x[super.length - 1])) {
                    break;
                }
                super.length--;
            }
            if (super.length < 3) {
                return null;
            }
            return this;
        }
    }

    // writable Track
    public static class Current extends Track {
        private static final SimpleDateFormat gpxTime; // 2023-01-20T11:28:00Z in utc
        private static final String gpxOpen = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"cat.aqoleg.com\">\n" +
                " <trk>";
        private static final String gpxTrksegOpen = "\n" +
                "  <trkseg>";
        private static final String gpxTrkpt = "\n" +
                "   <trkpt lat=\"%2$f\" lon=\"%1$f\">\n" + // 6 digits after the dot by default
                "    <ele>%3$d</ele>\n" +
                "    <time>%4$s</time>\n" +
                "   </trkpt>";
        private static final String gpxTrksegClose = "\n" +
                "  </trkseg>";
        private static final String gpxClose = "\n" +
                " </trk>\n" +
                "</gpx>";

        private final File file;


        static {
            gpxTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
            gpxTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        }


        private Current(File file, int length) {
            super(length);
            this.file = file;
        }


        // adds and writes this point in the end of the track
        public void addPoint(double longitude, double latitude, int altitudeMeters, long unixTime) {
            super.addPoint(
                    Projection.getX(longitude),
                    Projection.getY(latitude, false),
                    Projection.getY(latitude, true),
                    10
            );

            String point = String.format(
                    Locale.ENGLISH,
                    gpxTrkpt,
                    longitude,
                    latitude,
                    altitudeMeters,
                    gpxTime.format(unixTime)
            );

            Writer writer = null;
            try {
                writer = new FileWriter(file, true).append(point);
            } catch (IOException e) {
                Files.getInstance().log("cannot write track point " + file.getAbsolutePath() + ": " + e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // adds and writes segment delimiter in the end of the track, if it ends with a point
        public void addSegmentDelimiter() {
            if (!super.endsWithPoint()) {
                return;
            }
            super.addPoint(Double.NaN, Double.NaN, Double.NaN, 10);

            Writer writer = null;
            try {
                writer = new FileWriter(file, true).append(gpxTrksegClose).append(gpxTrksegOpen);
            } catch (IOException e) {
                Files.getInstance().log("cannot write track segment delimiter " + file.getAbsolutePath() + ": " + e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // deletes small current track or finishes and moves it to the saved tracks
        public void close() {
            if (super.length < 4) {
                if (!file.delete()) {
                    Files.getInstance().log("cannot delete current track " + file.getAbsolutePath());
                }
            } else {
                writeClosing();
                Files.getInstance().moveCurrentTrack(file);
            }
        }

        // returns encoded track "x1.0y1.0x2.0y2.0xNaNyNaNx4.0y4.0" or null
        public String getEncoded() {
            if (super.length < 3) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = null;
            int tempIndex, startIndex;
            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                while (line != null) {
                    tempIndex = line.indexOf("<trkpt");
                    // <trkpt lat="0.0" lon="0.0"   or <trkpt lon="0.0" lat="0.0"
                    if (tempIndex >= 0) {
                        tempIndex += 6;
                        startIndex = line.indexOf("lon=\"", tempIndex) + 5;
                        builder.append('x').append(line.substring(startIndex, line.indexOf('"', startIndex)));
                        startIndex = line.indexOf("lat=\"", tempIndex) + 5;
                        builder.append('y').append(line.substring(startIndex, line.indexOf('"', startIndex)));
                    } else if (line.contains("</trkseg>")) {
                        builder.append("xNaNyNaN");
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                Files.getInstance().log("cannot parse current track: " + e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            return builder.toString();
        }


        private void writeOpening() {
            Writer writer = null;
            try {
                writer = new FileWriter(file).append(gpxOpen).append(gpxTrksegOpen);
            } catch (IOException e) {
                Files.getInstance().log("cannot write track opening " + file.getAbsolutePath() + ": " + e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private void writeClosing() {
            Writer writer = null;
            try {
                writer = new FileWriter(file, true).append(gpxTrksegClose).append(gpxClose);
            } catch (IOException e) {
                Files.getInstance().log("cannot write track closing " + file.getAbsolutePath() + ": " + e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}