package com.rc.screensaver;

import android.content.Context;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.dreams.DreamService;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.util.Log;
import android.view.View;
import android.widget.TextClock;

import com.rc.screensaver.Utils.GpsStatusListener;
import com.rc.screensaver.Utils.MobilePhoneStateListener;
import com.rc.screensaver.Utils.ScreensaverMoveSaverRunnable;


public class Screensaver extends DreamService {
    private static final String TAG = "Screensaver";
    private static final boolean DEBUG = true;

    public static final int DEFAULT_SCREENSAVER_TIMEOUT =  Integer.MAX_VALUE;// 5 * 60 * 1000;
    public static final int ORIENTATION_CHANGE_DELAY_MS = 200;

    private TelephonyManager mTelephonyManager;
    private MobilePhoneStateListener mListener;
    private LocationManager gpsLocationManager;
    private GpsStatusListener mGpsStatusListener;
    private View mContentView, mSaverView;

    private final Handler mHandler = new Handler();

    private final ScreensaverMoveSaverRunnable mMoveSaverRunnable;

    private final Runnable mQuitScreensaver = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    public Screensaver() {
        if (DEBUG) Log.d(TAG, "Screensaver allocated");
        mMoveSaverRunnable = new ScreensaverMoveSaverRunnable(this, mHandler);

    }

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Screensaver created");
        super.onCreate();
        setTheme(R.style.ScreensaverTheme);
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
        if (DEBUG) Log.d(TAG, "Screensaver attached to window");
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
    }

    @Override
    public void onDetachedFromWindow() {
        if (DEBUG) Log.d(TAG, "Screensaver detached from window");
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mMoveSaverRunnable);
        mHandler.removeCallbacks(mQuitScreensaver);
        mTelephonyManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
    }

    private void layoutClockSaver() {
        setContentView(R.layout.screensaver);
        mSaverView = findViewById(R.id.screensaver_view);
        mContentView = (View) mSaverView.getParent();
        boolean dimNightMode = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(ScreensaverSettingsActivity.KEY_NIGHT_MODE, false);
        Utils.dimClockView(this, dimNightMode, mSaverView);
        setScreenBright(!dimNightMode);

        //mSaverView.setAlpha(0);
        mMoveSaverRunnable.registerViews(mContentView, mSaverView);
    }

}
