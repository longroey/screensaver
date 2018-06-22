package com.rc.screensaver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.util.Log;
import android.view.View;

import com.didi.drivingrecorder.ICloseCameraCallback;
import com.didi.drivingrecorder.IDrService;
import com.didi.drivingrecorder.fota.ICountDownCallback;
import com.didi.drivingrecorder.fota.IDidiFotaService;
import com.didi.drivingrecorder.fota.IFetchUpdateInfoCallBack;
import com.didi.drivingrecorder.fota.UpdateInfo;

import com.rc.screensaver.Utils.GpsStatusListener;
import com.rc.screensaver.Utils.MobilePhoneStateListener;
import com.rc.screensaver.Utils.ScreensaverMoveSaverRunnable;

import static com.rc.screensaver.Utils.DEBUG;


public class Screensaver extends DreamService {
    private static final String TAG = "Screensaver";


    public static final int DEFAULT_SCREENSAVER_TIMEOUT = 5 * 60 * 1000; //Integer.MAX_VALUE;
    public static final int ORIENTATION_CHANGE_DELAY_MS = 200;

    private TelephonyManager mTelephonyManager;
    private MobilePhoneStateListener mListener;
    private LocationManager gpsLocationManager;
    private GpsStatusListener mGpsStatusListener;
    private View mContentView, mSaverView;

