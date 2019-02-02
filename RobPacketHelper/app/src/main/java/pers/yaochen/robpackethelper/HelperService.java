package pers.yaochen.robpackethelper;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yaochen 2017/11/30 15:09 All rights reserved.
 * @describe TODO
 */
public class HelperService extends AccessibilityService {
    private static final String TAG = "HelperService";

    // 锁屏、解锁相关
    private KeyguardManager km;
    private KeyguardManager.KeyguardLock kl;
    // 唤醒屏幕相关
    private PowerManager pm;
    private PowerManager.WakeLock wl = null;

    // 通知根节点
    private AccessibilityNodeInfo rootNodeInfo;

    // 当前页面红包总量
    private int redPacketNum = 0;

    // 是否计算过红包数量
    private boolean isCalcRedPacketNum = false;

    // 已经领取的红包数量
    private int hasReceived = 0;

    // 红包节点父布局
    private static final String RED_PACKET_PARENT_LAYOUT = "android.widget.LinearLayout";
    private static final String RED_PACKET_NOTIFICATION = "[微信红包]";
    private static final String GET_RED_PACKET = "领取红包";            // 别人发的
    private static final String CHECK_RED_PACKET = "查看红包";          // 自己发的
    private static final String RECEIVE_RED_PACKET_PRIVATE = "给你发了一个红包";
    private static final String RECEIVE_RED_PACKET_PUBLIC = "发了一个红包";
    private static final String RED_PACKET_PICKED = "手慢了，红包派完了";
    private static final String RED_PACKET_EXPIRED = "该红包";
//    private static final String RED_PACKET_PICKED2 = "手气";
//    private static final String RED_PACKET_PICKED_DETAIL = "红包详情";
//    private static final String RED_PACKET_SAVE = "已存入零钱";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 监听推送通知
        watchNotifications(event);

        // 根节点为空直接返回
        rootNodeInfo = event.getSource();
        if (null == rootNodeInfo) {
            return;
        }

        // 监听聊天列表
        watchChatList(event);

        // 遍历聊天详情节点
        checkChatInfo(event);

        // 打开红包
        openRedPacket(event);

        // 返回领取下一个红包或者退出锁屏
        back(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // 获取电源管理器对象
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // 得到键盘锁管理器对象
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        // 初始化一个键盘锁管理器对象
        kl = km.newKeyguardLock("unLock");
    }

    /**
     * 监听推送通知
     *
     * @param event
     */
    private void watchNotifications(AccessibilityEvent event) {
        // 监听是否有通知栏消息 不是通知栏消息则直接结束
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return;
        }
        // 判断是否为微信红包,不为微信红包则直接返回
        String title = event.getText().toString();
        if (!title.contains(RED_PACKET_NOTIFICATION)) {
            return;
        }

        // 是微信红包就解锁
        wakeAndUnLock(true);

