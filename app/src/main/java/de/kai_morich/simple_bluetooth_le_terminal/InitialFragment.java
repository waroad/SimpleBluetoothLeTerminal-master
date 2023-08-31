package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class InitialFragment extends Fragment {
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // 아래 코드를 통하여 xml과 연결됨
        View view = inflater.inflate(R.layout.fragment_initial,container,false);
        ImageButton btn1 = (ImageButton) view.findViewById(R.id.bluetooth_connect_btn);
        Button btn2 = (Button) view.findViewById(R.id.choice_button);

        /*Button btn2 = (Button) view.findViewById(R.id.sound_choice_btn);
        Button btn3 = (Button) view.findViewById(R.id.recording_btn);*/

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
                showFragmentDialog();
            }
        });
        return view;
    }
    //이 부분은 알림음을 선택할 시 녹음 파일을 선택할 수도 있고 기본음을 선택할 수 도 있는 다이어로그창을 띄웁니다.
    private void showFragmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.choice_dialog, null);
        builder.setView(view);
        final Button record_choice = view.findViewById(R.id.record_choice_btn);
        final Button notification_choice = view.findViewById(R.id.notification_choice_btn);

        builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        record_choice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                Fragment record_fragment = new RecordFragment();
                record_fragment.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, record_fragment, "device1").addToBackStack(null).commit();
                dialog.cancel();
            }
        });
        //버튼을 클릭시 기본음 프래그먼트로 이동합니다.
        notification_choice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                Fragment sound_fragment = new SoundFragment();
                sound_fragment.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragment, sound_fragment, "device2").addToBackStack(null).commit();
                dialog.cancel();
            }
        });
        dialog.show();
        //builder.show();
    }
}
