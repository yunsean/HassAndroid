package cn.com.thinkwatch.ihass2.aidl;

import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback;
import cn.com.thinkwatch.ihass2.dto.ServiceRequest;
import cn.com.thinkwatch.ihass2.model.Location;

interface IDataSyncService {
    void hassChanged();
    void configChanged();
    boolean isRunning();
    void requestLocation();
    cn.com.thinkwatch.ihass2.model.Location getLocation();
    boolean callService(in String domain, in String service, in ServiceRequest serviceRequest);
    void registerCallback(IDataSyncCallback cb);
    void unregisterCallback(IDataSyncCallback cb);
}