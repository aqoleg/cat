/*
list with all maps to select
 */
package space.aqoleg.cat;

import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DialogMaps extends DialogFragment implements AdapterView.OnItemClickListener {
    private static final String argumentCurrentMapN = "mapN";

    private int currentMapN;

    static DialogMaps newInstance(int currentMapN) {
        Bundle args = new Bundle();
        args.putInt(argumentCurrentMapN, currentMapN);
        DialogMaps dialog = new DialogMaps();
        dialog.setArguments(args);
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_list, container, false);
        ((TextView) view.findViewById(R.id.title)).setText(getString(R.string.maps));
        currentMapN = getArguments().getInt(argumentCurrentMapN);

        ListView listView = view.findViewById(R.id.list);
        listView.setAdapter(new Adapter(getActivity().getApplicationContext()));
        listView.setSelection(currentMapN);
        listView.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position != currentMapN) {
            ((MainActivity) getActivity()).selectMap(position);
        }
        dismiss();
    }

    private class Adapter extends ArrayAdapter<String> {
        Adapter(Context context) {
            super(
                    context,
                    R.layout.list_item,
                    R.id.text,
                    Maps.getInstance().getList()
            );
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (position == currentMapN) {
                view.setBackgroundColor(Color.GRAY);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    }
}