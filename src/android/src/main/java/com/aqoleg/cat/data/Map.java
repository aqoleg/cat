/*
creates and reads maps

properties.txt json file:
{
   "url": "https://example/x=%1$d/y=%2$d/z=%3$d",   // optional, if this is not specified, cannot be downloaded
   "projection": "ellipsoid"                        // optional, if this is not specified, use spherical
}

https://json.org
 */
package com.aqoleg.cat.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.IllegalFormatException;

public class Map {
    private static final String jsonUrl = "url";
    private static final String jsonProjection = "projection";
    private static final String jsonProjectionEllipsoid = "ellipsoid";

    public final String name;
    public final boolean ellipsoid;
    private final String url; // can be null

    private Map(String name, String url, boolean ellipsoid) {
        this.name = name;
        this.url = url;
        this.ellipsoid = ellipsoid;
    }


    // creates all default maps
    static void addDefault() {
        new Map(
                "mende",
                "http://cat.aqoleg.com/maps/mende/%3$d/%2$d/%1$d.jpeg",
                false
        ).save();
        new Map(
                "osm",
                "http://a.tile.openstreetmap.org/%3$d/%1$d/%2$d.png",
                false
        ).save();
        new Map(
                "otm",
                "https://a.tile.opentopomap.org/%3$d/%1$d/%2$d.png",
                false
        ).save();
        new Map(
                "topo",
                "https://maps.marshruty.ru/ml.ashx?al=1&x=%1$d&y=%2$d&z=%3$d",
                false
        ).save();
        new Map(
                "gsat",
                "https://khms0.googleapis.com/kh?v=937&hl=en&x=%1$d&y=%2$d&z=%3$d",
                false
        ).save();
        new Map(
                "gmap",
                "http://mt0.google.com/vt/lyrs=m&hl=en&x=%1$d&y=%2$d&z=%3$d",
                false
        ).save();
        new Map(
                "yasat",
                "https://sat01.maps.yandex.net/tiles?l=sat&x=%1$d&y=%2$d&z=%3$d&g=Gagarin",
                true
        ).save();
        new Map(
                "arcsat",
                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%3$d/%2$d/%1$d",
                false
        ).save();
        new Map(
                "arctopo",
                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/%3$d/%2$d/%1$d",
                false
        ).save();
    }

    // checks url, saves map, changes name if it exists, returns actual name or null
    public static String save(String name, String url, String projection) {
        try {
            new URL(String.format(url, 1, 1, 1));
        } catch (NullPointerException | IllegalFormatException | MalformedURLException e) {
            Files.getInstance().log("incorrect url of " + name + ": " + url + ": " + e);
            return null;
        }
        boolean ellipsoid = false;
        if (projection != null) {
            if (projection.equals(jsonProjectionEllipsoid)) {
                ellipsoid = true;
            } else {
                Files.getInstance().log("incorrect projection of " + name + ": " + projection);
            }
        }
        return new Map(name, url, ellipsoid).save();
    }

    // if mapName == null or map directory does not exist, returns default map
    public static Map load(String mapName) {
        if (mapName == null) {
            mapName = "osm";
        }
        String properties = Files.getInstance().readMapProperties(mapName);
        if (properties == null) {
            mapName = "osm";
            properties = Files.getInstance().readMapProperties(mapName);
        }
        String url = null;
        boolean ellipsoid = false;
        if (properties != null && !properties.isEmpty()) {
            try {
                JSONObject json = new JSONObject(properties);
                url = json.optString(jsonUrl, null);
                ellipsoid = jsonProjectionEllipsoid.equals(json.optString(jsonProjection));
            } catch (JSONException e) {
                Files.getInstance().logOnce("Map.load", "cannot read properties of " + mapName + ": " + e.toString());
            }
        }
        return new Map(mapName, url, ellipsoid);
    }


    // returns url to download the tile or null
    public URL getUrl(int z, int y, int x) {
        if (url == null) {
            return null;
        }
        try {
            return new URL(String.format(url, x, y, z));
        } catch (MalformedURLException e) {
            Files.getInstance().logOnce("Map.getUrl", "incorrect url of " + name + ": " + url + ": " + e);
            return null;
        }
    }


    private String save() {
        // JSON.toString() escapes '\' so it looks like : http:\/\/map
        String properties = "{\n   \"" + jsonUrl + "\": \"" + url + '"';
        if (ellipsoid) {
            properties += ",\n   \"" + jsonProjection + "\": \"" + jsonProjectionEllipsoid + '"';
        }
        properties += "\n}";
        return Files.getInstance().addMap(name, properties);
    }
}