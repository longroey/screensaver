package com.didi.drivingrecorder.fota;

import com.didi.drivingrecorder.fota.ICountDownCallback;
import com.didi.drivingrecorder.fota.IFetchUpdateInfoCallBack;

interface IDidiFotaService {

    // 注册升级倒计时回调
    void registerUpdateCountDownCallback(ICountDownCallback callback);

    // 注销升级倒计时回调
    void unregisterUpdateCountDownCallback(ICountDownCallback callback);

    // 获取升级包信息
    void fetchUpdateInfo(IFetchUpdateInfoCallBack callback);

}
