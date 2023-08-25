package de.kai_morich.simple_bluetooth_le_terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class RecordFragment extends Fragment {

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private ArrayList<String> songNames;
    private ArrayAdapter<String> adapter;
    String timeStamp;
    Button recordenter;
    private int selectedPosition = -1;
        @SuppressLint("MissingInflatedId")
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
             View view = inflater.inflate(R.layout.fragment_record,container,false);
        // Request permission to record audio
        // Set up the list view
        songNames = new ArrayList<>();
        ListView listView = view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, songNames);
        listView.setAdapter(adapter);
        recordenter = view.findViewById(R.id.record_button);
        // Load the saved recordings
        loadRecordings();
        //initial(listView);
            recordenter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRecordDialog(listView);
                }
            });
        // Set the click listener for the list view
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (mediaRecorder != null) {
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                    }
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
                    playSong(position);
                    SharedPreferences sharedPreferences= getActivity().getSharedPreferences("test",getActivity().MODE_PRIVATE);    // test 이름의 기본모드 설정
                    SharedPreferences.Editor editor= sharedPreferences.edit(); //sharedPreferences를 제어할 editor를 선언
                    editor.putInt("inputText",position); // key,value 형식으로 저장
                    editor.putBoolean("input",true);
                    editor.commit();
                    Log.d("tag","why");
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    if(selectedPosition!=position){
                        showDeleteDialog(position,listView);
                    }
                    // Save the position of the selected item
                    return true;
                }
            });
        return view;
    }

    /*private void initial(ListView listView){
        boolean ismode;
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("test", Context.MODE_PRIVATE);
        selectedPosition = sharedPreferences.getInt("inputText", -1);
        ismode = sharedPreferences.getBoolean("input", false);
        if(ismode==true&&selectedPosition!=-1){
            View colored = listView.getChildAt(selectedPosition);
            colored.setBackgroundColor(Color.YELLOW);
        }
    }*/
    private void showRecordDialog(ListView listview) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.record_dialog, null);
        builder.setView(view);
        final Button recordbtn = view.findViewById(R.id.recordbtn);
        final Button recordcompletebtn = view.findViewById(R.id.recordcomplete);
        recordbtn.setEnabled(true);
        recordcompletebtn.setEnabled(false);
        recordbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mediaRecorder==null){
                    recordbtn.setText("녹음중");
                    startRecording();
                    recordbtn.setEnabled(false);
                    recordcompletebtn.setEnabled(true);
                }
            }
        });
        builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mediaRecorder != null) {
                    stopRecording();
                    songNames.remove(songNames.size()-1);
                    adapter.notifyDataSetChanged();
                }
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        recordcompletebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaRecorder != null) {
                    stopRecording();
                    //showTitleInputDialog();
                }
                dialog.cancel();
                if (selectedPosition != -1) {
                    View previousView = listview.getChildAt(selectedPosition);
                    if (previousView != null) {
                        previousView.setBackgroundColor(Color.TRANSPARENT);
                    }
                    selectedPosition++;
                    View colored = listview.getChildAt(selectedPosition);
                    colored.setBackgroundColor(Color.YELLOW);
                }
                adapter.notifyDataSetChanged();
            }
        });
        dialog.show();
        //builder.show();
    }

    private void showTitleInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("제목 입력");

        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String title = input.getText().toString();
                songNames.add(0,title);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
    private void loadRecordings() {
        File path = getActivity().getFilesDir();
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".3gp")) {
                    songNames.add(file.getName());
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void startRecording() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        File path = getActivity().getFilesDir();
        timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File file = new File(path, "record" + timeStamp +(songNames.size() + 1)+ ".3gp");
        mediaRecorder.setOutputFile(file.getAbsolutePath());
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            songNames.add(0,file.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;
        //adapter.notifyDataSetChanged();
    }

    private void playSong(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Start playing the selected song
        File path = getActivity().getFilesDir();
        File file = new File(path, songNames.get(position));
        if (file.exists()) {
            mediaPlayer = MediaPlayer.create(getActivity(), Uri.fromFile(file));
            mediaPlayer.start();
        }
    }

    private void deleteSong(int position) {
        // Stop any currently playing song
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            return;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Delete the selected song
        File path = getActivity().getFilesDir();
        File file = new File(path, songNames.get(position));
        if (file.exists()) {
            file.delete();
            songNames.remove(position);
            adapter.notifyDataSetChanged();
        }
    }
    private void showDeleteDialog(int position,ListView listView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.delete_dialog, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        final Button deletebtn = view.findViewById(R.id.deletebtn);
        final Button cancelbtn = view.findViewById(R.id.cancelbtn);
        deletebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSong(position);
                dialog.cancel();
                if (selectedPosition != -1) {
                    View previousView = listView.getChildAt(selectedPosition);
                    if (previousView != null) {
                        previousView.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
                if(selectedPosition>position){
                    selectedPosition--;
                }
                if(selectedPosition!=-1){
                    View colored = listView.getChildAt(selectedPosition);
                    Log.d("tag","long");
                    colored.setBackgroundColor(Color.YELLOW);
                }
                /*SharedPreferences sharedPreferences= getActivity().getSharedPreferences("test",getActivity().MODE_PRIVATE);    // test 이름의 기본모드 설정
                SharedPreferences.Editor editor= sharedPreferences.edit(); //sharedPreferences를 제어할 editor를 선언
                editor.putInt("inputText",selectedPosition); // key,value 형식으로 저장
                editor.commit();*/
            }
        });
        cancelbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Release the media recorder and player resources
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
