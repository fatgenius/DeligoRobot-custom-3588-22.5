//package com.reeman.delige.fragments;
//
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.Looper;
//import android.text.TextUtils;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.TextView;
//
//import androidx.annotation.Nullable;
//import androidx.room.util.FileUtil;
//
//import com.google.gson.JsonArray;
//import com.google.gson.JsonObject;
//import com.reeman.delige.BuildConfig;
//import com.reeman.delige.R;
//import com.reeman.delige.base.BaseFragment;
//import com.reeman.delige.utils.PackageUtils;
//import com.reeman.delige.event.Event;
//
//import org.greenrobot.eventbus.Subscribe;
//import org.greenrobot.eventbus.ThreadMode;
//
//import static com.reeman.delige.base.BaseApplication.ros;
//
//public class VersionSettingFragment extends BaseFragment implements View.OnClickListener {
//
//    private TextView tvNavigationVersion;
//    private final Handler handler = new Handler(Looper.getMainLooper());
//
//    @Override
//    protected int getLayoutRes() {
//        return R.layout.fragment_version_setting;
//    }
//
//    @Override
//    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//        super.onActivityCreated(savedInstanceState);
//        TextView tvAppVersion = root.findViewById(R.id.tv_app_version);
//        tvAppVersion.setText(PackageUtils.getVersion(requireContext()));
//        tvNavigationVersion = root.findViewById(R.id.tv_navigation_version);
//        tvNavigationVersion.setOnClickListener(this);
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        ros.getHostVersion();
//    }
//
//    @Override
//    public void onClick(View v) {
//        int id = v.getId();
//         if (id == R.id.tv_navigation_version) {
//            ros.getHostVersion();
//        }
//    }
//
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onHostVersionObtained(Event.OnVersionEvent event) {
//        tvNavigationVersion.setText(event.version);
//    }
//
//}
