package cn.com.thinkwatch.ihass2.aidl;
import cn.com.thinkwatch.ihass2.model.Location;

interface IDataSyncCallback {
    void onEntityUpdated();
    void onEntityChanged(in String entityId);
    void onConnectChanged(in boolean isLocal);
    void onLocationChanged(in cn.com.thinkwatch.ihass2.model.Location location);
    void onCallResult(in long index, in String result, in String error);
}
