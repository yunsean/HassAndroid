package cn.com.thinkwatch.ihass2.aidl;

import cn.com.thinkwatch.ihass2.aidl.IDataSyncCallback;
import cn.com.thinkwatch.ihass2.dto.ServiceRequest;
import cn.com.thinkwatch.ihass2.model.Location;
import cn.com.thinkwatch.ihass2.model.JsonEntity;

interface IDataSyncService {
    void hassChanged();
    void configChanged();
    void triggerChanged();
    void observedChanged();
    void widgetChanged();
    void notificationChanged();
    void updateEntity(in long index);
    void callService(in long index, in String domain, in String service, in ServiceRequest serviceRequest);
    void getService(in long index);
    void getHistory(in long index, in String timestamp, in String entityId, in String endTime);
    void requestLocation();
    void nfcTrigger(String uid);
    cn.com.thinkwatch.ihass2.model.Location getLocation();
    void registerCallback(IDataSyncCallback cb);
    void unregisterCallback(IDataSyncCallback cb);
}