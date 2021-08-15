// ILauncher.aidl
package com.yunsean.ihasslauncher;

interface ILauncher {
    void startup(
        String packageName,
        String serviceName,
        String actionName);
}