        // 微信红包时，发送该消息,模拟点击该消息
        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                // 发送该通知对象
                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 监听聊天列表
     *
     * @param event 聊天列表有变化时会收到TYPE_WINDOW_CONTENT_CHANGED事件
     *              经测试在收到TYPE_WINDOW_CONTENT_CHANGED事件之前会收到TYPE_WINDOW_STATE_CHANGED
     */
    private void watchChatList(AccessibilityEvent event) {
        // 收到非页面内容变化事件直接返回
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        // 获取包含“[微信红包]”字样的内容节点
        List<AccessibilityNodeInfo> nodes = findAccessibilityNodeInfosByTexts(rootNodeInfo,
                new String[]{RED_PACKET_NOTIFICATION});
        if (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.get(0);
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

    }

    /**
     * 遍历聊天详情节点
     * 判断是否存在红包
     *
     * @param event
     */
    private void checkChatInfo(AccessibilityEvent event) {
        Log.d(TAG, "EventType: " + event.getEventType());
        Log.d(TAG, "eventClassName: " + event.getClassName().toString());
        // 收到非页面内容变化事件直接返回
        List<AccessibilityNodeInfo> nodes = findAccessibilityNodeInfosByTexts(rootNodeInfo,
                new String[]{GET_RED_PACKET, CHECK_RED_PACKET});
        if (!nodes.isEmpty()) {
            Log.d(TAG, "存在红包！");
            // 计算当前页面红包总个数
            if (!isCalcRedPacketNum) {
                redPacketNum += nodes.size();
                isCalcRedPacketNum = true;
            }
            // 获取目标红包节点
            AccessibilityNodeInfo targetNode = nodes.get(0);
            // 红包父布局为LinearLayout，可以避免被普通文字干扰
            if (RED_PACKET_PARENT_LAYOUT.equals(targetNode.getParent().getClassName())) {
                targetNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } else {
            Log.d(TAG, "不存在红包!");
        }
    }

    /**
     * 打开红包
     */
    private void openRedPacket(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        // 存在可拆红包
        List<AccessibilityNodeInfo> packetNodes = findAccessibilityNodeInfosByTexts(rootNodeInfo,
                new String[]{RECEIVE_RED_PACKET_PRIVATE, RECEIVE_RED_PACKET_PUBLIC});
        // 红包被领完或者过期等异常情况
        List<AccessibilityNodeInfo> errNodes = findAccessibilityNodeInfosByTexts(rootNodeInfo,
                new String[]{RED_PACKET_PICKED, RED_PACKET_EXPIRED});
        if (!packetNodes.isEmpty()) {
            // 接收红包+1
            hasReceived++;
            Log.d(TAG, "packetNodes: " + hasReceived);
            // 这里是把根节点下的所有view的类型打印出来，找到了唯一的Button
            // 用DDMS看到的布局与实际不符,待研究
            AccessibilityNodeInfo node = packetNodes.get(0);
            AccessibilityNodeInfo frameNode = node.getParent();
            AccessibilityNodeInfo targetNode = frameNode.getChild(3);
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        if (!errNodes.isEmpty()) {
            hasReceived++;
            Log.d(TAG, "errNodes: " + hasReceived);
            // 红包异常状态则调用返回键 (也可以搜索返回按钮)
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }
    }

    /**
     * 返回继续领红包或者退出锁屏
     */
    private void back(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }
        List<AccessibilityNodeInfo> nodes = findAccessibilityNodeInfosByTexts(rootNodeInfo,
                new String[]{"红包详情"});
        if (!nodes.isEmpty()) {
            Log.d(TAG, "进入到领完页面 ");
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            Log.d(TAG, "hasReceived: " + hasReceived);
            if (hasReceived >= redPacketNum && isCalcRedPacketNum) {
                Log.d(TAG, "领完退出！");
                hasReceived = 0;
                redPacketNum = 0;
                isCalcRedPacketNum = false;
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
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
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);

            if (!nodes.isEmpty()) {
                return nodes;
            }
        }
        return new ArrayList<>();
    }

    /**
     * 解锁屏
     */
    private void wakeAndUnLock(boolean unLock) {
        // 解锁屏幕
        if (unLock) {
            // 若为黑屏状态则唤醒屏幕
            if (!pm.isScreenOn()) {
                //获取电源管理器对象，ACQUIRE_CAUSES_WAKEUP这个参数能从黑屏唤醒屏幕
                wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
                //点亮屏幕
                wl.acquire();
            }
            //若在锁屏界面则解锁直接跳过锁屏
            if (km.inKeyguardRestrictedInputMode()) {
                //设置解锁标志，以判断抢完红包能否锁屏
//                enableKeyguard = false;
                kl.disableKeyguard();
            }
            // 锁屏
        } else {
            kl.reenableKeyguard();
            if (wl != null) {
                wl.release();
                wl = null;
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt: ");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 锁屏
        wakeAndUnLock(false);
    }
}
