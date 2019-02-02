package pers.yaochen.robpackethelper.helper;

import com.yline.utils.LogUtil;

import java.util.HashMap;
import java.util.HashSet;

public class StaticManager {
    private HashMap<String, Object> hashMap = new HashMap<>();

    private HashSet<Long> packetMap = new HashSet<>();

    private static StaticManager sInstance;

    private StaticManager() {
    }

    public synchronized static StaticManager getInstance() {
        if (null == sInstance) {
            synchronized (StaticManager.class) {
                if (null == sInstance) {
                    sInstance = new StaticManager();
                }
            }
        }
        return sInstance;
    }

    public void setClickedPacket(long hashCode) {
        packetMap.add(hashCode);
    }

    public boolean isClickedPacked(long hashCode) {
        return packetMap.contains(hashCode);
    }

    public void setRobServiceRunning(boolean isRunning) {
        hashMap.put(Key.ROB_SERVICE_RUNNING, isRunning);
    }

    public boolean isRobServiceRunning() {
        Boolean isRunning = (Boolean) hashMap.get(Key.ROB_SERVICE_RUNNING);
        LogUtil.v("isRunning = " + isRunning);
        return (null != isRunning && isRunning);
    }

    private static class Key {
        private static final String ROB_SERVICE_RUNNING = "rob_service_running";
    }
}
