package com.reeman.delige.presenter.impl;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.reeman.delige.R;
import com.reeman.delige.board.BoardConstants;
import com.reeman.delige.board.BoardFactory;
import com.reeman.delige.contract.WiFiConnectContract;
import com.reeman.delige.utils.ToastUtils;
import com.reeman.delige.utils.VoiceHelper;
import com.reeman.delige.utils.WIFIUtils;

import static com.reeman.delige.base.BaseApplication.ros;

import timber.log.Timber;

public class WiFiConnectPresenter implements WiFiConnectContract.Presenter {

    private int currentPhase = PHASE_INIT;
    private static final int PHASE_INIT = 0;
    private static final int PHASE_ANDROID_CONNECTING = 1;
    private static final int PHASE_ROS_CONNECTING = 2;
    private int androidConnectSuccessCount;


    private final WiFiConnectContract.View view;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable connectTimeOutTask;
    private String wifiPass;
    private String wifiName;

    public WiFiConnectPresenter(WiFiConnectContract.View view) {
        this.view = view;
    }

    public void startScanWiFi(WifiManager manager) {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean success = manager.startScan();
                if (!success) {
                    mHandler.postDelayed(this, 500);
                }
            }
        }, 100);
        view.showStartRefreshView();
    }


    @Override
    public void onRefresh(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int wifiState = wifiManager.getWifiState();
        if (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) {
            ToastUtils.showShortToast(context.getString(R.string.text_open_wifi_first));
            view.showRefreshFailedView();
            return;
        }
        startScanWiFi(wifiManager);
    }

    @Override
    public void auth(Context context, String name, String passwd, ScanResult hidden) {
        wifiName = name;
        wifiPass = passwd;
        String wifiSSID = WIFIUtils.getConnectWifiSSID(context);
        if ("".equals(wifiSSID)) wifiSSID = context.getString(R.string.text_not_connected);
        currentPhase = PHASE_ANDROID_CONNECTING;
        VoiceHelper.play("voice_connecting_wifi");
        String prompt = context.getString(R.string.voice_connecting_wifi);
        view.showConnectingView(prompt);
        Timber.w("ssid: %s, name: %s",wifiSSID,name);
        if (wifiSSID != null && wifiSSID.equals(name)) {
            if (!Build.PRODUCT.startsWith("YF")) {
                androidConnectSuccessCount++;
            }
            mHandler.postDelayed(() -> onAndroidConnected(context), 2000);
            return;
        }
        BoardFactory.create(Build.PRODUCT).connectWiFi(context,name,passwd,hidden);
        connectTimeOutTask = new TimeOutTask(context.getString(R.string.text_android_wifi_connect_time_out));
        mHandler.postDelayed(connectTimeOutTask, 30_000);
    }

    @Override
    public void onAndroidConnected(Context context) {
        if (currentPhase != PHASE_ANDROID_CONNECTING) return;
        androidConnectSuccessCount++;
        if (androidConnectSuccessCount >= BoardConstants.WIFI_CONNECT_THRESHOLD) {
            mHandler.removeCallbacks(connectTimeOutTask);
            connectTimeOutTask = new TimeOutTask(context.getString(R.string.text_ros_wifi_connect_time_out));
            mHandler.postDelayed(connectTimeOutTask, 30_000);
            currentPhase = PHASE_ROS_CONNECTING;
            ros.connectROSWifi(wifiName,wifiPass);
        }
    }

    @Override
    public void onWiFiEvent(Context context, boolean isConnect) {
        mHandler.removeCallbacks(connectTimeOutTask);
        currentPhase = PHASE_INIT;
        androidConnectSuccessCount = 0;
        if (isConnect) {
            ros.getHostIP();
            view.onConnectSuccess();
            return;
        }
        view.onConnectFailed();
    }

    private class TimeOutTask implements Runnable {
        private final String prompt;

        public TimeOutTask(String prompt) {
            this.prompt = prompt;
        }

        @Override
        public void run() {
            currentPhase = PHASE_INIT;
            androidConnectSuccessCount = 0;
            view.showConnectTimeOutView(prompt);
        }
    }
}
