package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;

public class SoundFragment extends Fragment {
    private SoundPool mediaPlayer;
    private ArrayList<String> songNames;
    private TypedArray songIds;
    private int selectedPosition = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 아래 코드를 통하여 xml과 연결됨
        View view = inflater.inflate(R.layout.fragment_sound,container,false);
        // Get the song names and ids from the resources
        songNames = new ArrayList<>();
        String[] rawSongNames = getResources().getStringArray(R.array.song_names);
        for (String name : rawSongNames) {
            songNames.add(name);
        }
        songIds = getResources().obtainTypedArray(R.array.song_ids);

        // Set up the list view
        ListView listView = view.findViewById(R.id.list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, songNames);
        listView.setAdapter(adapter);

        // Set the click listener for the list view
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playSong(position);
                if (selectedPosition != -1) {
                    View previousView = listView.getChildAt(selectedPosition);
                    if (previousView != null) {
                        previousView.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
                // Change the background color of the selected item
                view.setBackgroundColor(Color.YELLOW);
                // Save the position of the selected item
                selectedPosition = position;
                SharedPreferences sharedPreferences= getActivity().getSharedPreferences("test",getActivity().MODE_PRIVATE);    // test 이름의 기본모드 설정
                SharedPreferences.Editor editor= sharedPreferences.edit(); //sharedPreferences를 제어할 editor를 선언
                editor.putInt("inputText",position); // key,value 형식으로 저장
                editor.putBoolean("input",false);
                editor.commit();
            }
        });
        return view;
    }

    private void playSong(int position) {
        // Stop any currently playing song
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Start playing the selected song
        int resourceId = songIds.getResourceId(position, -1);
        if (resourceId != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer = new SoundPool.Builder().setMaxStreams(1).build();
            }
            int soundId = mediaPlayer.load(getActivity(), resourceId, 1);
            mediaPlayer.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
                }
            });
        }
    }

    public void onDestroy() {
        super.onDestroy();

        // Release the media player resources
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Recycle the typed array
        songIds.recycle();
    }
}
