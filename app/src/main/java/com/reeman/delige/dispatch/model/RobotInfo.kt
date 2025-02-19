package com.reeman.delige.dispatch.model

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.reeman.delige.constants.State
import com.reeman.delige.dispatch.DispatchState
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

@Keep
data class RobotInfo(
    @SerializedName("h")
    @Expose(serialize = true,deserialize = true)
    var hostname: String = "",
    @Volatile
    @SerializedName("cp")
    @Expose(serialize = true,deserialize = true)
    var currentPosition: DoubleArray = DoubleArray(3),
    @Volatile
    @SerializedName("csa")
    @Expose(serialize = true,deserialize = true)
    var currentSpecialArea: String = "",
    @SerializedName("rt")
    @Expose(serialize = false,deserialize = false)
    var receiveTime: Long = 0,
    @SerializedName("esas")
    @Expose(serialize = true,deserialize = true)
    var enterSpecialAreaSequence: Int = 0,
    @SerializedName("pq")
    @Expose(serialize = true,deserialize = true)
    val pointQueue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue<String>(),
    @SerializedName("ds")
    @Expose(serialize = true,deserialize = true)
    var dispatchState: DispatchState = DispatchState.INIT,
    @Expose(serialize = false,deserialize =false)
    var currentPositionWithLineSpeed: IntArray = IntArray(3)
) : java.io.Serializable {

    companion object {
        fun fromCmdString(data: String): RobotInfo {
            val splitRobotInfo = data.split("\n")
            var robotInfo = RobotInfo()
            if (splitRobotInfo.size >= 6) {
                robotInfo.hostname = splitRobotInfo[0]
                val splitPosition = splitRobotInfo[1].split(",")
                robotInfo.currentPosition =
                    splitPosition.map { it.toDouble() / 100 }.toDoubleArray()
                robotInfo.dispatchState = DispatchState.values()[splitRobotInfo[2].toInt()]
                if (splitRobotInfo[3] != "nul") {
                    val splitPath = splitRobotInfo[3].split(",")
                    for (point in splitPath) {
                        if (point.startsWith("w_")) {
                            if (point.contains("*")) {
                                val splitPoint = point.split("*")
                                val firstWayPoint = splitPoint[0].replace("w_", "").toInt()
                                if (splitPoint[1].startsWith("-")) {
                                    for (i in 0..splitPoint[1].replace("-", "").toInt()) {
                                        robotInfo.pointQueue.add("w_" + (firstWayPoint - i))
                                    }
                                } else {
                                    for (i in 0..splitPoint[1].replace("-", "").toInt()) {
                                        robotInfo.pointQueue.add("w_" + (firstWayPoint + i))
                                    }
                                }
                            } else {
                                robotInfo.pointQueue.add(point)
                            }
                        } else {
                            robotInfo.pointQueue.add(point)
                        }
                    }
                }
                robotInfo.currentSpecialArea = if (splitRobotInfo[4] == "nul") {
                    ""
                } else {
                    splitRobotInfo[4]
                }
                robotInfo.enterSpecialAreaSequence = splitRobotInfo[5].toInt()
                if (splitRobotInfo.size == 7) {
                    Timber.d("receive count : %s", splitRobotInfo[6])
                }
            }
            Timber.w("parser robot info :%s", robotInfo)
            return robotInfo
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RobotInfo) return false
        if (hostname != other.hostname) return false
        if (currentSpecialArea != other.currentSpecialArea) return false
        if (pointQueue != other.pointQueue) return false
        if (dispatchState != other.dispatchState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hostname.hashCode()
        result = 31 * result + currentSpecialArea.hashCode()
        result = 31 * result + pointQueue.hashCode()
        result = 31 * result + dispatchState.hashCode()
        return result
    }
}