package com.reeman.delige.board;

import android.os.Build;

public class BoardConstants {
    public static final int WIFI_CONNECT_THRESHOLD = Build.PRODUCT.startsWith("YF") ||Build.PRODUCT.startsWith("rk3588") ? 1 : 2;
}
