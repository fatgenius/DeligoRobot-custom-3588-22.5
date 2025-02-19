package com.reeman.delige.activities;


import static com.reeman.delige.base.BaseApplication.dbRepository;
import static com.reeman.delige.base.BaseApplication.mApp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;

import com.reeman.delige.BuildConfig;
import com.reeman.delige.R;
import com.reeman.delige.SplashActivity;
import com.reeman.delige.constants.Constants;
import com.reeman.delige.repository.entities.CrashNotify;
import com.reeman.delige.request.model.Msg;
import com.reeman.delige.request.notifier.Notifier;
import com.reeman.delige.request.notifier.NotifyConstant;
import com.reeman.delige.utils.LocaleUtil;
import com.reeman.delige.utils.ScreenUtils;
import com.reeman.delige.utils.SpManager;

import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import timber.log.Timber;

public class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int languageType = SpManager.getInstance().getInt(Constants.KEY_LANGUAGE_TYPE, Constants.DEFAULT_LANGUAGE_TYPE);
        Timber.tag(getClass().getSimpleName()).e("语言 : %d",languageType);
        LocaleUtil.changeAppLanguage(getResources(), languageType);
        setContentView(R.layout.activity_crash);
        ScreenUtils.hideBottomUIMenu(this);
        findViewById(R.id.restartButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            ScreenUtils.setImmersive(this);
            mApp.exit();
        });
        findViewById(R.id.exitButton).setOnClickListener(v -> {
            ScreenUtils.setImmersive(this);
            mApp.exit();
        });
        getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String hostname = getIntent().getStringExtra("hostname");
        if (!TextUtils.isEmpty(hostname) && !BuildConfig.DEBUG) {
            String stackTrace = getIntent().getStringExtra("stackTrace");
            Observable<Map<String, Object>> notify2 = Notifier.notify2(new Msg(NotifyConstant.SYSTEM_NOTIFY, "application crash(应用崩溃)", stackTrace, hostname));
            if (notify2 != null) {
                notify2.subscribe(stringObjectMap -> Timber.w("上传crash日志成功"), throwable -> dbRepository.addCrashNotify(new CrashNotify(stackTrace)));
            }
        }
    }
}

