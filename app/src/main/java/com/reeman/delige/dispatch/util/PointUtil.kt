package com.reeman.delige.dispatch.util

import com.reeman.delige.request.model.PathPoint
import com.reeman.delige.utils.DestHelper
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PointUtil {

    companion object {

        /**
         * 计算弧度
         */
        fun calculateRadian(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            // 计算两点之间的直线距离
            val deltaX = x2 - x1
            val deltaY = y2 - y1
            // 使用反正切函数计算弧度

            return BigDecimal.valueOf(atan2(deltaY, deltaX)).setScale(1,RoundingMode.CEILING).toDouble()
        }

        fun areSegmentsOpposite(radian1: Double,radian2: Double):Boolean{
            val angleDifference = abs(Math.toDegrees(radian1)-Math.toDegrees(radian2))
            return angleDifference <= 150.0 || angleDifference >= 210.0
        }


        /**
         * 计算距离
         */
        fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val deltaX = x2 - x1
            val deltaY = y2 - y1
            return sqrt((deltaX * deltaX + deltaY * deltaY))
        }

        /**
         * 计算最近的路线点
         */
        fun calculateNearestPoint(position: DoubleArray): PathPoint? {
            val pathPoints = DestHelper.getInstance().pathPoints
            if (pathPoints.isNullOrEmpty()) return null
            val pathPointList = pathPoints as List<PathPoint>
            if (pathPointList.isNotEmpty()) {
                return pathPointList
                    .filter {
                        calculateDistance(
                            position[0],
                            position[1],
                            it.xPosition,
                            it.yPosition
                        ) < 1.0
                    }
                    .minByOrNull {
                        calculateDistance(
                            position[0],
                            position[1],
                            it.xPosition,
                            it.yPosition
                        )
                    }
            }
            return null
        }

        /**
         * 判断点C是否在线段AB中间或前方
         */
        fun determinePosition(x1: Double, y1: Double, x2: Double, y2: Double, xC: Double, yC: Double): Boolean {
            val dx = x2 - x1
            val dy = y2 - y1

            val t = ((xC - x1) * dx + (yC - y1) * dy) / (dx * dx + dy * dy)

            val xPerpendicular = x1 + t * dx
            val yPerpendicular = y1 + t * dy

            return when {
                (xPerpendicular == xC && yPerpendicular == yC)|| t < 0 -> true // 点C在线段AB上/点C在线段AB的前方
                else -> false  // 点C在线段AB的后方
            }
        }

        fun compressStringArray(array: Array<String>?): String {
            if (array.isNullOrEmpty()) return ""
            val compressed = StringBuilder()
            var start = -1
            var end = -1
            for (i in array.indices) {
                if (array[i].startsWith("w_")) {
                    if (start == -1) {
                        start = i
                        end = i
                    } else {
                        end = i
                    }
                } else {
                    if (start != -1) {
                        if (start == end) {
                            compressed.append(array[start])
                        } else {
                            val startPoint = array[start].substring(2)
                            val endPoint = array[end].substring(2)
                            compressed.append("w_").append(startPoint).append("*")
                            if (startPoint.toInt() > endPoint.toInt()) {
                                compressed.append("-")
                            }
                            compressed.append(end - start + 1)
                        }
                        compressed.append(",")
                        start = -1
                        end = -1
                    }
                    compressed.append(array[i])
                    compressed.append(",")
                }
            }
            if (start != -1) {
                if (start == end) {
                    compressed.append(array[start])
                } else {
                    val startPoint = array[start].substring(2)
                    val endPoint = array[end].substring(2)
                    compressed.append("w_").append(startPoint).append("*")
                    if (startPoint.toInt() > endPoint.toInt()) {
                        compressed.append("-")
                    }
                    compressed.append(end - start + 1)
                }
            }
            return compressed.toString()
        }
    }
}