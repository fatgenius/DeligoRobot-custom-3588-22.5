package com.reeman.delige.base;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;


import com.elvishew.xlog.XLog;
import com.reeman.delige.BuildConfig;
import com.reeman.delige.activities.CrashActivity;
import com.reeman.delige.constants.Constants;
import com.reeman.delige.dispatch.DispatchState;
import com.reeman.delige.dispatch.model.RobotInfo;
import com.reeman.delige.dispatch.mqtt.MqttClient;
import com.reeman.delige.dispatch.service.DispatchService;
import com.reeman.delige.event.Event;
import com.reeman.delige.light.LightController;
import com.reeman.delige.navigation.Mode;
import com.reeman.delige.navigation.ROSController;
import com.reeman.delige.receiver.RobotReceiver;
import com.reeman.delige.repository.DbRepository;
import com.reeman.delige.repository.db.AppDataBase;
import com.reeman.delige.request.model.PointInfo;
import com.reeman.delige.service.RobotService;
import com.reeman.delige.utils.PackageUtils;
import com.reeman.delige.utils.ScreenUtils;
import com.reeman.delige.utils.SpManager;
import com.reeman.delige.utils.ToastUtils;
import com.reeman.delige.utils.WIFIUtils;
import com.reeman.log.FileLoggingTree;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import timber.log.Timber;
import xcrash.ICrashCallback;
import xcrash.XCrash;

public class BaseApplication extends Application {

    public static List<Activity> activityStack = new ArrayList<>();
    public static BaseApplication mApp;
    public static String appVersion;
    public static String macAddress;
    public static DbRepository dbRepository;
    private static LinkedHashSet<String> callingQueue;
    public static Mode navigationMode = Mode.AUTO_ROUTE;
    public static boolean shouldRefreshPoints = false;
    public static boolean isFirstEnter = true;
    public static ROSController ros;
    public static volatile RobotInfo mRobotInfo = new RobotInfo();
    public static volatile DispatchState dispatchState = DispatchState.INIT;
    public static volatile ConcurrentLinkedQueue<PointInfo> pointInfoQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;

        appVersion = PackageUtils.getVersion(this);

        macAddress = WIFIUtils.getMacAddress(this);
        XLog.init();
        //android-target-tooltip也使用Timber,通过关键字区分
        List<String> blocks = Arrays.asList("offsetBy", "viewContainer", "contentView", "closePolicy", "findPosition", "anchorPosition",
                "centerPosition", "displayFrame", "displayFrame", "arrowPosition", "contentPosition", "setAnchor", "onBoundsChange",
                "globalVisibleRect", "calculatePath", "tmpPoint", "rawPoint");
        FileLoggingTree fileLoggingTree = new FileLoggingTree(BuildConfig.DEBUG ? Log.VERBOSE : Log.WARN, BuildConfig.DEBUG, Environment.getExternalStorageDirectory().getPath(), BuildConfig.APP_LOG_DIR, Arrays.asList(BuildConfig.APP_LOG_DIR, BuildConfig.CRASH_LOG_DIR, BuildConfig.WHEEL_INFO_DIR, BuildConfig.BATTERY_REPORT_DIR, com.reeman.serialport.BuildConfig.LOG_POWER_BOARD));
        fileLoggingTree.setBlackedMessages(new LinkedHashSet<>(blocks));
        Timber.plant(fileLoggingTree);
        XCrash.init(this, new XCrash.InitParameters().setAppVersion(BuildConfig.VERSION_NAME).setJavaCallback(callback).setNativeCallback(callback).setAnrCallback(callback));

        try {
            LightController.getInstance().start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //sp存储工具类
        SpManager.init(this, Constants.KEY_DELIGO_SP_NAME);

        //吐司工具类
        ToastUtils.init(this);

        dbRepository = DbRepository.getInstance(AppDataBase.getInstance(this));

        registerReceiver(new RobotReceiver(), new RobotReceiver.RobotIntentFilter());

        startService(new Intent(this, RobotService.class));

//        startService(new Intent(this, DispatchService.class));

        String mode = SpManager.getInstance().getString(Constants.KEY_NAVIGATION_MODE, Mode.AUTO_ROUTE.name());
        navigationMode = mode.equals(Mode.AUTO_ROUTE.name()) ? Mode.AUTO_ROUTE : Mode.FIX_ROUTE;
    }

    ICrashCallback callback = new ICrashCallback() {
        @Override
        public void onCrash(String logPath, String emergency) {
            String content = null;
            try {
                File file = new File(logPath);
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                content = new String(data, StandardCharsets.UTF_8);
                Timber.tag(BuildConfig.CRASH_LOG_DIR).e("Uncaught exception:\n %s", content);
                if (ros != null) {
                    isFirstEnter = true;
                    ros.unInit();
                    ros = null;
                }
                MqttClient mqttClient = MqttClient.getInstance();
                if (mqttClient.isConnected()){
                    mqttClient.disconnect();
                }
                file.delete();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Intent intent = new Intent(mApp, CrashActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                if (content != null && content.length() > 0 && content.contains("logcat:")) {
                    Event.OnHostnameEvent hostnameEvent = Event.getOnHostnameEvent();
                    if (hostnameEvent != null && hostnameEvent.hostname != null) {
                        intent.putExtra("hostname", hostnameEvent.hostname);
                    }
                    intent.putExtra("stackTrace", content.split("logcat:")[0]);
                }
                mApp.startActivity(intent);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    };

    public void exit() {
        if (ros != null) {
            ros.unInit();
            ros = null;
        }
        MqttClient mqttClient = MqttClient.getInstance();
        if (mqttClient.isConnected()){
            mqttClient.disconnect();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public static LinkedHashSet<String> getCallingQueue() {
        return callingQueue;
    }

    public static void addToCallingQueue(String task) {
        if (callingQueue == null) {
            callingQueue = new LinkedHashSet<>();
        }
        callingQueue.add(task);
    }

}
