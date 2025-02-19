package com.reeman.delige;

import android.content.Context;
import android.media.AudioManager;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.reeman.delige.base.BaseActivity;
import com.reeman.delige.constants.Constants;
import com.reeman.delige.contract.SplashContract;
import com.reeman.delige.navigation.ROSController;
import com.reeman.delige.presenter.impl.SplashPresenter;
import com.reeman.delige.state.RobotInfo;
import com.reeman.delige.utils.LocaleUtil;
import com.reeman.delige.utils.ScreenUtils;
import com.reeman.delige.utils.SpManager;
import com.reeman.delige.widgets.EasyDialog;
import com.reeman.delige.widgets.WebViewHolder;
import com.reeman.delige.event.Event;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import timber.log.Timber;

import static com.reeman.delige.base.BaseApplication.isFirstEnter;

import static com.reeman.delige.base.BaseApplication.mApp;
import static com.reeman.delige.base.BaseApplication.ros;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SplashActivity extends BaseActivity implements SplashContract.View {

    private GifImageView mStartupImageView;
    private GifDrawable mStartupDrawable;
    private Runnable mCommunicationRunnable;
    private SplashPresenter presenter;
    private volatile boolean receiveDataFormROS = false;

    @Override
    protected boolean disableBottomNavigationBar() {
        return true;
    }


    @Override
    protected int getLayoutRes() {
        return R.layout.activity_splash;
    }

    @Override
    protected void initCustomView() {
        ViewTreeObserver viewTreeObserver = getWindow().getDecorView().getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mHandler.postDelayed(() -> WebViewHolder.getView(getApplicationContext()), 1500);
            }
        });
        initStartupAnimation();
    }

    @Override
    protected void initData() {
        presenter = new SplashPresenter(this);
        int languageType = SpManager.getInstance().getInt(Constants.KEY_LANGUAGE_TYPE, Constants.DEFAULT_LANGUAGE_TYPE);
        if (languageType != -1 && languageType != LocaleUtil.getLocaleType()) {
            LocaleUtil.changeAppLanguage(getResources(), languageType);
        }
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    }

    private void initStartupAnimation() {
        try {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mStartupImageView = new GifImageView(this);
            mStartupDrawable = new GifDrawable(getResources(), R.drawable.gif_startup);
            mStartupImageView.setImageDrawable(mStartupDrawable);
            ((FrameLayout) $(R.id.root)).addView(mStartupImageView, layoutParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mStartupDrawable.start();
        if (isFirstEnter) {
            isFirstEnter = false;
            ros = ROSController.getInstance();
            try {
                ros.init();
                ros.heartBeat();
            } catch (Exception e) {
                e.printStackTrace();
                Timber.e(e, "串口初始化失败");
                EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.text_communicate_failed_with_ros), (dialog, id) -> {
                    dialog.dismiss();
                    ScreenUtils.setImmersive(this);
                    mApp.exit();
                });
            }
        } else {
            ros.heartBeat();
        }
        Runnable mCommunicationRunnable = () -> {
            if (receiveDataFormROS) {
                receiveDataFormROS = false;
                presenter.startup(SplashActivity.this);
            } else {
                EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.text_communicate_failed_with_ros), (dialog, id) -> {
                    dialog.dismiss();
                    ScreenUtils.setImmersive(this);
                    mApp.exit();
                });
            }
        };
        mHandler.postDelayed(mCommunicationRunnable, 3000);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onHflsVersionEvent(Event.OnHflsVersionEvent event) {
        RobotInfo.INSTANCE.setHflsVersionEvent(event);
        receiveDataFormROS = true;
        ros.cpuPerformance();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mStartupDrawable.stop();
    }
}