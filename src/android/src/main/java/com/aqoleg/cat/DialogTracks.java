/*
list with all tracks to select, search, delete
 */
package com.aqoleg.cat;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.aqoleg.cat.app.App;
import com.aqoleg.cat.data.Files;

import java.util.ArrayList;

public class DialogTracks extends DialogFragment implements AdapterView.OnItemClickListener, View.OnClickListener {
    private String title;
    private ArrayList<String> trackNames;
    private Adapter adapter;
    private int colorNotSelected;
    private int colorSelected;

    static DialogTracks newInstance() {
        DialogTracks dialog = new DialogTracks();
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.dialog);
        return dialog;
    }


    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.dialog_tracks, container, false);
            title = getResources().getString(R.string.tracksTitle);
            ((ListView) view.findViewById(R.id.list)).setOnItemClickListener(this);
            colorNotSelected = getResources().getColor(R.color.mainBlack);
            colorSelected = getResources().getColor(R.color.tracksPurple);
            view.findViewById(R.id.delete).setOnClickListener(this);
            view.findViewById(R.id.deselect).setOnClickListener(this);
            view.findViewById(R.id.searchUp).setOnClickListener(this);
            return view;
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
        return null;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onStart() {
        try {
            super.onStart();
            getDialog().getWindow().setLayout(
                    (int) (192 * getActivity().getResources().getDisplayMetrics().density),
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            getDialog().getWindow().setGravity(Gravity.RIGHT | Gravity.BOTTOM);
            if (trackNames == null) {
                trackNames = Files.getInstance().getTrackNames();
            }
            setTracksTitle();
            adapter = new Adapter(getActivity().getApplicationContext());
            ListView listView = getView().findViewById(R.id.list);
            listView.setAdapter(adapter);
            listView.setSelection(App.getTracksPosition());
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            App.setTracksPosition(((ListView) getView().findViewById(R.id.list)).getFirstVisiblePosition());
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onStop() {
        try {
            super.onStop();
            App.setTracksPosition(((ListView) getView().findViewById(R.id.list)).getFirstVisiblePosition());
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            App.changeTrackVisibility(trackNames.get(position));
            setTracksTitle();
            adapter.notifyDataSetChanged();
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onClick(View view) {
        try {
            switch (view.getId()) {
                case R.id.delete:
                    new DialogDelete().show();
                    break;
                case R.id.deselect:
                    App.deselectAllTracks();
                    setTracksTitle();
                    adapter.notifyDataSetChanged();
                    break;
                case R.id.searchUp:
                    int stopPos = ((ListView) getView().findViewById(R.id.list)).getLastVisiblePosition();
                    App.searchUpVisibleTracks(trackNames, stopPos);
                    dismiss();
                    break;
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }


    private void setTracksTitle() {
        String text = String.format(title, App.getNumberOfSelectedTracks(), trackNames.size());
        ((TextView) getView().findViewById(R.id.title)).setText(text);
    }


    private class Adapter extends BaseAdapter {
        private final LayoutInflater inflater;

        Adapter(Context context) {
            inflater = LayoutInflater.from(context);
        }


        @Override
        public int getCount() {
            try {
                return trackNames.size();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            try {
                return trackNames.get(position);
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                TextView view;
                if (convertView == null) {
                    view = (TextView) inflater.inflate(R.layout.list_item, parent, false);
                } else {
                    view = (TextView) convertView;
                }
                String trackName = trackNames.get(position);
                view.setText(trackName.substring(0, trackName.length() - 4));
                if (App.isTrackSelected(trackName)) {
                    view.setTextColor(colorSelected);
                } else {
                    view.setTextColor(colorNotSelected);
                }
                return view;
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return null;
        }
    }

    private class DialogDelete extends Dialog implements View.OnClickListener {

        DialogDelete() {
            super(getActivity(), R.style.prompt);
        }


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            try {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.dialog_delete);
                int n = App.getNumberOfSelectedTracks();
                String deleteTitle;
                if (n == 0) {
                    deleteTitle = getString(R.string.selectTracks);
                } else if (n == 1) {
                    deleteTitle = getString(R.string.deleteTrack);
                } else {
                    deleteTitle = String.format(getString(R.string.deleteTracks), n);
                }
                ((TextView) findViewById(R.id.title)).setText(deleteTitle);
                findViewById(R.id.cancel).setOnClickListener(this);
                findViewById(R.id.ok).setOnClickListener(this);
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }

        @Override
        public void onClick(View view) {
            try {
                switch (view.getId()) {
                    case R.id.cancel:
                        dismiss();
                        break;
                    case R.id.ok:
                        App.deleteSelectedTracks();
                        trackNames = Files.getInstance().getTrackNames();
                        setTracksTitle();
                        adapter.notifyDataSetChanged();
                        dismiss();
                        break;
                }
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
        }
    }
}