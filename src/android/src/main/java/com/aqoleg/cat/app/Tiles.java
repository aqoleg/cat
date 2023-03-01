/*
handles tile cache, reads tiles from storage and downloads tiles
 */
package com.aqoleg.cat.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import com.aqoleg.cat.data.Files;
import com.aqoleg.cat.data.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

class Tiles {
    final int cacheSize;
    private final BitmapFactory.Options options = new BitmapFactory.Options();
    private final Files files = Files.getInstance();
    private final Tile[] cache;

    private int nextStackLevel;
    private Reader reader;
    private Downloader downloader;

    Tiles(Display display) {
        options.inPreferredConfig = Bitmap.Config.RGB_565; // 2 bytes/px
        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
        // 3 full screens of tiles
        int cacheSize = (displayMetrics.heightPixels / 256 + 2) * (displayMetrics.widthPixels / 256 + 2) * 3;
        if (cacheSize < 50) {
            cacheSize = 50;
        }
        this.cacheSize = cacheSize;
        cache = new Tile[cacheSize];
    }


    // returns bitmap from the cache, puts it on the top of the stack and starts downloader (if available)
    // if there is no such tile in the cache, returns null and asynchronously loads it from storage
    // if there is no such tile in the storage, gets it using lower zooms
    Bitmap getBitmap(Map map, int z, int y, int x) {
        for (Tile tile : cache) {
            if (tile == null) {
                break;
            }
            if (tile.equals(map, z, y, x)) {
                tile.stackLevel = nextStackLevel++; // put on the top of the stack
                if (tile.url != null && downloader == null) {
                    downloader = new Downloader(tile);
                }
                return tile.bitmap;
            }
        }
        // if tile have not found in the cache, start reader
        if (reader == null) {
            reader = new Reader(map, z, y, x);
        }
        return null;
    }

    void unload() {
        if (reader != null) {
            reader.cancel(true);
            reader = null;
        }
        if (downloader != null) {
            downloader.cancel(true);
            downloader = null;
        }
    }


    private class Reader extends AsyncTask<Void, Void, Void> {
        private final Tile tile;

        private Reader(Map map, int z, int y, int x) {
            tile = new Tile(map, z, y, x);
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        @Override
        protected Void doInBackground(Void... voids) {
            try {
                tile.bitmap = loadBitmap(tile.z, tile.y, tile.x);
                if (tile.bitmap != null) {
                    return null;
                }
                tile.url = tile.map.getUrl(tile.z, tile.y, tile.x);
                // try to fill the tile using lower zooms
                int pxSize = 256;
                int xPxLeft = 0, yPxTop = 0;
                int zTile = tile.z;
                int yTile = tile.y;
                int xTile = tile.x;
                for (int i = 0; i < 5; i++) {
                    if (zTile-- == 0) {
                        break;
                    }
                    pxSize = pxSize >> 1;

                    xPxLeft = xPxLeft >> 1;
                    if ((xTile & 0b1) == 1) {
                        xPxLeft += 128; // right half of the tile
                    }
                    xTile = xTile >> 1;

                    yPxTop = yPxTop >> 1;
                    if ((yTile & 0b1) == 1) {
                        yPxTop += 128; // bottom half of the tile
                    }
                    yTile = yTile >> 1;

                    tile.bitmap = loadBitmap(zTile, yTile, xTile);
                    if (tile.bitmap != null) {
                        tile.bitmap = Bitmap.createBitmap(tile.bitmap, xPxLeft, yPxTop, pxSize, pxSize);
                        tile.bitmap = Bitmap.createScaledBitmap(tile.bitmap, 256, 256, true);
                        return null;
                    }
                }
                return null;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                super.onPostExecute(aVoid);
                int cachePos = 0; // replace the bottom item
                int stackLevel, lowestStackLevel = nextStackLevel;
                for (int i = 0; i < cacheSize; i++) {
                    if (cache[i] == null) {
                        cachePos = i;
                        break;
                    }
                    stackLevel = cache[i].stackLevel;
                    if (stackLevel < lowestStackLevel) {
                        cachePos = i;
                        lowestStackLevel = stackLevel;
                    }
                }
                tile.stackLevel = nextStackLevel++;
                cache[cachePos] = tile;
                reader = null;
                App.refresh();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }


        private Bitmap loadBitmap(int z, int y, int x) {
            return BitmapFactory.decodeFile(files.getTilePath(tile.map.name, z, y, x), options);
        }
    }

    private class Downloader extends AsyncTask<Void, Void, Void> {
        private final Tile tile;
        private Bitmap bitmap;

        private Downloader(Tile tile) {
            this.tile = tile;
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        @Override
        protected Void doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            try {
                connection = (HttpURLConnection) tile.url.openConnection();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    if (tile.url.getProtocol().equals("https")) {
                        ((HttpsURLConnection) connection).setSSLSocketFactory(new TlsSocketFactory());
                    }
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "cat.aqoleg.com");
                inputStream = connection.getInputStream();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException(connection.getResponseMessage());
                }

                ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length = inputStream.read(buffer);
                while (length >= 0) {
                    byteArray.write(buffer, 0, length);
                    length = inputStream.read(buffer);
                }
                byte[] tileBytes = byteArray.toByteArray(); // use bytes, because of creating both file and bitmap

                bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes.length, options);
                if (bitmap == null) {
                    files.logOnce("Tiles.Downloader.doInBackground.0", "no bitmap for " + tile.url);
                } else {
                    String extension = connection.getContentType(); // image/png, image/jpeg
                    if (extension == null) {
                        extension = "jpeg";
                    } else {
                        extension = extension.substring(extension.lastIndexOf('/') + 1);
                    }
                    files.saveTile(tileBytes, tile.map.name, tile.z, tile.y, tile.x, extension);
                }
            } catch (Throwable t) {
                files.logOnce("Tiles.Downloader.doInBackground.1", "cannot download " + tile.url + ": " + t);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                super.onPostExecute(aVoid);
                if (bitmap != null) { // do not remove preview if bitmap has not been loaded
                    tile.bitmap = bitmap;
                }
                tile.url = null;
                downloader = null;
                App.refresh();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }

    private class Tile {
        private final Map map;
        private final int z;
        private final int y;
        private final int x;
        private Bitmap bitmap;
        private int stackLevel;
        private URL url;

        private Tile(Map map, int z, int y, int x) {
            this.map = map;
            this.z = z;
            this.y = y;
            this.x = x;
        }


        private boolean equals(Map map, int z, int y, int x) {
            return this.map.name.equals(map.name) && this.z == z && this.y == y && this.x == x;
        }
    }

    private class TlsSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory socketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

        @Override
        public String[] getDefaultCipherSuites() {
            return socketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return socketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return enableTls(socketFactory.createSocket());
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enableTls(socketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return enableTls(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return enableTls(socketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enableTls(socketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enableTls(socketFactory.createSocket(address, port, localAddress, localPort));
        }


        private Socket enableTls(Socket socket) {
            if (socket != null && (socket instanceof SSLSocket)) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.1", "TLSv1.2"});
            }
            return socket;
        }
    }
}