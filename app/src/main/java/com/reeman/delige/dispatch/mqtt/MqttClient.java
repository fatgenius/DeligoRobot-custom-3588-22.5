package com.reeman.delige.dispatch.mqtt;


import static com.reeman.delige.base.BaseApplication.activityStack;
import static com.reeman.delige.base.BaseApplication.mApp;

import android.annotation.SuppressLint;
import android.os.SystemClock;

import com.google.gson.Gson;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.reeman.delige.activities.WiFiConnectActivity;
import com.reeman.delige.dispatch.model.RobotInfo;
import com.reeman.delige.dispatch.util.DispatchUtil;
import com.reeman.delige.event.Event;
import com.reeman.delige.utils.WIFIUtils;

import org.greenrobot.eventbus.EventBus;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;


public class MqttClient {

    private static MqttClient instance;

    private final static String TAG = "DispatchMqttClient";

    private final String host = "mqtt.rmbot.cn";
//    private final String host = "mqtt2.rmbot.cn";

    private String clientId;

    private String hostname;
    private final String username;
    private final String password;
    private Mqtt3BlockingClient client;
    private String topic;
    private int reconnectCount;
    private boolean isConnected = false;

    private boolean canReconnect = false;

    private final Gson gson = new Gson();

    public boolean isCanReconnect() {
        return canReconnect;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public static MqttClient getInstance() {
        if (instance == null) {
            synchronized (MqttClient.class) {
                if (instance == null) {
                    instance = new MqttClient();
                }
            }
        }
        return instance;
    }

    public MqttClient() {
        username = "AGV";
        password = "no1robot";
    }

    public Observable<Integer> connect(String hostname) {
        this.hostname = hostname;
        this.clientId = hostname + "_" + SystemClock.uptimeMillis();
        this.topic = Topic.dispatchTopicSub(hostname);
        return Observable.create(emitter -> {
            reconnectCount = 0;
            Mqtt3Connect connect = Mqtt3Connect.builder()
                    .keepAlive(10)
                    .build();
            client = com.hivemq.client.mqtt.MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(host)
                    .serverPort(1883)
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes())
                    .applySimpleAuth()
                    .addConnectedListener(context -> {
                        EventBus.getDefault().post(Event.setMqttConnectEvent(true));
                        isConnected = true;
                        if (reconnectCount != 0) {
                            Timber.tag(TAG).w("重新连接成功");
                            reconnectCount = 0;
                            return;
                        }
                        Timber.tag(TAG).w("连接成功");
                        emitter.onNext(0);
                    })
                    .addDisconnectedListener(context -> {
                        EventBus.getDefault().post(Event.setMqttConnectEvent(false));
                        isConnected = false;
                        Timber.w(context.getCause(), "连接断开");
                        if (!com.reeman.delige.state.RobotInfo.INSTANCE.isNetworkConnected() || activityStack.get(activityStack.size() - 1) instanceof WiFiConnectActivity)
                            return;
                        if (reconnectCount < 3) {
                            Timber.tag(TAG).w("断开连接,正在重连 : %s", reconnectCount);
                            context.getReconnector()
                                    .reconnect(true)
                                    .delay(++reconnectCount * 2L, TimeUnit.SECONDS);
                            return;
                        }
                        // TODO: 2024/1/5 throw exception

                        Timber.tag(TAG).w("断开连接,超过最大重连次数");
                    })
                    .buildBlocking();
            client.connect(connect);
            isConnected = true;
            canReconnect = true;
        });
    }

    public Observable<Integer> reconnect() {
        Timber.w("重新连接mqtt");
        reconnectCount = 0;
        return Observable.create(emitter -> {
            client.connect();
        });
    }

    public Observable<Boolean> publish(String topic, String msg) {
        return Observable.create((ObservableOnSubscribe<Boolean>) emitter -> {
                    client.publishWith().topic(topic).qos(MqttQos.AT_MOST_ONCE).payload(msg.getBytes()).send();
                    emitter.onNext(true);
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressLint("CheckResult")
    public void publishDispatchTopic(String hostname, String payload) {
        if (isConnected()) {
            publish(Topic.dispatchTopicPub(hostname), payload)
                    .subscribe(aBoolean -> Timber.d("publish success"), throwable -> Timber.w(throwable, "发布机器人信息失败 %s", payload));
        } else {
            Timber.w("mqtt未连接 %s", payload);
        }
    }

    @SuppressLint("CheckResult")
    public Observable<Boolean> subscribeToTopic() {
        return Observable.create(emitter -> {
            try {
                Mqtt3AsyncClient mqtt3AsyncClient = client.toAsync();
                mqtt3AsyncClient.unsubscribeWith().topicFilter(topic).send();
            } catch (Exception e) {
                e.printStackTrace();
            }
            client.toRx().subscribePublishesWith()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_MOST_ONCE)
                    .applySubscribe()
                    .doOnSingle(mqtt3SubAck -> emitter.onNext(true))
                    .subscribe(mqtt5Publish -> {
                        String topicReceive = mqtt5Publish.getTopic().toString();
                        String receiveHostname = topicReceive.replace("reeman/dispatch/", "");
                        if (receiveHostname.equals(hostname) || !DispatchUtil.Companion.isRobotInCacheList(receiveHostname)) return;
                        String payload = new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                        Timber.tag("subscribe").v("topic %s , payload %s", topicReceive, payload);
                        EventBus.getDefault().post(gson.fromJson(payload, RobotInfo.class));
                    }, throwable -> {
                        if (client == null || !isConnected || !com.reeman.delige.state.RobotInfo.INSTANCE.isNetworkConnected())
                            return;
                        Timber.w(throwable, "mqtt订阅异常");
                        emitter.onError(throwable);
                    });
        });
    }

    public void disconnect() {
        canReconnect = false;
        isConnected = false;
        if (client != null) {
            try {
                Mqtt3AsyncClient mqtt3AsyncClient = client.toAsync();
                mqtt3AsyncClient.unsubscribeWith().topicFilter(topic).send();
                mqtt3AsyncClient.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            client = null;
        }
    }

}
