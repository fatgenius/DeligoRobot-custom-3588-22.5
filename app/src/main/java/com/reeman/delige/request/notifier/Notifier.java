package com.reeman.delige.request.notifier;

import com.google.gson.Gson;
import com.reeman.delige.request.ServiceFactory;
import com.reeman.delige.request.model.Msg;
import com.reeman.delige.request.url.API;
import com.reeman.delige.request.service.RobotService;
import com.reeman.delige.utils.AESUtil;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class Notifier {

    public static final String key = "a123456";

    public static void notify(Msg msg) {
        try {
            RobotService robotService = ServiceFactory.getRobotService();
            Map<String, String> map = new HashMap<>();
            map.put("device", AESUtil.encrypt(key, new Gson().toJson(msg)));
            map.put("key", key);
            robotService.notify(API.notifyAPI(), map).subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(map1 -> {

                    }, throwable -> {
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Observable<Map<String, Object>> notify2(Msg msg) {
        try {
            RobotService robotService = ServiceFactory.getRobotService();
            Map<String, String> map = new HashMap<>();
            map.put("device", AESUtil.encrypt(key, new Gson().toJson(msg)));
            map.put("key", key);
            return robotService.notify(API.notifyAPI(), map).subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
