package com.didi.drivingrecorder;

interface IDrService {

    // 埋点
    void trackEvent(String eventId);

    // 埋点
    void trackEventKV(String eventId, String key, String value);

}
