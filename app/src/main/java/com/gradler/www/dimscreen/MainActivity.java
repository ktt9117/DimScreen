package com.gradler.www.dimscreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.remote_view);

        LinearLayout layout = new LinearLayout(getBaseContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(250, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        layout.setLayoutParams(layoutParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Button btnStart = new Button(getBaseContext());
        btnStart.setLayoutParams(params);
        btnStart.setText("Start Service");
        btnStart.setId(1);
        Button btnEnd = new Button(getBaseContext());
        btnEnd.setLayoutParams(params);
        btnEnd.setText("End Service");
        btnEnd.setId(2);
        btnStart.setOnClickListener(this);
        btnEnd.setOnClickListener(this);

        layout.addView(btnStart);
        layout.addView(btnEnd);

        setContentView(layout);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == 1) {
            startService(new Intent(this, DimScreenService.class));
        } else {
            stopService(new Intent(this, DimScreenService.class));
        }
    }
}
