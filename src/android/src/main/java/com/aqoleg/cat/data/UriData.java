/*
parses uri from intent
 opens any gpx file
 opens geo:
  geo:0,0?q=latitude,longitude(label)
  geo:0,0?q=latitude,longitude
  geo:latitude,longitude?z=zoom
  geo:latitude,longitude
 opens url http(s)://cat.aqoleg.com/app?
  adds new map:
   newMap=newMapName&newUrl=urlToDownloadNewMap&newProjection=ellipsoid&
   newMap=map&newUrl=https%3A%2F%2Fcat.aqoleg.com%2Fmaps%2Fn%2F%253%24d%2F%252%24d%2F%251%24d.png&
  selects map:
   map=mapName&
  selects zoom:
   z=2&
  displays track:
   track=xlongitudeylatitudexnextLongitudeynextLatitude&
   track=x10y10x11y11xNaNyNaNx10.55y12.56&
  selects point:
   longitude=longitude&latitude=latitude&
   lon=longitude&lat=latitude&
   lon=10&lat=12&
 */
package com.aqoleg.cat.data;

import android.net.Uri;
import com.aqoleg.cat.utils.Projection;

public class UriData {
    public final String newMapName; // map name to save or null
    public final String newMapUrl; // map url to save, null if newMapName == null
    public final String newMapProjection; // map projection to save, null if newMapName == null
    public final String mapName; // map name to open or null
    public final boolean hasZ;
    public final int z; // normalized zoom to open, if hasZ
    public final String track; // track to open: absolute track file path, encoded track or null
    public final boolean hasPoint;
    public final double pointLongitude; // normalized, if hasPoint
    public final double pointLatitude; // normalized, if hasPoint

    private UriData(
            String newMapName,
            String newMapUrl,
            String newMapProjection,
            String mapName,
            int z,
            String track,
            boolean hasPoint,
            double pointLongitude,
            double pointLatitude
    ) {
        this.newMapName = newMapName;
        this.newMapUrl = newMapUrl;
        this.newMapProjection = newMapProjection;
        this.mapName = mapName;
        hasZ = z > 0;
        if (--z > 17) {
            z = 17;
        }
        this.z = z;
        this.track = track;
        this.hasPoint = hasPoint;
        this.pointLongitude = Projection.normalizeLongitude(pointLongitude);
        this.pointLatitude = Projection.normalizeLatitude(pointLatitude);
    }


    // returns UriData or null
    public static UriData parse(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return new UriData(null, null, null, null, 0, uri.getPath(), false, 0, 0);
        } else if ("geo".equals(uri.getScheme())) {
            String geo = uri.toString();
            int startIndex = geo.indexOf("0,0?q=");
            int middleIndex, endIndex;
            if (startIndex > 0) {
                startIndex += 6;
                middleIndex = geo.indexOf(",", startIndex);
                endIndex = geo.indexOf("(", middleIndex);
            } else {
                startIndex = 4;
                middleIndex = geo.indexOf(",", 4);
                endIndex = geo.indexOf("?", startIndex);
            }
            if (endIndex < 0) {
                endIndex = geo.length();
            }
            double latitude, longitude;
            try {
                latitude = Double.parseDouble(geo.substring(startIndex, middleIndex));
                longitude = Double.parseDouble(geo.substring(middleIndex + 1, endIndex));
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                Files.getInstance().log("incorrect geo uri in intent " + uri.toString() + ": " + e);
                return null;
            }
            int z = -1;
            int zIndex = geo.indexOf("?z=");
            if (zIndex > 0) {
                try {
                    z = Integer.parseInt(geo.substring(zIndex + 3));
                } catch (Exception e) {
                    Files.getInstance().log("incorrect z in uri in intent " + uri.toString() + ": " + e);
                }
            }
            return new UriData(null, null, null, null, z, null, true, longitude, latitude);
        } else if ("cat.aqoleg.com".equals(uri.getHost())) {
            if (uri.getPath() == null || !uri.getPath().startsWith("/app")) {
                Files.getInstance().log("incorrect uri path in intent " + uri.toString());
                return null;
            }
            int z = 0;
            String s = uri.getQueryParameter("z");
            if (s != null) {
                try {
                    z = Integer.parseInt(s);
                } catch (Exception e) {
                    Files.getInstance().log("incorrect z in uri in intent " + uri.toString() + ": " + e);
                }
            }
            boolean hasSelection = false;
            double longitude = 0, latitude = 0;
            try {
                s = uri.getQueryParameter("longitude");
                if (s == null) {
                    s = uri.getQueryParameter("lon");
                }
                if (s != null) {
                    longitude = Double.parseDouble(s);
                    s = uri.getQueryParameter("latitude");
                    if (s == null) {
                        s = uri.getQueryParameter("lat");
                    }
                    if (s != null) {
                        latitude = Double.parseDouble(s);
                        hasSelection = true;
                    }
                }
            } catch (NumberFormatException e) {
                Files.getInstance().log("incorrect uri in intent " + uri.toString() + ": " + e);
            }
            return new UriData(
                    uri.getQueryParameter("newMap"),
                    uri.getQueryParameter("newUrl"),
                    uri.getQueryParameter("newProjection"),
                    uri.getQueryParameter("map"),
                    z,
                    uri.getQueryParameter("track"),
                    hasSelection,
                    longitude,
                    latitude
            );
        } else {
            Files.getInstance().log("incorrect uri in intent " + uri.toString());
            return null;
        }
    }
}