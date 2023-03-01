/*
handles track cache and selected track list
 */
package com.aqoleg.cat.app;

import android.os.AsyncTask;
import com.aqoleg.cat.ActivityView;
import com.aqoleg.cat.data.Files;
import com.aqoleg.cat.data.Track;

import java.util.*;

class Tracks {
    private final WeakHashMap<String, Track> cache = new WeakHashMap<>(); // synchronize it!

    private HashMap<String, Track> selected = new HashMap<>(); // main thread only, track == null if not loaded
    private Opener opener;
    private Loader loader;
    private Searcher searcher;

    private Tracks() {
    }


    static Tracks load(String[] trackNames) {
        Tracks tracks = new Tracks();
        for (String trackName : trackNames) {
            tracks.selected.put(trackName, null);
        }
        tracks.opener = tracks.new Opener(trackNames);
        return tracks;
    }


    void changeVisibility(String trackName) {
        if (selected.containsKey(trackName)) {
            if (selected.remove(trackName) != null) {
                App.refresh();
            }
        } else {
            Track track;
            synchronized (cache) {
                track = cache.get(trackName);
            }
            selected.put(trackName, track);
            if (track != null) {
                App.centerOnTrack(track);
            } else if (opener == null && loader == null) {
                loader = new Loader(trackName);
            }
        }
    }

    void deselectAll() {
        selected = new HashMap<>();
        unload();
        App.refresh();
    }

    void searchVisible(ArrayList<String> tracks, int stopPos, ActivityView.Boundaries boundaries, boolean ellipsoid) {
        if (searcher != null) {
            searcher.cancel(true);
        }
        searcher = new Searcher(tracks, stopPos, boundaries, ellipsoid);
    }

    int getCacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }

    int getSelectedCount() {
        return selected.size();
    }

    String[] getTrackNames() {
        return selected.keySet().toArray(new String[selected.size()]);
    }

    boolean isSelected(String trackName) {
        return selected.containsKey(trackName);
    }

    void deleteSelected() {
        for (Map.Entry<String, Track> entry : selected.entrySet()) {
            Files.getInstance().deleteTrack(entry.getKey());
        }
        selected = new HashMap<>();
        App.refresh();
    }

    Iterator<Track> getTrackIterator(final boolean ellipsoid) {
        final Iterator<Map.Entry<String, Track>> iterator = selected.entrySet().iterator();
        return new Iterator<Track>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            // returns track or null
            @Override
            public Track next() {
                Map.Entry<String, Track> entry = iterator.next();
                Track track = entry.getValue();
                if (track != null) {
                    track.startPointIterator(ellipsoid);
                    return track;
                } else if (opener == null && loader == null) {
                    loader = new Loader(entry.getKey());
                }
                return null;
            }
        };
    }

    void unload() {
        if (opener != null) {
            opener.cancel(true);
            opener = null;
        }
        if (loader != null) {
            loader.cancel(true);
            loader = null;
        }
        if (searcher != null) {
            searcher.cancel(true);
            searcher = null;
        }
    }


    private class Opener extends AsyncTask<Void, Void, Void> {
        private final String[] trackNames;
        private final HashMap<String, Track> openedTracks = new HashMap<>();

        private Opener(String[] trackNames) {
            this.trackNames = trackNames;
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        @Override
        protected Void doInBackground(Void... voids) {
            try {
                for (String trackName : trackNames) {
                    Track track = Track.load(trackName);
                    openedTracks.put(trackName, track);
                    synchronized (cache) {
                        cache.put(trackName, track);
                    }
                }
                return null;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                super.onPostExecute(result);
                Set<Map.Entry<String, Track>> entrySet = openedTracks.entrySet();
                for (Map.Entry<String, Track> entry : entrySet) {
                    if (selected.containsKey(entry.getKey())) {
                        selected.put(entry.getKey(), entry.getValue());
                    }
                }
                opener = null;
                App.finishLoadingTracks();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }

    private class Loader extends AsyncTask<Void, Void, Void> {
        private final String trackName;
        private Track loadedTrack;

        private Loader(String trackName) {
            this.trackName = trackName;
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        @Override
        protected Void doInBackground(Void... voids) {
            try {
                loadedTrack = Track.load(trackName);
                synchronized (cache) {
                    cache.put(trackName, loadedTrack);
                }
                return null;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                super.onPostExecute(result);
                if (selected.containsKey(trackName)) {
                    selected.put(trackName, loadedTrack);
                }
                loader = null;
                App.centerOnTrack(loadedTrack);
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }

    private class Searcher extends AsyncTask<Void, Void, Void> {
        private ArrayList<String> allTrackNames;
        private int stopPos;
        private final ActivityView.Boundaries boundaries;
        private final boolean ellipsoid;
        private final HashMap<String, Track> foundTracks = new HashMap<>();

        private Searcher(
                ArrayList<String> allTrackNames,
                int stopPos,
                ActivityView.Boundaries boundaries,
                boolean ellipsoid
        ) {
            this.allTrackNames = allTrackNames;
            this.stopPos = stopPos;
            this.boundaries = boundaries;
            this.ellipsoid = ellipsoid;
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (allTrackNames == null) {
                    allTrackNames = Files.getInstance().getTrackNames();
                }
                if (stopPos == -1) {
                    stopPos = allTrackNames.size() - 1;
                }
                for (int i = 0; i <= stopPos; i++) {
                    String trackName = allTrackNames.get(i);
                    Track track;
                    synchronized (cache) {
                        track = cache.get(trackName);
                    }
                    if (track == null) {
                        track = Track.load(trackName);
                        synchronized (cache) {
                            cache.put(trackName, track);
                        }
                    }
                    if (track.contains(boundaries, ellipsoid)) {
                        foundTracks.put(trackName, track);
                    }
                }
                return null;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                super.onPostExecute(result);
                selected.putAll(foundTracks);
                searcher = null;
                App.finishLoadingTracks();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }
}