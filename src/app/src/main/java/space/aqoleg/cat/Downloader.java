/*
asynchronous tile downloader
downloads tile, saves it into memory and invokes callback with bitmap
 */
package space.aqoleg.cat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

class Downloader extends AsyncTask<Void, Void, Boolean> {
    private final int mapN;
    private final int z;
    private final int y;
    private final int x;
    private final Callback callback;
    private Bitmap bitmap;

    Downloader(int mapN, int z, int y, int x, Callback callback) {
        this.callback = callback;
        this.mapN = mapN;
        this.z = z;
        this.y = y;
        this.x = x;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Maps maps = Maps.getInstance();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(maps.getUrl(mapN, z, y, x));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "cat");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            String contentType = connection.getContentType(); // image/png, image/jpeg
            DataInputStream inputStream = new DataInputStream(connection.getInputStream());

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            do {
                length = inputStream.read(buffer);
                if (length > 0) {
                    byteArray.write(buffer, 0, length);
                } else {
                    break;
                }
            } while (true);
            inputStream.close();
            byte[] content = byteArray.toByteArray();

            bitmap = BitmapFactory.decodeByteArray(content, 0, content.length);
            if (bitmap == null) {
                return false;
            }
            String extension = '.' + contentType.substring(contentType.lastIndexOf('/') + 1);
            FileOutputStream outputStream = new FileOutputStream(maps.createTile(mapN, z, y, x, extension));
            outputStream.write(content);
            outputStream.close();
            return true;
        } catch (Exception exception) {
            if (exception instanceof IOException) {
                return false;
            }
            Data.getInstance().writeLog("can not download tile: " + exception.toString());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        callback.onDownloadFinish(mapN, z, y, x, result ? bitmap : null);
    }

    interface Callback {
        void onDownloadFinish(int mapN, int z, int y, int x, Bitmap bitmap);
    }
}
