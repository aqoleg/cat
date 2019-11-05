/*
cache with bitmaps
when get the tile, returns bitmap from cache and puts on the top of the stack,
or, if there is no such tile, asynchronously loads it from memory and puts it on the top of the stack
instead of the bottom item, if there is no such tile in the memory, try to get it from the lower zooms
and calls downloader in the one instance
 */
package space.aqoleg.cat;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.RandomAccessFile;
import java.util.Arrays;

class BitmapCache implements Downloader.Callback {
    private final Maps maps;
    private final Callback callback;

    private final int cacheSize;
    private final int[] cacheMapN;
    private final int[] cacheZ;
    private final int[] cacheY;
    private final int[] cacheX;
    private final Bitmap[] cacheBitmap;
    private final boolean[] cacheToDownload;
    private final int[] cacheStackLevel;
    private int nextStackLevel = 0; // possibly can not overflow

    private boolean hasLoader = false;
    private boolean hasDownloader = false;

    BitmapCache(Callback callback) {
        maps = Maps.getInstance();
        this.callback = callback;
        cacheSize = getCacheSize();
        cacheMapN = new int[cacheSize];
        Arrays.fill(cacheMapN, -1); // tile with mapN = 0, z = 0, y = 0, x = 0 did not loaded yet
        cacheZ = new int[cacheSize];
        cacheY = new int[cacheSize];
        cacheX = new int[cacheSize];
        cacheBitmap = new Bitmap[cacheSize];
        cacheToDownload = new boolean[cacheSize];
        cacheStackLevel = new int[cacheSize];
    }

    @Override
    public void onDownloadFinish(int mapN, int z, int y, int x, Bitmap bitmap) {
        for (int i = 0; i < cacheSize; i++) {
            if (x == cacheX[i] && y == cacheY[i] && z == cacheZ[i] && mapN == cacheMapN[i]) {
                if (bitmap != null) {
                    cacheBitmap[i] = bitmap;
                }
                cacheToDownload[i] = false; // even if this tile did not loaded, do not try one more time
                break;
            }
        }
        hasDownloader = false;
        callback.onBitmapCacheLoad();
    }

    // returns bitmap from cache or null if it does not exist in the cache
    Bitmap getBitmap(int mapN, int z, int y, int x) {
        for (int i = 0; i < cacheSize; i++) {
            if (x == cacheX[i] && y == cacheY[i] && z == cacheZ[i] && mapN == cacheMapN[i]) {
                // put on the top of the stack
                nextStackLevel++;
                cacheStackLevel[i] = nextStackLevel;
                // start download
                if (cacheToDownload[i] && !hasDownloader && maps.canDownload(mapN)) {
                    hasDownloader = true;
                    new Downloader(mapN, z, y, x, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
                return cacheBitmap[i];
            }
        }
        // if tile did not find in the cache, start loader
        if (!hasLoader) {
            hasLoader = true;
            new Loader(mapN, z, y, x).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        return null;
    }

    private int getCacheSize() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r");
            String line = reader.readLine();
            reader.close();
            // MemTotal:      1030428 kB
            line = line.substring(line.indexOf("MemTotal:") + 9, line.indexOf("kB")).trim();
            int value = Integer.valueOf(line);
            // 30 + ~50 tiles per gb
            value = value / 20000;
            if (value < 10) {
                value = 10;
            } else if (value > 200) {
                value = 200;
            }
            return value + 30;
        } catch (Exception exception) {
            Data.getInstance().writeLog("can not determine memory size: " + exception.toString());
        }
        return 50;
    }

    interface Callback {
        void onBitmapCacheLoad();
    }

    class Loader extends AsyncTask<Void, Void, Void> {
        private final int loaderMapN;
        private final int loaderZ;
        private final int loaderY;
        private final int loaderX;
        private Bitmap bitmap;
        private boolean isBitmapLoaded;

        Loader(int mapN, int z, int y, int x) {
            loaderMapN = mapN;
            loaderZ = z;
            loaderY = y;
            loaderX = x;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            bitmap = maps.getTile(loaderMapN, loaderZ, loaderY, loaderX);
            isBitmapLoaded = bitmap != null;
            if (!isBitmapLoaded) {
                // try to fill with tile from previous zoom
                int fullTileSize = maps.getSize(loaderMapN);
                int xLeftPx = 0;
                int yTopPx = 0;
                int size = fullTileSize;
                int z = loaderZ;
                int y = loaderY;
                int x = loaderX;
                for (int i = 0; i < 5; i++) {
                    z--;
                    if (z == 0) {
                        break;
                    }
                    size = size >> 1;

                    xLeftPx = xLeftPx >> 1;
                    if ((x & 0b1) == 1) {
                        // right half of the tile
                        xLeftPx += fullTileSize >> 1;
                    }
                    x = x >> 1;

                    yTopPx = yTopPx >> 1;
                    if ((y & 0b1) == 1) {
                        // bottom half of the tile
                        yTopPx += fullTileSize >> 1;
                    }
                    y = y >> 1;

                    bitmap = maps.getTile(loaderMapN, z, y, x);
                    if (bitmap != null) {
                        bitmap = Bitmap.createBitmap(bitmap, xLeftPx, yTopPx, size, size);
                        bitmap = Bitmap.createScaledBitmap(bitmap, fullTileSize, fullTileSize, true);
                        return null;
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // do operation in the stack in the main thread
            // search for the bottom item of the stack
            int cacheN = 0;
            int lowestStackLevel = Integer.MAX_VALUE;
            for (int i = 0; i < cacheSize; i++) {
                if (cacheStackLevel[i] < lowestStackLevel) {
                    cacheN = i;
                    lowestStackLevel = cacheStackLevel[i];
                }
            }
            // put tile instead the bottom item
            cacheMapN[cacheN] = loaderMapN;
            cacheZ[cacheN] = loaderZ;
            cacheY[cacheN] = loaderY;
            cacheX[cacheN] = loaderX;
            cacheBitmap[cacheN] = bitmap;
            cacheToDownload[cacheN] = !isBitmapLoaded;
            // put on the top of the stack
            nextStackLevel++;
            cacheStackLevel[cacheN] = nextStackLevel;
            // refresh
            hasLoader = false;
            callback.onBitmapCacheLoad();
        }
    }
}