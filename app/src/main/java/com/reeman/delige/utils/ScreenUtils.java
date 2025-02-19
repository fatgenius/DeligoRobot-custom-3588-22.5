package com.reeman.delige.utils;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import static com.reeman.delige.base.BaseApplication.mApp;

import com.reeman.delige.board.Board;
import com.reeman.delige.board.BoardFactory;

/**
 * @ClassName: ScreenUtils.java
 * @Author: XueDong(1123988589 @ qq.com)
 * @Date: 2022/1/9 15:02
 * @Description: 屏幕设置工具类
 */
public class ScreenUtils {

    private static final int SYSTEM_UI_FLAG_IMMERSIVE_GESTURE_ISOLATED = 0x00004000;

    /**
     * 隐藏虚拟按键，并且全屏
     */
    public static void hideBottomUIMenu(Activity context) {
        Board board = BoardFactory.create( Build.PRODUCT);
        board.navigationBarControl(context, false);
        board.gestureNavigationBarControl(context, false);
        board.statusBarControl(context, false);
    }

    /**
     * 沉浸式，上划可以显示导航栏，过一段时间自动消失
     *
     * @param context
     */
    public static void setImmersive(Activity context) {
        Board board = BoardFactory.create(Build.PRODUCT);
        if (board != null) {
            board.navigationBarControl(context, true);
            board.gestureNavigationBarControl(context, true);
            board.statusBarControl(context, true);
        }
    }

    public static float getScreenWidth() {
        return mApp.getResources().getDisplayMetrics().widthPixels;
    }

    public static float getScreenHeight() {
        return mApp.getResources().getDisplayMetrics().heightPixels;
    }

}
