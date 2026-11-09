package com.goodcom.pos.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.goodcom.pos.MainApp;
import com.goodcom.pos.R;

/**
 * Home screen: global service status on top, product modules below.
 */
public class MainActivity extends AppCompatActivity
        implements View.OnClickListener, MainApp.ConnectionListener {

    private TextView mTvStatus;
    private View mStatusDot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTvStatus = findViewById(R.id.tv_service_status);
        mStatusDot = findViewById(R.id.view_status_dot);

        findViewById(R.id.btn_reconnect).setOnClickListener(this);
        findViewById(R.id.module_card_reader).setOnClickListener(this);
        findViewById(R.id.module_pin_pad).setOnClickListener(this);
        findViewById(R.id.module_emv).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.getInstance().addConnectionListener(this);
        // The binding may have dropped while we were in background: re-sync on every return.
        renderStatus(MainApp.getInstance().isSdkConnected());
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.getInstance().removeConnectionListener(this);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        renderStatus(connected);
    }

    private void renderStatus(boolean connected) {
        mTvStatus.setText(connected ? "Service Connected" : "Service Disconnected");
        mStatusDot.setBackgroundResource(connected ? R.drawable.dot_green : R.drawable.dot_orange);
        findViewById(R.id.btn_reconnect).setVisibility(connected ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_reconnect) {
            MainApp.getInstance().connectSdk();
            mTvStatus.setText("Connecting...");
        } else if (id == R.id.module_card_reader) {
            startActivity(new Intent(this, CardReaderActivity.class));
        } else {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
        }
    }
}
