package com.reeman.delige.dispatch.util

import com.reeman.delige.base.BaseApplication.*
import com.reeman.delige.constants.State
import com.reeman.delige.dispatch.DispatchState
import com.reeman.delige.dispatch.model.RobotInfo
import com.reeman.delige.event.Event
import com.reeman.delige.request.model.PathPoint
import com.reeman.delige.request.model.PointInfo
import com.reeman.delige.utils.DestHelper
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DispatchUtil {
    companion object {

        private val robotMap = ConcurrentHashMap<String, RobotInfo>()

        private val robotList = mutableListOf<String>()

        fun addToRobotList(hostname: String) {
            if (robotList.contains(hostname)) return
            robotList.add(hostname)
        }

        fun isRobotInCacheList(hostname: String): Boolean {
            return robotList.contains(hostname)
        }

        /**
         * 判断距离目标点是否小于2.5米
         */
        fun isCloseToTargetPoint(): Boolean {
//            if (pointInfoQueue.size in 2..4) {
//                return true
//            }
            return false
        }

        /**
         * 目标点被占
         */
        fun isTargetPointOccupied(): Boolean {
            val robotList = getRobotList()
            if (robotList.isNotEmpty()) {
                val firstOrNull =
                    robotList.firstOrNull { robotInfo ->
                        robotInfo.pointQueue.firstOrNull() == ros.lastNavPoint
                                && robotInfo.dispatchState != DispatchState.IGNORE
                                && ros.lastNavPoint != DestHelper.getInstance().chargePoint
                    }
                if (firstOrNull != null) {
                    Timber.w("目标点被占 :" + firstOrNull.hostname)
                    return true
                }
            }
            return false
        }

        /**
         * 判断是否可以暂停
         */
        fun canPause(): Boolean {
            if (dispatchState == DispatchState.WAITING) {
                Timber.w("已触发排队逻辑")
                return false
            }
            if (ros.state == State.PAUSE || ros.state == State.CHARGING || ros.state == State.IDLE) {
                Timber.w("机器不在导航状态,不处理暂停")
                return false
            }
            val pathPoints = DestHelper.getInstance().pathPoints as List<PathPoint>
            val firstOrNull =
                pathPoints.firstOrNull { pathPoint -> pathPoint.name == ros.lastNavPoint }
            if (firstOrNull != null) {
                val calculateDistance = PointUtil.calculateDistance(
                    firstOrNull.xPosition,
                    firstOrNull.yPosition,
                    Event.getOnPositionEvent().position[0],
                    Event.getOnPositionEvent().position[1]
                )
                if (calculateDistance < 0.1) {
                    Timber.w("距离目标点${calculateDistance},不处理暂停")
                    return false
                }
            }
            return true
        }

        fun isFirstEnterSpecialArea(robotList: List<RobotInfo>): Boolean {
            if (mRobotInfo.enterSpecialAreaSequence != 0) {
                val robotInfoListEnterSALater = robotList.filter { robotInfo ->
                    robotInfo.currentSpecialArea == mRobotInfo.currentSpecialArea
                            && robotInfo.enterSpecialAreaSequence <= mRobotInfo.enterSpecialAreaSequence
                            && robotInfo.dispatchState != DispatchState.IGNORE
                }
                if (robotInfoListEnterSALater.isEmpty()) {
                    Timber.w("区域内只有本机")
                    return true
                }
                val firstEnterSA =
                    robotInfoListEnterSALater.minBy { robotInfo -> robotInfo.enterSpecialAreaSequence }
                if (firstEnterSA.enterSpecialAreaSequence < mRobotInfo.enterSpecialAreaSequence) {
                    return false
                }
                if (firstEnterSA.enterSpecialAreaSequence == mRobotInfo.enterSpecialAreaSequence) {
                    val filterHostname =
                        robotInfoListEnterSALater.filter { robotInfo -> robotInfo.enterSpecialAreaSequence == mRobotInfo.enterSpecialAreaSequence }
                            .map { it.hostname }
                    val hostnameList = arrayListOf(mRobotInfo.hostname)
                    hostnameList.addAll(filterHostname)
                    val sorted = hostnameList.sorted()
                    Timber.w("区域内多台机器,并且优先级和本机一样,按名称排序结果 : $sorted")
                    if (sorted[0] == mRobotInfo.hostname) {
                        return true
                    }
                    return false
                }
            }
            return false
        }

        fun checkPause(
            pathList: List<String>,
            robotList: List<RobotInfo>,
            checkWaiting: Boolean
        ): Boolean {
            val hostnameList = arrayListOf(mRobotInfo.hostname)
            var otherOnFront = false
            val pathListNew = pathList.filter { it != DestHelper.getInstance().chargePoint }
            robotList.forEach { robotInfo ->
                if (robotInfo.dispatchState != DispatchState.IGNORE) {
                    var robotPathPoints = robotInfo.pointQueue.take(4)
                        .filter { it != DestHelper.getInstance().chargePoint }
                    val intersect = pathListNew.intersect(robotPathPoints.toSet())
                    if (intersect.isNotEmpty()
                        && pathListNew[0] !in intersect
                    ) {
                        if (robotPathPoints[0] in intersect) {
                            otherOnFront = true
                            Timber.w("机器${robotInfo.hostname}在本机前方")
                        } else {
                            if (
                                robotInfo.dispatchState == DispatchState.INIT
                            ) {
                                hostnameList.add(robotInfo.hostname)
                                Timber.w("机器与${robotInfo.hostname}存在路线交叉,暂停任务")
                            }
                        }
                    }
                }
            }
            if (otherOnFront) {
                return true
            }

            if (hostnameList.size > 1) {
                if (checkWaiting) {
                    return true
                }
                val sorted = hostnameList.sorted()
                Timber.w("排序后的机器列表 : $sorted")
                if (sorted[0] == mRobotInfo.hostname)
                    return true
            }
            return false
        }

        /**
         * 更新本机特殊区
         */
        fun updateSpecialArea(name: String, mRobotInfo: RobotInfo): Boolean {
            mRobotInfo.currentSpecialArea = name

//            val event = Event.getOnSpecialPlanEvent()
//            Timber.w("特殊区列表 : $event")
//            val isCurrentSpecialArea = event.rooms.isNotEmpty() && event.rooms[0].name == name
//            if (isCurrentSpecialArea) {
            if (name.isNotBlank()) {
                Timber.w("进入特殊区 : $name")
                val robotInfoList =
                    robotMap.filterValues { robotInfo -> robotInfo.enterSpecialAreaSequence > 0 }
                        .map { it.value }
                if (robotInfoList.isEmpty()) {
                    mRobotInfo.enterSpecialAreaSequence = 1
                } else {
                    val maxBy =
                        robotInfoList.maxBy { robotInfo -> robotInfo.enterSpecialAreaSequence }
                    mRobotInfo.enterSpecialAreaSequence = maxBy.enterSpecialAreaSequence + 1
                }
                if (ros.state != State.CHARGING) {
                    val robotList = getRobotList()
                    if (robotList.isEmpty()) return false
                    Timber.w("机器列表 : $robotList")
                    val filter =
                        robotList.filter { robotInfo -> robotInfo.currentSpecialArea == name && robotInfo.dispatchState != DispatchState.IGNORE }
                    if (filter.isNotEmpty()) {
                        Timber.w("特殊区被占 : $filter")
                        return true
                    }
                }
            } else {
//                Event.getOnSpecialPlanEvent().rooms.removeAt(0)
                mRobotInfo.enterSpecialAreaSequence = 0
            }
//            } else {
//                mRobotInfo.enterSpecialAreaSequence = 0
//            }
            return false
        }

        fun checkRoute(
            currentPathPoints: List<String>,
            robotList: List<RobotInfo>
        ): Boolean {
            var hasCross = false
            val currentPathPointsNew =
                currentPathPoints.filter { it != DestHelper.getInstance().chargePoint }
            robotList.forEach { robotInfo ->
                if (robotInfo.dispatchState != DispatchState.IGNORE) {
                    val robotPathPoints = robotInfo.pointQueue.take(5)
                        .filter { it != DestHelper.getInstance().chargePoint }
                    val intersect = currentPathPointsNew.intersect(robotPathPoints.toSet())
                    if (intersect.isNotEmpty()
                    ) {
                        if (robotPathPoints[0] in intersect) {
                            Timber.d("机器${robotInfo.hostname}在本机的前方,无法恢复任务")
                            hasCross = true
                        } else if (currentPathPointsNew[0] !in intersect && robotInfo.dispatchState != DispatchState.WAITING) {
                            Timber.d("机器与${robotInfo.hostname}存在路线交叉,另一台机器不处于等待状态,无法恢复任务")
                            hasCross = true
                        }
                    }
                }
            }
            return hasCross
        }

        /**
         * 更新本机路线
         */
        fun updateRoutePoint(points: List<String>, pointQueue: ConcurrentLinkedQueue<PointInfo>) {
            pointQueue.clear()
            var lastPathWidth = 1.0
            for (i in 0 until points.size - 1) {
                val currentPoint = points[i]
                val nextPoint = points[i + 1]
                val pathList = DestHelper.getInstance().pathList
                val firstOrNull =
                    pathList.firstOrNull { path -> path.sourcePoint == currentPoint && path.destinationPoint == nextPoint }
                if (firstOrNull == null) {
                    pointQueue.offer(PointInfo(points[i], 1.0))
                } else {
                    lastPathWidth = firstOrNull.pathWidth
                    pointQueue.offer(PointInfo(points[i], firstOrNull.pathWidth))
                }
            }
            pointQueue.offer(PointInfo(points[points.size - 1], lastPathWidth))
        }

        /**
         * 更新其他机器
         */
        fun updateRobotList(robotInfo: RobotInfo) {
            val hostname = Event.getOnHostnameEvent().hostname
            val split = hostname.split("-")
            if (robotInfo.hostname != split[split.size-1]) {
                val get = robotMap[robotInfo.hostname]
                /*
                使用mqtt时需根据发送时间判断是否是重复/过时数据
//                if (get != null) {
//                    if (get.updateTime > robotInfo.updateTime) {
//                        Timber.w("收到过时消息 ${get.hostname} 时间差 : ${get.updateTime - robotInfo.updateTime}")
//                        return
//                    }
//                    if (get.updateTime == robotInfo.updateTime)
//                        return
//                }

                 */

                robotInfo.receiveTime = System.currentTimeMillis()
                if (get != null && robotInfo.receiveTime - get.receiveTime > 500) {
                    Timber.w("${get.hostname} 消息超时 : ${robotInfo.receiveTime - get.receiveTime}")
                }
                robotMap[robotInfo.hostname] = robotInfo
            }
        }

        fun getLastReceiveTime(): Long {
            if (robotMap.isEmpty()) return 0
            return robotMap.values.maxBy { it.receiveTime }.receiveTime
        }

        /**
         * 更新本机路线
         */
        fun updateCurrentPointAndRoute(
            position: DoubleArray,
            pointQueue: ConcurrentLinkedQueue<PointInfo>
        ): Double {
            val takeFront2 = pointQueue.take(2).map { it.name }
            var width = -1.0
            try {
//                val takeFront2 = pointNameList
                val pathPointList = DestHelper.getInstance().pathPoints as List<PathPoint>
                if (pathPointList.isNotEmpty()) {
                    val pointList = pathPointList.filter { pathPoint ->
                        pathPoint.name in takeFront2
                    }
                    var minDistance = Double.MAX_VALUE
                    val minDistancePoint = pointList.minByOrNull { pathPoint ->
                        val x = pathPoint.xPosition
                        val y = pathPoint.yPosition
                        val distance = PointUtil.calculateDistance(
                            x,
                            y,
                            position[0],
                            position[1]
                        )
                        if (distance < minDistance) {
                            minDistance = distance
                        }
                        distance
                    }
                    if (minDistance > 1.5 && takeFront2.size > 1) {
                        Timber.w("最近的点距离 : $minDistance , 移除")
                        val poll = pointQueue.poll()
                        width = poll!!.width
                    } else {
                        if (minDistancePoint != null) {
                            try {
                                if (takeFront2[0] != minDistancePoint.name
                                    && takeFront2.contains(minDistancePoint.name)
                                ) {
                                    val iterator = pointQueue.iterator()
                                    while (iterator.hasNext()) {
                                        val next = iterator.next()
                                        if (next.name == minDistancePoint.name) {
                                            width = next.width
                                            break
                                        }
                                        iterator.remove()
                                    }
                                    Timber.w("更新路线:${pointQueue}")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Timber.e("更新路线异常: ${e.message}")
                            }
                        } else {
                            Timber.e("找不到距离最近的点")
                            pointQueue.clear()
                        }
                    }
                } else {
                    Timber.w("本地缓存路线为空")
                }
            } catch (e: Exception) {
                Timber.e("更新本机当前路线失败", e)
            }
            return width
        }

        /**
         * 查询未过期的机器
         */
        fun getRobotList(): ArrayList<RobotInfo> {
            var robotInfoList = arrayListOf<RobotInfo>()
            if (robotMap.isEmpty()) return robotInfoList
            val currentTimeMillis = System.currentTimeMillis()
            val filterMap = robotMap.filterValues { value ->
                value.receiveTime > currentTimeMillis - 2500
            }
            if (filterMap.isEmpty()) {
                robotMap.clear()
                Timber.w("robot size is zero")
                return robotInfoList
            }
            if (robotMap.size != filterMap.size) {
                robotMap.clear()
                robotMap.putAll(filterMap)
            }
            robotInfoList = ArrayList(filterMap.values.toList())
            Timber.w("robot size : %s", robotInfoList.size)
            return robotInfoList
        }
    }
}