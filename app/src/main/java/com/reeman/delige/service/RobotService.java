package com.reeman.delige.service;


import static com.reeman.delige.base.BaseApplication.activityStack;
import static com.reeman.delige.base.BaseApplication.mApp;
import static com.reeman.delige.base.BaseApplication.mRobotInfo;
import static com.reeman.delige.base.BaseApplication.navigationMode;
import static com.reeman.delige.base.BaseApplication.ros;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.reeman.delige.activities.WiFiConnectActivity;
import com.reeman.delige.base.BaseApplication;
import com.reeman.delige.dispatch.mqtt.MqttClient;
import com.reeman.delige.navigation.Mode;
import com.reeman.delige.request.ServiceFactory;
import com.reeman.delige.request.model.StateRecord;
import com.reeman.delige.request.url.API;
import com.reeman.delige.event.Event;
import com.reeman.delige.state.RobotInfo;
import com.reeman.delige.utils.WIFIUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class RobotService extends Service {

    Runnable task = () -> {
        String hostname = Event.getOnHostnameEvent().hostname;
        if (TextUtils.isEmpty(hostname)) return;
        try {
            int chargePlug = 0;
            if (ros.isCharging()) {
                chargePlug = 4;
            }
            StateRecord record = new StateRecord(0,
                    ros.getLevel(),
                    chargePlug,
                    ros.getEmergencyStop(),
                    ros.getState().ordinal(),
                    Event.getOnHflsVersionEvent().softVersion,
                    BaseApplication.appVersion,
                    1,
                    "",
                    BaseApplication.macAddress,
                    System.currentTimeMillis(),
                    "v1.1",
                    "");
            Log.w("上传状态：", record.toString());
            ServiceFactory.getRobotService().heartbeat(API.heartbeatAPI(hostname), record).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    };


    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = createNotification();
            startForeground(1001, notification);
        }
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(task, 10, 15, TimeUnit.SECONDS);
        registerNetworkReceiver();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "robot server";
            String channelName = "robot server";
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "robot server")
                .setContentTitle("robot server")
                .setContentText("Service is running")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerNetworkReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        WifiBroadcastReceiver receiver = new WifiBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
    }

    public static class WifiBroadcastReceiver extends BroadcastReceiver {

        private int androidWifiConnectCount = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    RobotInfo robotInfo = RobotInfo.INSTANCE;
                    if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                        Timber.w("DISCONNECTED");
                        robotInfo.setNetworkConnected(false);
                    } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                        Timber.w("CONNECTED");
                        robotInfo.setNetworkConnected(true);
                        if (++androidWifiConnectCount > 1) {
                            androidWifiConnectCount = 0;
                            if (navigationMode == Mode.FIX_ROUTE && !(activityStack.get(activityStack.size() - 1) instanceof WiFiConnectActivity)) {
                                MqttClient mqttClient = MqttClient.getInstance();
                                Timber.w("isConnected : %s , isCanReconnect : %s",mqttClient.isConnected(),mqttClient.isCanReconnect());
                                if (!mqttClient.isConnected()) {
                                    Observable<Integer> observable;
                                    if (mqttClient.isCanReconnect()) {
                                        observable = mqttClient.reconnect();
                                    } else {
                                        observable = mqttClient.connect(mRobotInfo.getHostname());
                                    }
                                    observable.subscribeOn(Schedulers.io())
                                            .observeOn(Schedulers.io())
                                            .doOnNext(v -> Timber.w("mqtt connected"))
                                            .subscribeOn(Schedulers.io())
                                            .flatMap(v -> mqttClient.subscribeToTopic())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(new Observer<Boolean>() {
                                                @Override
                                                public void onSubscribe(@NonNull Disposable d) {

                                                }

                                                @Override
                                                public void onNext(@NonNull Boolean aBoolean) {
                                                    EventBus.getDefault().post(Event.setMqttConnectEvent(true));
                                                }

                                                @Override
                                                public void onError(@NonNull Throwable e) {
                                                    EventBus.getDefault().post(Event.setMqttConnectEvent(false));
                                                    if (!mqttClient.isConnected()) {
                                                        Timber.w(e, "建立mqtt连接失败");
                                                    } else {
                                                        Timber.w(e, "订阅失败");
                                                    }
                                                }

                                                @Override
                                                public void onComplete() {

                                                }
                                            });
                                }
                            }
                        }
                    }
                    break;
                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                    if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                        Timber.w("WIFI_STATE_DISABLED");
                    }
                    break;
            }
        }
    }
}
