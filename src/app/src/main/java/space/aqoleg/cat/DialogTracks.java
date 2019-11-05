/*
list with all tracks to select
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

public class DialogTracks extends DialogFragment implements AdapterView.OnItemClickListener {
    private static final String argumentCurrentTrackN = "trackN";

    private int currentTrackN;

    static DialogTracks newInstance(int currentTrackN) {
        Bundle args = new Bundle();
        args.putInt(argumentCurrentTrackN, currentTrackN);
        DialogTracks dialog = new DialogTracks();
        dialog.setArguments(args);
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_list, container, false);
        ((TextView) view.findViewById(R.id.title)).setText(getString(R.string.tracks));
        currentTrackN = getArguments().getInt(argumentCurrentTrackN);

        ListView listView = view.findViewById(R.id.list);
        listView.setAdapter(new Adapter(getActivity().getApplicationContext()));
        listView.setSelection(currentTrackN);
        listView.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        SavedTracks.getInstance().loadTrack(
                position == currentTrackN ? -1 : position,
                true,
                (MainActivity) getActivity()
        );
        dismiss();
    }

    private class Adapter extends ArrayAdapter<String> {
        Adapter(Context context) {
            super(
                    context,
                    R.layout.list_item,
                    R.id.text,
                    SavedTracks.getInstance().getList()
            );
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (position == currentTrackN) {
                view.setBackgroundColor(Color.GRAY);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    }
}