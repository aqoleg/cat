/*
map properties singleton
creates default maps on the first app launch, parses list of maps properties from json files,
searches current map by its name on the app start, handles tile's parameters: url, size, projection, name and files

tile's parameters:
z - zoom from 0 to 18, displayed zoom = (z + 1)
x, y - tiles number, tile(0, 0) is the top left tile, from 0 to (2^z - 1)

files:
/maps/map1/, /maps/map2/, ... , /maps/mapN/
/maps/map1/properties.txt   // optional
/maps/map1/z/y/x.extension   // for example /maps/map1/10/4/4.png or /maps/map1/10/4/4.jpeg

properties.txt json file:
{
   "name": "map name",   // optional, if this is not specified, use directory name
   "url": "https://example/x=%1$d/y=%2$d/z=%3$d",   // optional, if this is not specified, can not download
   "size": 256,   // optional, if this is not specified, use 256
   "projection": "ellipsoid"   // optional, if this is not specified, use spherical
}
 */
package space.aqoleg.cat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

class Maps {
    private static final String propertiesFileName = "properties.txt";
    private static final String jsonName = "name";
    private static final String jsonUrl = "url";
    private static final String jsonSize = "size";
    private static final String jsonProjection = "projection";
    private static final String jsonProjectionEllipsoid = "ellipsoid";
    private static final int sizeDefault = 256;

    private static final Maps maps = new Maps();

    private final ArrayList<String> nameList = new ArrayList<>();
    private String[] pathArray; // absolute path of the map's directory
    private String[] urlArray; // can be empty
    private int[] sizeArray;
    private boolean[] isEllipsoidArray; // if true ellipsoid, else spherical

    private Maps() {
    }

    static Maps getInstance() {
        return maps;
    }

    // returns number of the map with this searchingDirectoryName or 0
    int loadMaps(String searchingDirectoryName) {
        String[] list = Data.getInstance().getMaps().list();
        Arrays.sort(list);
        int mapsCount = list.length;
        if (mapsCount == 0) {
            addDefaultMaps();
            return loadMaps("");
        } else {
            nameList.clear();
            pathArray = new String[mapsCount];
            urlArray = new String[mapsCount];
            sizeArray = new int[mapsCount];
            isEllipsoidArray = new boolean[mapsCount];
            int mapN = 0;
            int i = 0;
            for (String string : list) {
                if (string.equals(searchingDirectoryName)) {
                    mapN = i;
                }
                loadMap(string, i);
                i++;
            }
            return mapN;
        }
    }

    // for save state and then load it with loadMaps()
    String getDirectoryName(int mapN) {
        return new File(pathArray[mapN]).getName();
    }

    // for fragment's adapter
    ArrayList<String> getList() {
        return nameList;
    }

    boolean canDownload(int mapN) {
        return !urlArray[mapN].isEmpty();
    }

    String getUrl(int mapN, int z, int y, int x) {
        return String.format(urlArray[mapN], x, y, z);
    }

    int getSize(int mapN) {
        return sizeArray[mapN];
    }

    boolean isEllipsoid(int mapN) {
        return isEllipsoidArray[mapN];
    }

    // returns .png file, or .jpeg file, or null
    Bitmap getTile(int mapN, int z, int y, int x) {
        String path = pathArray[mapN] + File.separator + z + File.separator + y + File.separator + x;
        Bitmap bitmap = BitmapFactory.decodeFile(path + ".png");
        if (bitmap != null) {
            return bitmap;
        }
        return BitmapFactory.decodeFile(path + ".jpeg");
    }

    // creates all directories if they are not exist
    File createTile(int mapN, int z, int y, int x, String extension) {
        File yDirectory = new File(pathArray[mapN] + File.separator + z + File.separator + y);
        if (!yDirectory.isDirectory() && !yDirectory.mkdirs()) {
            Data.getInstance().writeLog("can not create " + yDirectory.getAbsolutePath());
        }
        return new File(yDirectory, x + extension);
    }

    private void addDefaultMaps() {
        addMap(
                "osm",
                "open street map",
                "http://a.tile.openstreetmap.org/%3$d/%1$d/%2$d.png",
                false
        );
        addMap(
                "topo",
                "marshruty",
                "http://maps.marshruty.ru/ml.ashx?al=1&i=1&x=%1$d&y=%2$d&z=%3$d",
                false
        );
        addMap(
                "yasat",
                "yandex satellites",
                "http://sat01.maps.yandex.net/tiles?l=sat&x=%1$d&y=%2$d&z=%3$d&g=Gagarin",
                true
        );
        addMap(
                "arctopo",
                "arcgis topo map",
                "http://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/%3$d/%2$d/%1$d",
                false
        );
        addMap(
                "arcsat",
                "arcgis satellite",
                "http://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%3$d/%2$d/%1$d",
                false
        );
    }

    private void addMap(String directoryName, String mapName, String url, boolean projectionsIsEllipsoid) {
        File directory = new File(Data.getInstance().getMaps(), directoryName);
        if (!directory.mkdir()) {
            Data.getInstance().writeLog("can not create " + directory.getAbsolutePath());
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put(jsonName, mapName);
            json.put(jsonUrl, url);
            if (projectionsIsEllipsoid) {
                json.put(jsonProjection, jsonProjectionEllipsoid);
            }
            byte[] data = json.toString(3).getBytes();

            FileOutputStream outputStream = new FileOutputStream(new File(directory, propertiesFileName));
            outputStream.write(data);
            outputStream.close();
        } catch (Exception e) {
            Data.getInstance().writeLog("can not write properties " + directory.getAbsolutePath() + " " + e.toString());
        }
    }

    private void loadMap(String directoryName, int mapN) {
        pathArray[mapN] = new File(Data.getInstance().getMaps(), directoryName).getAbsolutePath();
        urlArray[mapN] = "";
        String name = "";
        File file = new File(pathArray[mapN], propertiesFileName);
        if (file.isFile()) {
            try {
                FileInputStream inputStream = new FileInputStream(file);
                byte[] data = new byte[inputStream.available()];
                int bytesRead = inputStream.read(data);
                inputStream.close();
                if (bytesRead != data.length) {
                    throw new IOException();
                }

                JSONObject json = new JSONObject(new String(data, "UTF-8"));
                name = json.optString(jsonName);
                urlArray[mapN] = json.optString(jsonUrl);
                sizeArray[mapN] = json.optInt(jsonSize);
                isEllipsoidArray[mapN] = json.optString(jsonProjection).equals(jsonProjectionEllipsoid);
            } catch (Exception e) {
                Data.getInstance().writeLog("can not read properties " + e.toString());
            }
        }
        nameList.add(name.isEmpty() ? directoryName : name);
        if (sizeArray[mapN] == 0) {
            sizeArray[mapN] = sizeDefault;
        }
    }
}