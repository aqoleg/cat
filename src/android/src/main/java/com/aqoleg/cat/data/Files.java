/*
handles operations in filesystem, creates folders and files

files:
/cat/                 // app's folder in the storage root
 .nomedia
 log.txt
 maps/
  mapName/            // map1, map2, ...
   properties.txt     // optional
   z/                 // 0 ... 18
    y/                // 0 ... 2^z-1
     x.extension      // /cat/maps/myMap/10/4/4.png or /maps/otherMap/11/40/48.jpeg
 tracks/
  trackName.gpx       // track1, track2, ...
  current/
   trackName.tmp      // currently writing track (unclosed)
 */
package com.aqoleg.cat.data;

import android.os.Environment;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Files {
    private static final String mapPropertiesFileName = "properties.txt";
    private static final SimpleDateFormat currentTrackFileName =
            new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'.tmp'", Locale.ENGLISH);
    // 2023-01-20T11-28-00.tmp in the local time zone

    private static Files files; // singleton

    private final HashSet<String> tags = new HashSet<>();
    private final File log;
    private final File maps;
    private final File tracks;
    private final File currentTrack;

    // initialization, creates default files and folders
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Files() {
        File root = new File(Environment.getExternalStorageDirectory(), "cat");
        if (!root.isDirectory()) {
            if (!root.mkdirs()) {
                root.delete();
            }
            try {
                new File(root, ".nomedia").createNewFile();
            } catch (IOException ignored) {
            }
        }
        log = new File(root, "log.txt");
        maps = new File(root, "maps");
        if (!maps.isDirectory()) {
            if (!maps.mkdirs()) {
                maps.delete();
            }
            Map.addDefault();
        }
        tracks = new File(root, "tracks");
        if (!tracks.isDirectory()) {
            if (!tracks.mkdirs()) {
                tracks.delete();
            }
            Track.writeDefault(new File(tracks, "cat.gpx"));
        }
        currentTrack = new File(tracks, "current");
        if (!currentTrack.isDirectory()) {
            if (!currentTrack.mkdirs()) {
                currentTrack.delete();
            }
        }
    }


    public static Files getInstance() {
        if (files == null) {
            files = new Files();
        }
        return files;
    }


    // appends stacktrace in the end of log.txt
    public void log(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer, true));
        log(writer.toString());
    }

    // if this tag is the first tag since the app launch, appends the msg in the end of the log.txt
    public void logOnce(String tag, String msg) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            log(msg);
        }
    }

    // appends msg in the end of log.txt
    public void log(String msg) {
        Writer writer = null;
        try {
            writer = new FileWriter(log, true).append(msg).append('\n').append('\n');
        } catch (Exception ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // returns sorted list of names of map folders
    public ArrayList<String> getMapNames() {
        ArrayList<String> arrayList = new ArrayList<>();
        String[] list = maps.list();
        if (list == null) {
            log("empty map folder");
            return arrayList;
        }
        Arrays.sort(list);
        for (String s : list) {
            if (new File(maps, s).isDirectory()) {
                arrayList.add(s);
            }
        }
        return arrayList;
    }

    // creates new map, changes mapName if it exists, returns new map name or null
    String addMap(String mapName, String properties) {
        File mapDir = new File(maps, mapName);
        if (mapDir.exists()) {
            String mapNameBase = mapName;
            int i = 1;
            do {
                mapName = mapNameBase + '_' + i++;
                mapDir = new File(maps, mapName);
            } while (mapDir.exists());
        }
        if (!mapDir.mkdirs()) {
            log("cannot create " + mapDir.getAbsolutePath());
            return null;
        }
        Writer writer = null;
        try {
            writer = new FileWriter(new File(mapDir, mapPropertiesFileName)).append(properties);
        } catch (IOException e) {
            log("cannot write properties in " + mapDir.getAbsolutePath() + ": " + e);
            return null;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
        return mapName;
    }

    // returns properties, or empty string if map contains no properties, or null if there is no such map
    String readMapProperties(String mapName) {
        File mapDir = new File(maps, mapName);
        if (!mapDir.isDirectory()) {
            return null;
        }
        File properties = new File(mapDir, mapPropertiesFileName);
        if (!properties.isFile()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(properties));
            String line = reader.readLine();
            while (line != null) {
                builder.append(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            log("cannot read " + properties.getAbsolutePath() + ": " + e);
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

    // extension 'jpeg' or 'png'
    public void saveTile(byte[] tileBytes, String mapName, int z, int y, int x, String extension) {
        File yDir = new File(maps, mapName + File.separator + z + File.separator + y);
        if (!yDir.isDirectory() && !yDir.mkdirs()) {
            logOnce("Files.saveFile.0", "cannot create " + yDir.getAbsolutePath());
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(new File(yDir, x + "." + extension));
            stream.write(tileBytes);
        } catch (IOException e) {
            logOnce("Files.saveTile.1", "cannot save tile in " + yDir.getAbsolutePath() + ": " + e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // returns tile absolute path or null
    public String getTilePath(String mapName, int z, int y, int x) {
        String path = mapName + File.separator + z + File.separator + y + File.separator + x;
        File file = new File(maps, path + ".png");
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        file = new File(maps, path + ".jpeg");
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        return null;
    }

    // returns tile path, if exists, or tile path without extension
    public String getTilePathOrName(String mapName, int z, int y, int x) {
        String path = mapName + File.separator + z + File.separator + y + File.separator + x;
        File file = new File(maps, path + ".png");
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        file = new File(maps, path + ".jpeg");
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        return new File(maps, path).getAbsolutePath();
    }

    // returns reverse sorted list of names of track files which ends with '.gpx'
    public ArrayList<String> getTrackNames() {
        // Do not cache because of WeakHashMap keys!
        ArrayList<String> arrayList = new ArrayList<>();
        String[] list = tracks.list();
        if (list == null) {
            return arrayList;
        }
        Arrays.sort(list);
        String s;
        for (int i = list.length - 1; i >= 0; i--) {
            s = list[i];
            if (new File(tracks, s).isFile() && s.endsWith(".gpx")) {
                arrayList.add(s);
            }
        }
        return arrayList;
    }

    public void deleteTrack(String trackName) {
        if (!new File(tracks, trackName).delete()) {
            log("cannot delete track " + trackName);
        }
    }

    File getTrack(String trackName) {
        return new File(tracks, trackName);
    }

    // returns last current track which ends with '.tmp' or null
    File getLastCurrentTrack() {
        String[] list = currentTrack.list();
        if (list == null || list.length == 0) {
            return null;
        }
        Arrays.sort(list);
        for (int i = list.length - 1; i >= 0; i--) {
            File file = new File(currentTrack, list[i]);
            if (file.isFile() && list[i].endsWith(".tmp")) {
                return file;
            }
        }
        return null;
    }

    File createCurrentTrack() {
        return new File(currentTrack, currentTrackFileName.format(new Date()));
    }

    // moves current track from /tracks/current to /tracks
    void moveCurrentTrack(File file) {
        String name = file.getName();
        name = name.substring(0, name.length() - 4) + ".gpx"; // .tmp -> .gpx
        if (!file.renameTo(new File(tracks, name))) {
            log("cannot move current track " + file.getAbsolutePath());
        }
    }
}