/*
list with all maps to select
 */
package com.aqoleg.cat;

import android.annotation.SuppressLint;
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

public class DialogMaps extends DialogFragment implements AdapterView.OnItemClickListener {
    private ArrayList<String> mapNames;
    private int colorNotSelected;
    private int colorSelected;

    static DialogMaps newInstance() {
        DialogMaps dialog = new DialogMaps();
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.dialog);
        return dialog;
    }


    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.dialog_maps, container, false);
            ((ListView) view.findViewById(R.id.list)).setOnItemClickListener(this);
            colorNotSelected = getResources().getColor(R.color.mainBlack);
            colorSelected = getResources().getColor(R.color.mainRed);
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
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            getDialog().getWindow().setGravity(Gravity.RIGHT | Gravity.BOTTOM);
            if (mapNames == null) {
                mapNames = Files.getInstance().getMapNames();
            }
            Adapter adapter = new Adapter(getActivity().getApplicationContext());
            ((ListView) getView().findViewById(R.id.list)).setAdapter(adapter);
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            if (!mapNames.get(position).equals(App.getMapName())) {
                App.selectMap(mapNames.get(position));
                dismiss();
            }
        } catch (Throwable t) {
            Files.getInstance().log(t);
        }
    }


    private class Adapter extends BaseAdapter {
        private final LayoutInflater inflater;

        Adapter(Context context) {
            inflater = LayoutInflater.from(context);
        }


        @Override
        public int getCount() {
            try {
                return mapNames.size();
            } catch (Throwable t) {
                Files.getInstance().log(t);
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            try {
                return mapNames.get(position);
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
                String mapName = mapNames.get(position);
                view.setText(mapName);
                if (mapName.equals(App.getMapName())) {
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
}