package pers.yaochen.robpackethelper.helper;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateInterpolator;

import com.yline.utils.LogUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 将Service内容，抽过来
 *
 * @author yline 2019/2/2 -- 9:31
 */
public class RobHelper {
    // 锁屏、解锁相关
    private KeyguardManager keyguardManager;
    private KeyguardManager.KeyguardLock keyguardLock;

    // 唤醒屏幕相关
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock = null;

    private static final String RED_PACKET_PARENT_LAYOUT = "android.widget.LinearLayout";

    private static final String RED_PACKET_NOTIFICATION = "微信红包";
    private static final String GET_RED_PACKET = "领取红包";            // 别人发的
    private static final String CHECK_RED_PACKET = "查看红包";          // 自己发的

    private static final String RECEIVE_RED_PACKET_PRIVATE = "给你发了一个红包";
    private static final String RECEIVE_RED_PACKET_PUBLIC = "发了一个红包";
    private static final String RED_PACKET_PICKED = "手慢了，红包派完了";
    private static final String RED_PACKET_EXPIRED = "该红包";

    private static final String KEYGUARD_TAG = "unLock";

    public RobHelper(Context context) {
        // 获取电源管理器对象
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        // 得到键盘锁管理器对象
        keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        // 初始化一个键盘锁管理器对象
        keyguardLock = keyguardManager.newKeyguardLock(KEYGUARD_TAG);
    }

    /**
     * 监听，红包推送通知
     */
    public void watchNotifications(AccessibilityEvent event) {
        int eventType = event.getEventType();
        String eventText = event.getText().toString();
        LogUtil.v("eventType = " + eventType + ", eventText = " + eventText);

        // 监听是否有通知栏消息 不是通知栏消息则直接结束
        if (eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }

        // 判断是否为微信红包,不为微信红包则直接返回
        if (null == eventText || !eventText.contains(RED_PACKET_NOTIFICATION)) {
            return;
        }

        LogUtil.v("rob Notification");
        // 是微信红包就解锁
        wakeAndUnLock(true);

        // 微信红包时，发送该消息,模拟点击该消息
        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            try {
                // 发送该通知对象
                ((Notification) parcelable).contentIntent.send();
            } catch (Exception e) {
                LogUtil.e("mockClick", e);
            }
        }
    }

    public void wakeAndUnLock() {
        wakeAndUnLock(false);
    }

    @SuppressLint("InvalidWakeLockTag")
    private void wakeAndUnLock(boolean unLock) {
        // 解锁屏幕
        if (unLock) {
            // 若为黑屏状态则唤醒屏幕
            if (!powerManager.isScreenOn()) {
                //获取电源管理器对象，ACQUIRE_CAUSES_WAKEUP这个参数能从黑屏唤醒屏幕
                wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
                //点亮屏幕
                wakeLock.acquire();
            }

            //若在锁屏界面则解锁直接跳过锁屏
            if (keyguardManager.inKeyguardRestrictedInputMode()) {
                //设置解锁标志，以判断抢完红包能否锁屏
                // enableKeyguard = false;
                keyguardLock.disableKeyguard();
            }
            // 锁屏
        } else {
            keyguardLock.reenableKeyguard();
            if (wakeLock != null) {
                wakeLock.release();
                wakeLock = null;
            }
        }
    }

    /**
     * 抢红包具体操作
     */
    public void robRedPacket(AccessibilityService service, AccessibilityEvent event) {
        AccessibilityNodeInfo rootNodeInfo = event.getSource();

        int eventType = event.getEventType();
        String nodeInfo = (null == rootNodeInfo ? "null" : String.valueOf(rootNodeInfo.getText()));
        LogUtil.v("eventType = " + eventType + ", nodeInfo = " + nodeInfo);

        // 根节点为空直接返回
        if (null == rootNodeInfo) {
            return;
        }

        // 聊天列表 --> 点击进入
        watchChatList(eventType, rootNodeInfo);

        // 遍历聊天详情节点
        checkChatInfo(event, rootNodeInfo);

        // 打开红包
        openRedPacket(service, event, rootNodeInfo);

        // 返回领取下一个红包或者退出锁屏
        back(service, event, rootNodeInfo);
    }

    /**
     * 监听聊天列表
     *
     * @param eventType 聊天列表有变化时会收到TYPE_WINDOW_CONTENT_CHANGED事件
     *                  经测试在收到TYPE_WINDOW_CONTENT_CHANGED事件之前会收到TYPE_WINDOW_STATE_CHANGED
     */
    private void watchChatList(int eventType, AccessibilityNodeInfo rootNodeInfo) {
        // 收到非页面内容变化事件直接返回
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) { // 2048
            return;
        }

        // 获取包含“[微信红包]”字样的内容节点
        List<AccessibilityNodeInfo> totalNodeArray = rootNodeInfo.findAccessibilityNodeInfosByText(RED_PACKET_NOTIFICATION);
        if (null != totalNodeArray && !totalNodeArray.isEmpty()) {
            // 第一种，自己已领取；要排除
            List<AccessibilityNodeInfo> firstList = rootNodeInfo.findAccessibilityNodeInfosByText("已领取");

            // 第二种，全部被领完；要排除
            List<AccessibilityNodeInfo> secondList = rootNodeInfo.findAccessibilityNodeInfosByText("已被领完");

            for (AccessibilityNodeInfo node : totalNodeArray) {
                AccessibilityNodeInfo normalTop = node.getParent().getParent(); // 顶部


            }

            LogUtil.v("---");
        }

