package pers.yaochen.robpackethelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import com.yline.base.BaseAppCompatActivity;
import com.yline.utils.LogUtil;

import java.util.List;

import pers.yaochen.robpackethelper.helper.StaticManager;

public class MainActivity extends BaseAppCompatActivity {
    private TextView stateTextView;

    private static final String STATE_OPEN = "插件正在运行";
    private static final String STATE_WAITING = "未开启权限";
    private static final String STATE_CLOSE = "插件未开启";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initData(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initData(false);
    }

    private void initView() {
        stateTextView = findViewById(R.id.main_service_state);

        initViewClick();
    }

    private void initViewClick() {
        // 更新服务状态
        findViewById(R.id.main_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initData(true);
            }
        });
    }

    private void initData(boolean autoOpenSetting) {
        boolean isExisted = StaticManager.getInstance().isRobServiceRunning();
        boolean isStartAccessibility = isStartAccessibilityService(MainActivity.this, RobService.class.getSimpleName());
        LogUtil.v("isExisted = " + isExisted + ", isStartAccessibility = " + isStartAccessibility);

        if (!isExisted) {
            stateTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
            stateTextView.setText(STATE_CLOSE);

            if (autoOpenSetting) {
                openAccessibility(MainActivity.this);
            }
        } else if (!isStartAccessibility) {
            stateTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
            stateTextView.setText(STATE_WAITING);

            if (autoOpenSetting) {
                openAccessibility(MainActivity.this);
            }
        } else {
            stateTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
            stateTextView.setText(STATE_OPEN);
        }
    }

    /**
     * 判断AccessibilityService服务是否已经启动
     */
    public static boolean isStartAccessibilityService(Context context, String name) {
        AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        LogUtil.v("accessibilityManager = " + accessibilityManager);
        if (null != accessibilityManager) {
            List<AccessibilityServiceInfo> serviceInfoList = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);

            if (null != serviceInfoList && !serviceInfoList.isEmpty()) {
                for (AccessibilityServiceInfo serviceInfo : serviceInfoList) {
                    String id = serviceInfo.getId(); // pers.yaochen.robpackethelper/.RobService
                    LogUtil.v("serviceInfoId = " + id);
                    if (id.contains(name)) {
                        return true;
                    }
                }
            } else {
                LogUtil.v("serviceInfoList = " + serviceInfoList);
            }
        }

        return false;
    }

    /**
     * 打开直接跳转到，无障碍
     */
    public static void openAccessibility(Context context) {
        Intent mAccessibleIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        context.startActivity(mAccessibleIntent);
    }

    /**
     * 服务是否开启，StaticManager比较靠谱；能够直接拿到数据
     */
    public static boolean isServiceExisted(Context context, String className) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (null != activityManager) {
            List<ActivityManager.RunningServiceInfo> serviceList = activityManager.getRunningServices(Integer.MAX_VALUE);

            if (null == serviceList || serviceList.isEmpty()) {
                return false;
            }

            for (ActivityManager.RunningServiceInfo serviceInfo : serviceList) {
                ComponentName serviceName = serviceInfo.service;
                if (serviceName.getClassName().equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }
}
