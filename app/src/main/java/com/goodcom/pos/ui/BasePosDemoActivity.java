package com.goodcom.pos.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.goodcom.pos.MainApp;
import com.goodcom.pos.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Common abstract base activity for POS demo pages.
 * Provides unified toolbar setup and thread-safe UI logging.
 */
public abstract class BasePosDemoActivity extends AppCompatActivity {
    protected String TAG = getClass().getSimpleName();

    protected TextView mTvLog;
    protected ScrollView mSvLog;
    protected ScrollView mSvOuter;
    protected View mToolbarStatusDot;
    protected TextView mTvToolbarTitle;
    protected final SimpleDateFormat mTimeFmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
        initBaseViews();
        initViews();
    }

    private final MainApp.ConnectionListener mConnListener = this::renderConnection;

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.getInstance().addConnectionListener(mConnListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderConnection(MainApp.getInstance().isSdkConnected());
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.getInstance().removeConnectionListener(mConnListener);
    }

    /** Called on every service state change; subclasses may override to refresh their data. */
    protected void onConnectionChanged(boolean connected) {
    }

    private void renderConnection(boolean connected) {
        if (mToolbarStatusDot != null) {
            mToolbarStatusDot.setBackgroundResource(connected ? R.drawable.dot_green_toolbar : R.drawable.dot_orange_toolbar);
        }
        onConnectionChanged(connected);
    }

    protected abstract int getLayoutResId();

    protected abstract void initViews();

    private void initBaseViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());

            CharSequence originalTitle = toolbar.getTitle();
            if (originalTitle == null && getTitle() != null) {
                originalTitle = getTitle();
            }

            View titleView = getLayoutInflater().inflate(R.layout.layout_toolbar_title_with_dot, toolbar, false);
            mTvToolbarTitle = titleView.findViewById(R.id.tv_toolbar_custom_title);
            mToolbarStatusDot = titleView.findViewById(R.id.view_toolbar_status_dot);

            if (mTvToolbarTitle != null && originalTitle != null) {
                mTvToolbarTitle.setText(originalTitle);
            }
            toolbar.addView(titleView);
        }

        mTvLog = findViewById(R.id.tv_log);
        mSvLog = findViewById(R.id.sv_log);
        mSvOuter = findViewById(R.id.sv_outer);
    }

    protected void appendLog(String message) {
        final String line = "[" + mTimeFmt.format(new Date()) + "] " + message + "\n";
        Log.i(TAG, message);
        runOnUiThread(() -> {
            if (mTvLog != null) {
                mTvLog.append(line);
                if (mSvLog != null) {
                    mSvLog.post(() -> mSvLog.fullScroll(ScrollView.FOCUS_DOWN));
                }
                if (mSvOuter != null) {
                    mSvOuter.post(() -> mSvOuter.fullScroll(ScrollView.FOCUS_DOWN));
                }
            }
        });
    }

    protected void clearLog() {
        if (mTvLog != null) {
            mTvLog.setText("");
        }
    }
}
