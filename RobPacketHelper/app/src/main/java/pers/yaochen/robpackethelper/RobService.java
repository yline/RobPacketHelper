package pers.yaochen.robpackethelper;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.yline.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

import pers.yaochen.robpackethelper.helper.RobHelper;
import pers.yaochen.robpackethelper.helper.StaticManager;

/**
 * @author yaochen 2017/11/30 15:09 All rights reserved.
 * @describe TODO
 */
public class RobService extends AccessibilityService {
    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.v("");
        StaticManager.getInstance().setRobServiceRunning(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.v("flags = " + flags + ", startId = " + startId);
        StaticManager.getInstance().setRobServiceRunning(true);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        LogUtil.v("");
        // 监听推送通知
        if (null == robHelper) {
            robHelper = new RobHelper(this);
        }

        robHelper.watchNotifications(event);
        robHelper.robRedPacket(this, event);
    }

    private RobHelper robHelper;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        LogUtil.v("");
        StaticManager.getInstance().setRobServiceRunning(true);
    }

    @Override
    public void onInterrupt() {
        LogUtil.v("");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.v("");

        if (null != robHelper) {
            robHelper.wakeAndUnLock();
        }
        StaticManager.getInstance().setRobServiceRunning(false);
    }
}
