/*
current track singleton
filters and keeps points of the current track, writes it in .gpx file, calculates total distance

gpx file:
<?xml version="1.0" encoding="UTF-8"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="aqoleg.space">
   <trk>
      <trkseg>   // at least one
         <trkpt lat="0" lon="0">   // decimal degrees, latitude from -90 till 90, longitude from -180 till 180
            <ele>0</ele>   // elevation (in meters), optional, default is 0
            <time>2009-10-17T18:37:26Z</time>   // utc date YYYY-MM-DD, T if one line, time hh:mm:ss, Z zero meridian
         </trkpt>
      </trkseg>
   </trk>
</gpx>
 */
package space.aqoleg.cat;

import android.location.Location;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

class CurrentTrack {
    private static final SimpleDateFormat fileName; // 2019-09-06T11-28-00.gpx in local time zone
    private static final SimpleDateFormat gpxTime; // 2019-09-06T11:28:00Z in utc
    private static final String gpxOpen; // <?xml ... <trkseg>\n
    private static final String gpxPoint; // <trkpt ... </trkpt>\n
    private static final String gpxClose; // </trkseg> ... </gpx>

    private static final CurrentTrack track = new CurrentTrack();

    private final ArrayList<Double> xList = new ArrayList<>();
    private final ArrayList<Double> ySphericalList = new ArrayList<>();
    private final ArrayList<Double> yEllipsoidList = new ArrayList<>();
    private File file;
    private Location previousLocation;
    private float totalDistance;
    private float localDistance; // can be reset

    static {
        fileName = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'.gpx'", Locale.getDefault());
        gpxTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        gpxTime.setTimeZone(TimeZone.getTimeZone("UTC"));
        gpxOpen = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                System.getProperty("line.separator") +
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"aqoleg.space\">" +
                System.getProperty("line.separator") +
                " <trk>" + System.getProperty("line.separator") +
                "  <trkseg>" + System.getProperty("line.separator");
        gpxPoint = "   <trkpt lat=\"%2$f\" lon=\"%1$f\">%n" +
                "    <ele>%3$d</ele>%n" +
                "    <time>%4$s</time>%n" +
                "   </trkpt>%n";
        gpxClose = "  </trkseg>" + System.getProperty("line.separator") +
                " </trk>" + System.getProperty("line.separator") +
                "</gpx>";
    }

    static CurrentTrack getInstance() {
        return track;
    }

    // clears all data
    void open() {
        xList.clear();
        ySphericalList.clear();
        yEllipsoidList.clear();
        file = null;
        previousLocation = null;
        totalDistance = 0;
        localDistance = 0;
    }

    // deletes track if it is too short or closes it, and clears data
    void close() {
        if (file != null) {
            if (totalDistance > 200 && file.length() > 480) { // more then 3 points and 200 m
                try {
                    FileWriter writer = new FileWriter(file, true);
                    writer.append(gpxClose);
                    writer.flush();
                    writer.close();
                } catch (Exception exception) {
                    Data.getInstance().writeLog("can not close track: " + exception.toString());
                }
            } else if (!file.delete()) {
                Data.getInstance().writeLog("can not delete short track");
            }
        }
        open();
    }

    // this track will not have add to saved track list
    String getName() {
        if (file == null) {
            return null;
        }
        return file.getName();
    }

    // returns last written location or null
    Location getLastLocation() {
        return previousLocation;
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

    float getTotalDistance() {
        return totalDistance;
    }

    float getLocalDistance() {
        return localDistance;
    }

    void resetLocalDistance() {
        localDistance = 0;
    }

    void setNewLocation(Location location) {
        if (previousLocation == null) {
            // first point
            previousLocation = location;
        } else {
            float distance = previousLocation.distanceTo(location);
            if (distance > 50 && distance > location.getAccuracy() * 4) {
                totalDistance += distance;
                localDistance += distance;
                previousLocation = location;
            } else {
                return;
            }
        }
        double longitude = location.getLongitude();
        double latitude = location.getLatitude();

        xList.add(Projection.getX(longitude));
        ySphericalList.add(Projection.getY(latitude, false));
        yEllipsoidList.add(Projection.getY(latitude, true));
        // decimal separator is a dot, float is rounded by 6 digits after dot
        String point = String.format(
                Locale.ENGLISH,
                gpxPoint,
                longitude,
                latitude,
                Math.round(location.getAltitude()),
                gpxTime.format(new Date())
        );

        boolean firstPoint = file == null;
        if (firstPoint) {
            file = new File(Data.getInstance().getTracks(), fileName.format(new Date()));
        }
        try {
            FileWriter writer = new FileWriter(file, true);
            if (firstPoint) {
                writer.append(gpxOpen);
            }
            writer.append(point);
            writer.flush();
            writer.close();
        } catch (Exception exception) {
            Data.getInstance().writeLog("can not write track: " + exception.toString());
        }
    }
}