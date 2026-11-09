package com.goodcom.pos;

import android.app.Application;

import com.goodcom.possdk.GcSmartPosHelper;
import com.goodcom.possdk.core.ConnectCallback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application entry. The SDK is initialized exactly once here so that every screen
 * can simply call SDK APIs without caring about binding.
 */
public class MainApp extends Application {

    /** Observer for service connection state changes (always invoked on the main thread). */
    public interface ConnectionListener {
        void onConnectionChanged(boolean connected);
    }

    private static MainApp sInstance;
    private final List<ConnectionListener> mListeners = new CopyOnWriteArrayList<>();

    public static MainApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        connectSdk();
    }

    /**
     * (Re)start the asynchronous service binding. Safe to call repeatedly (e.g. a manual "Retry").
     * The SDK keeps a single connect callback, so the app owns it and fans out to its own listeners.
     */
    public void connectSdk() {
        GcSmartPosHelper.get().init(this, new ConnectCallback() {
            @Override
            public void onConnectPaySDK() {
                notifyConnection(true);
            }

            @Override
            public void onDisconnectPaySDK() {
                notifyConnection(false);
            }
        });
    }

    public boolean isSdkConnected() {
        return GcSmartPosHelper.get().isConnected();
    }

    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeConnectionListener(ConnectionListener listener) {
        mListeners.remove(listener);
    }

    private void notifyConnection(boolean connected) {
        for (ConnectionListener l : mListeners) {
            l.onConnectionChanged(connected);
        }
    }
}
