package com.reeman.delige.state

import com.reeman.delige.event.Event

object RobotInfo {

    var hflsVersionEvent: Event.OnHflsVersionEvent? = null

    var isNetworkConnected = false
}