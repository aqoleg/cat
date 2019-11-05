/*
root singleton
creates root directory and files on the first launch, writes log

files:
sd/cat/.nomedia
sd/cat/log.txt
sd/cat/maps
sd/cat/tracks
 */
package space.aqoleg.cat;

import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

class Data {
    private static Data data;

    private final File log;
    private final File maps;
    private final File tracks;

    private Data() {
        File root = new File(Environment.getExternalStorageDirectory(), "cat");
        log = new File(root, "log.txt");
        if (!root.isDirectory()) {
            if (root.mkdirs()) {
                try {
                    if (!new File(root, ".nomedia").createNewFile()) {
                        throw new IOException();
                    }
                } catch (IOException e) {
                    writeLog("can not create .nomedia " + e.toString());
                }
            }
        }
        maps = new File(root, "maps");
        if (!maps.isDirectory()) {
            if (!maps.mkdir()) {
                writeLog("can not create " + maps.getAbsolutePath());
            }
        }
        tracks = new File(root, "tracks");
        if (!tracks.isDirectory()) {
            if (!tracks.mkdir()) {
                writeLog("can not create " + tracks.getAbsolutePath());
            }
        }
    }

    static Data getInstance() {
        if (data == null) {
            data = new Data();
        }
        return data;
    }

    void writeLog(String string) {
        try {
            FileWriter writer = new FileWriter(log, true);
            writer.append(string);
            writer.append(System.getProperty("line.separator"));
            writer.flush();
            writer.close();
        } catch (IOException ignored) {
        }
    }

    File getMaps() {
        return maps;
    }

    File getTracks() {
        return tracks;
    }
}