package com.reeman.delige.dispatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.GsonBuilder
import com.reeman.delige.base.BaseApplication.dispatchState
import com.reeman.delige.base.BaseApplication.mRobotInfo
import com.reeman.delige.base.BaseApplication.navigationMode
import com.reeman.delige.base.BaseApplication.pointInfoQueue
import com.reeman.delige.base.BaseApplication.ros
import com.reeman.delige.constants.State
import com.reeman.delige.dispatch.DispatchManager
import com.reeman.delige.dispatch.DispatchState
import com.reeman.delige.dispatch.model.RobotInfo
import com.reeman.delige.dispatch.mqtt.MqttClient
import com.reeman.delige.dispatch.util.DispatchUtil
import com.reeman.delige.dispatch.util.PointUtil
import com.reeman.delige.event.Event
import com.reeman.delige.navigation.Mode
import com.reeman.delige.request.model.PathPoint
import com.reeman.delige.utils.DestHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DispatchService : Service() {

    private var espPublishCount = 0

    private var lastRobotInfo: RobotInfo? = null

    private val gson =
        GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()


    private var mqttClient: MqttClient? = null

    private val handler: Handler = Handler()

    private var count = 0

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public fun onRobotInfo(robotInfo: RobotInfo) {
        val hostname = mRobotInfo.hostname
        val split = hostname.split("-")
        if (robotInfo.hostname != split[split.size-1]) {
            val lastReceiveTime = DispatchUtil.getLastReceiveTime()
            val currentTimeMillis = System.currentTimeMillis()
            val l = currentTimeMillis - lastReceiveTime
            if (l > 350) {
                Timber.w("outTime : %s", l)
            }
//            handler.postDelayed({
//                publish()
//            } else {
//            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ publish() }, 75)
//            }
                DispatchUtil.addToRobotList(robotInfo.hostname)
                Timber.d("robot info : %s", gson.toJson(robotInfo))
                DispatchUtil.updateRobotList(robotInfo)
//            }, 50)

        }
    }

    private fun publish() {
        val take = pointInfoQueue.take(6).map { it.name }
        mRobotInfo.pointQueue.clear()
        mRobotInfo.pointQueue.addAll(take)
        mRobotInfo.dispatchState = dispatchState
        espPublishCount = 0
        var lineSpeed = 0.0
        if (ros.state == State.PAUSE || ros.state == State.IDLE || ros.state == State.CHARGING || dispatchState != DispatchState.INIT) {
            lineSpeed = 0.0
        } else {
            if (take.size >= 2) {
                val pathPoints = DestHelper.getInstance().points as List<PathPoint>
                val firstPoint =
                    pathPoints.firstOrNull { pathPoint -> pathPoint.name == take[0] }
                val secondPoint =
                    pathPoints.firstOrNull { pathPoint -> pathPoint.name == take[1] }
                if (firstPoint != null && secondPoint != null) {
                    lineSpeed = PointUtil.calculateRadian(
                        firstPoint.xPosition,
                        firstPoint.yPosition,
                        secondPoint.xPosition,
                        secondPoint.yPosition
                    )
                } else {
                    lineSpeed = 0.0
                }
            } else {
                lineSpeed = 0.0
            }
        }
        val currentPosition = mRobotInfo.currentPosition
        currentPosition[2] = lineSpeed
        mRobotInfo.currentPositionWithLineSpeed =
            currentPosition.map { (it * 100).toInt() }.toIntArray()
        if (ros.isDocking) {
            mRobotInfo.dispatchState = DispatchState.IGNORE
        }
        lastRobotInfo = mRobotInfo.copy()
//        val currentTimeMillis = System.currentTimeMillis()
        DispatchManager.publishMessage(mRobotInfo)
//        val afterPublishTimeMillis = System.currentTimeMillis()
//        if (afterPublishTimeMillis - currentTimeMillis > 50) {
//            Timber.w("推送耗时 : %s", afterPublishTimeMillis - currentTimeMillis)
//        }
        Timber.w("本机推送 : ${gson.toJson(mRobotInfo)}")
    }


    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        val notificationId = 1001
        EventBus.getDefault().register(this)
        startForeground(notificationId, notification)
        val newScheduledThreadPool = Executors.newScheduledThreadPool(2)
        newScheduledThreadPool.scheduleWithFixedDelay(
            {
                try {
                    if (
                        !DispatchManager.isStarted()
                        || navigationMode != Mode.FIX_ROUTE
                        || mRobotInfo.hostname.isEmpty()
                    ) return@scheduleWithFixedDelay
                    val lastReceiveTime = DispatchUtil.getLastReceiveTime()
                    if (lastReceiveTime != 0L) return@scheduleWithFixedDelay
//                    publish()

//                    }
                } catch (e: Exception) {
                }
            },
            100, 300, TimeUnit.MILLISECONDS
        )
        newScheduledThreadPool
            .scheduleWithFixedDelay({
                try {
                    if (navigationMode != Mode.FIX_ROUTE
                        || dispatchState == DispatchState.IGNORE
                        || ros.state == State.IDLE
                        || ros.state == State.CHARGING
                        || ros.state == State.PAUSE
                    ) return@scheduleWithFixedDelay
                    val robotList = DispatchUtil.getRobotList()
                    Timber.w("机器信息 : ${gson.toJson(robotList)}")
                    if (robotList.isEmpty()) {
                        if (dispatchState == DispatchState.WAITING) {
                            Timber.w("机器全部离线,本机暂停中,恢复任务")
                            EventBus.getDefault().post(Event.getOnDispatchResumeEvent())
                        }
                        return@scheduleWithFixedDelay
                    }
                    if (dispatchState == DispatchState.INIT
                        && ros.state != State.IDLE
                        && ros.state != State.PAUSE
                        && ros.state != State.CHARGING
                    ) {
                        if (DispatchUtil.isFirstEnterSpecialArea(robotList)) {
                            val pathList = pointInfoQueue.take(6).map { it.name }
                            var otherOnFront = false
                            robotList.forEach { robotInfo ->
                                if (robotInfo.dispatchState != DispatchState.IGNORE) {
                                    val robotPathPoints = robotInfo.pointQueue.take(4)
                                        .filter { it != DestHelper.getInstance().chargePoint }
                                    val intersect = pathList.intersect(robotPathPoints.toSet())
                                    if (intersect.isNotEmpty()
                                        && pathList[0] !in intersect
                                    ) {
                                        if (robotPathPoints[0] in intersect) {
                                            otherOnFront = true
                                            Timber.w("机器${robotInfo.hostname}在本机前方")
                                        }
                                    }
                                }
                            }
                            if (otherOnFront) {
                                EventBus.getDefault().post(Event.getOnDispatchPauseEvent())
                            }
                            return@scheduleWithFixedDelay
                        }
                        if (DispatchUtil.checkPause(
                                pointInfoQueue.take(6).map { it.name },
                                robotList,
                                mRobotInfo.enterSpecialAreaSequence != 0
                            )
                        ) {
                            EventBus.getDefault().post(Event.getOnDispatchPauseEvent())
                        }

                        return@scheduleWithFixedDelay
                    }
                    if (dispatchState == DispatchState.WAITING) {
                        val currentPathPoints = pointInfoQueue.take(7).map { it.name }
                        if (mRobotInfo.enterSpecialAreaSequence != 0 && !DispatchUtil.isFirstEnterSpecialArea(
                                robotList
                            )
                        ) {
                            return@scheduleWithFixedDelay
                        }
                        if (DispatchUtil.checkRoute(
                                currentPathPoints,
                                robotList
                            )
                        ) return@scheduleWithFixedDelay
                        if (
                            DispatchUtil.isCloseToTargetPoint()
                            && DispatchUtil.isTargetPointOccupied()
                        ) return@scheduleWithFixedDelay
                        Timber.w("机器与其他机器无路线交叉,恢复任务")
                        EventBus.getDefault().post(Event.getOnDispatchResumeEvent())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Timber.e(e, "调度服务报错")
                }
            }, 0L, 500L, TimeUnit.MILLISECONDS)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "your_channel_id"
            val channelName = "Your Channel Name"
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, "your_channel_id")
            .setContentTitle("Dispatch")
            .setContentText("Dispatch service is running")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return builder.build()
    }
}