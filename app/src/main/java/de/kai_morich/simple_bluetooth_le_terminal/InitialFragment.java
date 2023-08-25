package de.kai_morich.simple_bluetooth_le_terminal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;

public class InitialFragment extends Fragment {
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 아래 코드를 통하여 xml과 연결됨
        View view = inflater.inflate(R.layout.fragment_initial,container,false);
        Button btn1 = (Button) view.findViewById(R.id.bluetooth_connect_btn);
        Button btn2 = (Button) view.findViewById(R.id.sound_choice_btn);
        Button btn3 = (Button) view.findViewById(R.id.recording_btn);

        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                Fragment fragment = new DevicesFragment();
                fragment.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "device").addToBackStack(null).commit();
            }
        });
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                Fragment fragment2 = new SoundFragment();
                fragment2.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment2, "device").addToBackStack(null).commit();
            }
        });
        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                Fragment fragment3 = new RecordFragment();
                fragment3.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment3, "device").addToBackStack(null).commit();
            }
        });
        return view;
    }
}
