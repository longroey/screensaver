package com.didi.drivingrecorder;

import com.didi.drivingrecorder.ICloseCameraCallback;

interface IDrService {

    // 埋点
    void trackEvent(String eventId);

    // 埋点
    void trackEventKV(String eventId, String key, String value);

    // 关闭正在录制的摄像头
    void closeCamera(ICloseCameraCallback callback);

    // 应用重启
    void reopenApp();

    // 前置摄像头是否正在录制
    boolean isFrontCameraRecording();

    // 后置摄像头是否正在录制
    boolean isBackCameraRecording();

}