    private IDrService mDrService;
    private IDidiFotaService didiFotaService;
    private boolean mScreensaverTimeout = false;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1) {
                Long leftTime = (Long) msg.obj;
                // 秒
                long s = leftTime / 1000 % 60;
                // 分
                long m = leftTime / 1000 / 60;
                if (DEBUG) Log.d(TAG, "升级倒计时:" + m + "分" + s + "秒");
            } else if (msg.what == 2) {
                UpdateInfo updateInfo = (UpdateInfo) msg.obj;
                if (DEBUG) Log.d(TAG, "更新包获取结果:" + updateInfo);
            }
        }
    };

    private final ScreensaverMoveSaverRunnable mMoveSaverRunnable;

    private final Runnable mQuitScreensaver = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.i(TAG, "Screensaver time out");
            mScreensaverTimeout = true;
            traceEventKV("mirror_recorder_homepage_sw", "num", "3");
            finish();
        }
    };

    public Screensaver() {
        if (DEBUG) Log.i(TAG, "Screensaver allocated");
        mMoveSaverRunnable = new ScreensaverMoveSaverRunnable(this, mHandler);

    }

    @Override
    public void onCreate() {
        if (DEBUG) Log.i(TAG, "Screensaver created");
        super.onCreate();
        setTheme(R.style.ScreensaverTheme);
        bindService();
        bindFotaService();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (DEBUG) Log.d(TAG, "Screensaver configuration changed");
        super.onConfigurationChanged(newConfig);
        mHandler.removeCallbacks(mMoveSaverRunnable);
        layoutClockSaver();
        mHandler.postDelayed(mMoveSaverRunnable, ORIENTATION_CHANGE_DELAY_MS);
    }

    @Override
    public void onAttachedToWindow() {
        if (DEBUG) Log.i(TAG, "Screensaver attached to window");
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);

        layoutClockSaver();

        mHandler.post(mMoveSaverRunnable);
        mHandler.postDelayed(mQuitScreensaver, DEFAULT_SCREENSAVER_TIMEOUT);

        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mGpsStatusListener = new GpsStatusListener(this);
        gpsLocationManager.addGpsStatusListener(mGpsStatusListener);

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mListener = new MobilePhoneStateListener(this);
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        traceEvent("mirror_recorder_screensaver_sw");
        traceEventKV("mirror_recorder_homepage_sw", "num", "2");
    }

    @Override
    public void onDetachedFromWindow() {
        if (DEBUG) Log.i(TAG, "Screensaver detached from window");
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mMoveSaverRunnable);
        mHandler.removeCallbacks(mQuitScreensaver);
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        if (DEBUG) Log.d(TAG, "mScreensaverTimeout:" + mScreensaverTimeout);
        if (!mScreensaverTimeout) {
            traceEvent("mirror_recorder_screensaver_ck");
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "Screensaver destroyed");
        super.onDestroy();
        unbindService(connection);
        mDrService = null;

        unregisterUpdateCountDownCallback();
        unbindService(fotaConnection);
    }

    private void layoutClockSaver() {
        if (DEBUG) Log.d(TAG, "layoutClockSaver");
        setContentView(R.layout.screensaver);
        mSaverView = findViewById(R.id.screensaver_view);
        mContentView = (View) mSaverView.getParent();
        boolean dimNightMode = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ScreensaverSettingsActivity.KEY_NIGHT_MODE, false);
        Utils.dimClockView(this, dimNightMode, mSaverView);
        setScreenBright(!dimNightMode);

        mSaverView.setAlpha(0);
        mMoveSaverRunnable.registerViews(mContentView, mSaverView);
    }

    private void bindService(){
        if (DEBUG) Log.d(TAG, "bindService");
        Intent intent = new Intent();
        intent.setAction("com.didi.drivingrecorder.core.DrService");
        intent.setPackage("com.didi.drivingrecorder");
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.d(TAG, "onServiceConnected");
            mDrService = IDrService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.d(TAG, "onServiceDisconnected");
            mDrService = null;
            bindService();
        }
    };

    /**
     * 点击"埋点"
     *
     * @param eventId
     */
    public void traceEvent(String eventId){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, traceEvent return");
            return;
        }
        if (DEBUG) Log.d(TAG, "traceEvent eventId:" + eventId);
        try {
            mDrService.trackEvent(eventId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击"埋点"
     *
     * @param eventId
     * @param key
     * @param value
     */
    public void traceEventKV(String eventId, String key, String value){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, traceEventKV return");
            return;
        }
        if (DEBUG) Log.d(TAG, "traceEventKV eventId:" + eventId + " key:" + key + " value:" + value);
        try {
            mDrService.trackEventKV(eventId, key, value);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击"关闭后置摄像头录制"
     */
    public void closeCamera(){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, closeCamera return");
            return;
        }
        if (DEBUG) Log.d(TAG, "closeCamera");
        try {
            mDrService.closeCamera(new ICloseCameraCallback.Stub(){

                @Override
                public void onClosed() throws RemoteException {
                    if (DEBUG) Log.d(TAG, "closeCamera: Main camera is closed");
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击"重启行车记录仪app"
     */
    public void reopenApp(){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, reopenApp return");
            return;
        }
        if (DEBUG) Log.d(TAG, "reopenApp");
        try {
            mDrService.reopenApp();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击"前置摄像头是否正在录制"
     */
    public void isFrontCameraRecording(){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, isFrontCameraRecording return");
            return;
        }
        if (DEBUG) Log.d(TAG, "isFrontCameraRecording");
        try {
            boolean recording = mDrService.isFrontCameraRecording();
            if (DEBUG) Log.d(TAG, "isFrontCameraRecording: " + (recording ? "Is recording" : "Not recording"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 点击"后置摄像头是否正在录制"
     */
    public void isBackCameraRecording(){
        if (mDrService == null) {
            if (DEBUG) Log.d(TAG, "mDrService is null, isBackCameraRecording return");
            return;
        }
        if (DEBUG) Log.d(TAG, "isBackCameraRecording");
        try {
            boolean recording = mDrService.isBackCameraRecording();
            if (DEBUG) Log.d(TAG, "isBackCameraRecording: " + (recording ? "Is recording" : "Not recording"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    private void bindFotaService(){
        Intent intent = new Intent();
        intent.setAction("com.didi.drivingrecorder.fota.service.FotaService");
        intent.setPackage("com.didi.drivingrecorder.fota");
        bindService(intent, fotaConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection fotaConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.d(TAG, "fotaConnection onServiceConnected");
            didiFotaService = IDidiFotaService.Stub.asInterface(service);
            registerUpdateCountDownCallback();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.d(TAG, "fotaConnection onServiceDisconnected");
            didiFotaService = null;
            bindFotaService();
        }
    };

    private void registerUpdateCountDownCallback() {
        if (didiFotaService != null) {
            try {
                // AIDL接口：注册开始升级倒计时回调
                didiFotaService.registerUpdateCountDownCallback(countDownCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    private void unregisterUpdateCountDownCallback() {
        if (didiFotaService != null) {
            try {
                // AIDL接口：注销开始升级倒计时回调
                didiFotaService.unregisterUpdateCountDownCallback(countDownCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        didiFotaService = null;
    }

    private ICountDownCallback countDownCallback = new ICountDownCallback.Stub() {
        @Override
        public void onCountDown(long timeLeft) throws RemoteException {

            Message message = Message.obtain();
            message.what = 1;
            message.obj = timeLeft;
            mHandler.sendMessage(message);
        }
    };

    public void fetchUpdateInfo() {
        if (didiFotaService != null) {
            try {
                //updateinfoTv.setText("更新包获取中...");

                // AIDL接口：获取升级包信息
                didiFotaService.fetchUpdateInfo(new IFetchUpdateInfoCallBack.Stub() {
                    @Override
                    public void onCallBack(UpdateInfo updateInfo) throws RemoteException {
                        Message message = Message.obtain();
                        message.what = 2;
                        message.obj = updateInfo;
                        mHandler.sendMessage(message);
                    }
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

}
