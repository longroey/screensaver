package com.didi.drivingrecorder.fota;

interface ICountDownCallback {

    // 剩余时间 单位：毫秒
    void onCountDown(long timeLeft);

}