/*
        if (null != nodeArray && !nodeArray.isEmpty()) {
            AccessibilityNodeInfo node = nodeArray.get(0);

            Bundle nodeExtras = node.getExtras();
            long sourceId = nodeExtras.getLong("sourceId");
            LogUtil.v("sourceId = " + sourceId);
            if (sourceId > 0) {
                LogUtil.v("红包已被查看");
            } else {
                nodeExtras.putLong("sourceId", System.currentTimeMillis());
                node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            *//*
            List<AccessibilityNodeInfo> singleList = node.getParent().findAccessibilityNodeInfosByText("已领取");
            if (null != singleList && !singleList.isEmpty()) {
                LogUtil.v("红包，已领取");
                return;
            }

            List<AccessibilityNodeInfo> allList = node.getParent().findAccessibilityNodeInfosByText("已被领完");
            if (null != allList && !allList.isEmpty()) {
                LogUtil.v("红包，已被领完");
                return;
            }*//*
        }*/
    }

    // 当前页面红包总量
    private int redPacketNum = 0;

    // 已经领取的红包数量
    private int hasReceived = 0;

    // 是否计算过红包数量
    private boolean isCalcRedPacketNum = false;

    /**
     * 遍历聊天详情节点
     * 判断是否存在红包
     */
    private void checkChatInfo(AccessibilityEvent event, AccessibilityNodeInfo rootNodeInfo) {
        // 收到非页面内容变化事件直接返回
        List<AccessibilityNodeInfo> nodeArray = findAccessibilityNodeInfosByTexts(rootNodeInfo, GET_RED_PACKET, CHECK_RED_PACKET);
        if (null != nodeArray && !nodeArray.isEmpty()) {
            LogUtil.v("存在红包！");

            // 计算当前页面红包总个数
            if (!isCalcRedPacketNum) {
                redPacketNum += nodeArray.size();
                isCalcRedPacketNum = true;
            }

            // 获取目标红包节点
            AccessibilityNodeInfo targetNode = nodeArray.get(0);
            // 红包父布局为LinearLayout，可以避免被普通文字干扰
            if (RED_PACKET_PARENT_LAYOUT.equals(targetNode.getParent().getClassName())) {
                targetNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } else {
            LogUtil.v("不存在红包!");
        }
    }

    /**
     * 打开红包
     */
    private void openRedPacket(AccessibilityService service, AccessibilityEvent event, AccessibilityNodeInfo rootNodeInfo) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        // 存在可拆红包
        List<AccessibilityNodeInfo> packetNodes = findAccessibilityNodeInfosByTexts(rootNodeInfo, RECEIVE_RED_PACKET_PRIVATE, RECEIVE_RED_PACKET_PUBLIC);
        if (!packetNodes.isEmpty()) {
            // 接收红包+1
            hasReceived++;
            LogUtil.v("packetNodes: " + hasReceived);
            // 这里是把根节点下的所有view的类型打印出来，找到了唯一的Button
            // 用DDMS看到的布局与实际不符,待研究
            AccessibilityNodeInfo node = packetNodes.get(0);
            AccessibilityNodeInfo frameNode = node.getParent();
            AccessibilityNodeInfo targetNode = frameNode.getChild(3);
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        // 红包被领完或者过期等异常情况
        List<AccessibilityNodeInfo> errNodes = findAccessibilityNodeInfosByTexts(rootNodeInfo, RED_PACKET_PICKED, RED_PACKET_EXPIRED);
        if (!errNodes.isEmpty()) {
            hasReceived++;
            LogUtil.v("errNodes: " + hasReceived);
            // 红包异常状态则调用返回键 (也可以搜索返回按钮)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }
    }

    /**
     * 返回继续领红包或者退出锁屏
     */
    private void back(AccessibilityService service, AccessibilityEvent event, AccessibilityNodeInfo rootNodeInfo) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        List<AccessibilityNodeInfo> nodes = findAccessibilityNodeInfosByTexts(rootNodeInfo, "红包详情");
        if (!nodes.isEmpty()) {
            LogUtil.v("进入到领完页面 ");

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            LogUtil.v("hasReceived: " + hasReceived);
            if (hasReceived >= redPacketNum && isCalcRedPacketNum) {
                LogUtil.v("领完退出！");
                hasReceived = 0;
                redPacketNum = 0;
                isCalcRedPacketNum = false;
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

                wakeAndUnLock(false);
            }
        }
    }

    /**
     * 批量化执行AccessibilityNodeInfo.findAccessibilityNodeInfosByText(text).
     * 由于这个操作影响性能,将所有需要匹配的文字一起处理,尽早返回
     *
     * @param nodeInfo 窗口根节点
     * @param texts    需要匹配的字符串们
     * @return 匹配到的节点数组
     */
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String... texts) {
        for (String text : texts) {
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);
            if (!nodes.isEmpty()) {
                return nodes;
            }
        }
        return new ArrayList<>();
    }
}































