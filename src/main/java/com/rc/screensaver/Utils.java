package com.rc.screensaver;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Iterator;
import java.util.Locale;

/**
 * Created by quanlong.luo on 2018-3-13.
 */

public class Utils {
    private static String TAG = "Utils";
    public static final boolean DEBUG = true;

    public static void setTimeFormat(TextClock clock, int amPmFontSize) {
        if (clock != null) {
            clock.setFormat12Hour(get12ModeFormat(amPmFontSize));
            clock.setFormat24Hour(get24ModeFormat());
        }
    }

    public static CharSequence get12ModeFormat(int amPmFontSize) {
        String skeleton = "hma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        // Remove the am/pm
        if (amPmFontSize <= 0) {
            pattern.replaceAll("a", "").trim();
        }
        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");
        // Build a spannable so that the am/pm will be formatted
        int amPmPos = pattern.indexOf('a');
        if (amPmPos == -1) {
            return pattern;
        }
        Spannable sp = new SpannableString(pattern);
        sp.setSpan(new StyleSpan(Typeface.NORMAL), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new AbsoluteSizeSpan(amPmFontSize), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new TypefaceSpan("sans-serif"), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        return sp;
    }

    public static CharSequence get24ModeFormat() {
        String skeleton = "Hm";
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
    }

    public static void dimClockView(Context context, boolean dim, View clockView) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setColorFilter(new PorterDuffColorFilter(
                (dim ? context.getResources().getColor(R.color.dim_clock) : context.getResources().getColor(R.color.bright_clock)),
                PorterDuff.Mode.MULTIPLY));
        clockView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    /**
     * Runnable for use with screensaver and dream, to move the clock every minute.
     * registerViews() must be called prior to posting.
     */
    public static class ScreensaverMoveSaverRunnable implements Runnable {
        private final long MOVE_DELAY = 10 * 1000; // SCREEN_SAVER_MOVE_DELAY;
        private final long SLIDE_TIME = 2 * 1000;
        private final long FADE_TIME = 2 * 1000;
        private final String SCREENSAVER_MODE_UPDATE = "-1";
        private final String SCREENSAVER_MODE_LOCATION = "0";
        private final String SCREENSAVER_MODE_STATUS = "1";
        private final String SCREENSAVER_MODE_CAMERA = "2";
        private final String SCREENSAVER_MODE_LOOP = "3";

        private View mContentView, mSaverView;
        private ImageView mWeatherImg, mGpsStatus, mNetworkStatus, mNetworkType, mWifiStatus, mHotspotStatus, mMuteStatus, mMaincamStatus, mSubcamStatus;
        private LinearLayout mPrimaryLayout, mLocationLayout, mStatusLayout, mCameraLayout, mUpdateSystemLayout;
        private TextClock mClock;
        private TextView mLocationLabel, mMaincamLabel, mUpdateSystemTitle, mUpdateSystemSummary;
        private final Handler mHandler;
        private Context mContext;
        private SharedPreferences mSharedPref;
        private static final String IMAGE_URL = "image_url";
        private static final String LOCATION_LABEL = "location_label";
        private static final String SATELLITES_COUNT = "satellites_count";
        private static final String PHONE_SIGNAL_LEVEL = "phone_signal_level";
        private static final String MAIN_CAMERA_STATUS = "main_camera_status";
        private static final String SUB_CAMERA_STATUS = "sub_camera_status";

        private Typeface pingfangsc_regularTypeface, pingfangsc_semiboldTypeface, teko_regularTypeface;
        boolean mIsLoopMode;
        int mMode = 1;

        public ScreensaverMoveSaverRunnable(Context context, Handler handler) {
            mContext = context;
            mHandler = handler;
        }
        private boolean hasAnimation() {
            String style = mSharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_ANIMATION_STYLE, "none");
            if (!style.equals("none")) {
                return true;
            }
            return false;
        }

        private boolean isFadeAnimationStyle() {
            String style = mSharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_ANIMATION_STYLE, "");
            if (style.equals("fade")) {
                return true;
            }
            return false;
        }

        public void registerViews(View contentView, View saverView) {
            mContentView = contentView;
            mSaverView = saverView;

            mSharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String defaultScreensaverMode = mContext.getResources().getString(R.string.default_screensaver_mode);
            String mode = mSharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_MODE, defaultScreensaverMode);
            if (DEBUG) Log.d(TAG, "mode:" + mode);
            if (mode.equals(SCREENSAVER_MODE_LOOP)) {
                mIsLoopMode = true;
            } else {
                mIsLoopMode = false;
                if (mode.equals(SCREENSAVER_MODE_UPDATE)) {
                    mMode = -1;
                } else if (mode.equals(SCREENSAVER_MODE_LOCATION)) {
                    mMode = 0;
                } else if (mode.equals(SCREENSAVER_MODE_STATUS)) {
                    mMode = 1;
                } else if (mode.equals(SCREENSAVER_MODE_CAMERA)) {
                    mMode = 2;
                }
            }
            if (DEBUG) Log.d(TAG, "registerViews mIsLoopMode:" + mIsLoopMode + "mMode:" + mMode);

            mPrimaryLayout = (LinearLayout) mSaverView.findViewById(R.id.primary_layout);
            mLocationLayout = (LinearLayout) mSaverView.findViewById(R.id.location_layout);
            mStatusLayout = (LinearLayout) mSaverView.findViewById(R.id.status_layout);
            mCameraLayout = (LinearLayout) mSaverView.findViewById(R.id.camera_layout);
            mUpdateSystemLayout = (LinearLayout) mSaverView.findViewById(R.id.update_system_layout);

            mClock = (TextClock) mSaverView.findViewById(R.id.clock);

            mWeatherImg = (ImageView) mSaverView.findViewById(R.id.weather_image);
            mLocationLabel = (TextView) mSaverView.findViewById(R.id.location_label);
            mGpsStatus = (ImageView) mSaverView.findViewById(R.id.gps_status);

            mNetworkStatus = (ImageView) mSaverView.findViewById(R.id.network_status);
            mNetworkType = (ImageView) mSaverView.findViewById(R.id.network_type);
            mWifiStatus = (ImageView) mSaverView.findViewById(R.id.wifi_status);
            mHotspotStatus = (ImageView) mSaverView.findViewById(R.id.hotspot_status);
            mMuteStatus = (ImageView) mSaverView.findViewById(R.id.mute_status);

            mMaincamStatus = (ImageView) mSaverView.findViewById(R.id.maincam_status);
            mMaincamLabel = (TextView) mSaverView.findViewById(R.id.maincam_label);
            mSubcamStatus = (ImageView) mSaverView.findViewById(R.id.subcam_status);

            mUpdateSystemTitle = (TextView) mSaverView.findViewById(R.id.update_system_title);
            mUpdateSystemSummary = (TextView) mSaverView.findViewById(R.id.update_system_summary);

            pingfangsc_regularTypeface = Typeface.createFromAsset(mContext.getAssets(),"PingFangSC-Regular.ttf");
            pingfangsc_semiboldTypeface = Typeface.createFromAsset(mContext.getAssets(),"PingFangSC-SemiBold.ttf");
            teko_regularTypeface = Typeface.createFromAsset(mContext.getAssets(),"Teko-Regular.ttf");
            mClock.setTypeface(teko_regularTypeface);
            Utils.setTimeFormat(mClock, (int)mContext.getResources().getDimension(R.dimen.ampm_font_size));
            mLocationLabel.setTypeface(pingfangsc_regularTypeface);
            mMaincamLabel.setTypeface(pingfangsc_regularTypeface);
            mUpdateSystemTitle.setTypeface(pingfangsc_semiboldTypeface);
            mUpdateSystemSummary.setTypeface(pingfangsc_regularTypeface);

            updateScreensaverView();
        }

        private void updateScreensaverView() {
            mWeatherImg.setVisibility(View.GONE);
            mLocationLayout.setVisibility(View.GONE);
            mStatusLayout.setVisibility(View.GONE);
            mCameraLayout.setVisibility(View.GONE);

            updateLocationLayout();
            updateStatusLayout();
            updateCameraLayout();

            if (mIsLoopMode) {
                mMode = ++mMode % 3;
            }
            if (DEBUG) Log.d(TAG, "updateScreensaverView mIsLoopMode:" + mIsLoopMode + "mMode:" + mMode);
            if (mMode == -1) {
                mPrimaryLayout.setVisibility(View.GONE);
                mUpdateSystemLayout.setVisibility(View.VISIBLE);
            }else {
                mPrimaryLayout.setVisibility(View.VISIBLE);
                mUpdateSystemLayout.setVisibility(View.GONE);
                if (mMode == 0) {
                    mWeatherImg.setVisibility(View.VISIBLE);
                    mLocationLayout.setVisibility(View.VISIBLE);
                }
                if (mMode == 1) {
                    mStatusLayout.setVisibility(View.VISIBLE);
                }
                if (mMode == 2) {
                    mCameraLayout.setVisibility(View.VISIBLE);
                }
            }
            if (DEBUG) Log.d(TAG, "updateScreensaverView mMode:" + mMode);
        }

        @Override
        public void run() {
            long delay = MOVE_DELAY;
            boolean isFadeAnimation = isFadeAnimationStyle();
            if (mContentView == null || mSaverView == null) {
                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, delay);
                return;
            }

            final float xrange = mContentView.getWidth() - mSaverView.getWidth();
            final float yrange = mContentView.getHeight() - mSaverView.getHeight();
            if (DEBUG) Log.d(TAG, "mContentView Width:" + mContentView.getWidth() + " mContentView Height:" + mContentView.getHeight()
                    + "\nmSaverView Width:" + mSaverView.getWidth() + " mSaverView Height:" + mSaverView.getHeight()
                    + "\nxrange:" + xrange + " yrange:" + yrange);
            if (hasAnimation()) {
                if (xrange == 0 && yrange == 0) {
                    delay = 500; // back in a split second
                } else {
                    final int nextx = (int) (Math.random() * xrange);
                    final int nexty = (int) (Math.random() * yrange);

                    if (mSaverView.getAlpha() == 0f) {
                        // jump right there
                        mSaverView.setX(nextx);
                        mSaverView.setY(nexty);
                        ObjectAnimator.ofFloat(mSaverView, "alpha", 0f, 1f)
                                .setDuration(FADE_TIME)
                                .start();
                    } else {
                        AnimatorSet s = new AnimatorSet();
                        AnimatorSet e = new AnimatorSet();
                        Animator xMove = ObjectAnimator.ofFloat(mSaverView,
                                "x", mSaverView.getX(), nextx);
                        Animator yMove = ObjectAnimator.ofFloat(mSaverView,
                                "y", mSaverView.getY(), nexty);

                        Animator xShrink = ObjectAnimator.ofFloat(mSaverView, "scaleX", 1f, 0.9f);
                        Animator xGrow = ObjectAnimator.ofFloat(mSaverView, "scaleX", 0.9f, 1f);

                        Animator yShrink = ObjectAnimator.ofFloat(mSaverView, "scaleY", 1f, 0.9f);
                        Animator yGrow = ObjectAnimator.ofFloat(mSaverView, "scaleY", 0.9f, 1f);
                        AnimatorSet shrink = new AnimatorSet();
                        shrink.play(xShrink).with(yShrink);
                        AnimatorSet grow = new AnimatorSet();
                        grow.play(xGrow).with(yGrow);

                        Animator fadeout = ObjectAnimator.ofFloat(mSaverView, "alpha", 1f, 0f);
                        Animator fadein = ObjectAnimator.ofFloat(mSaverView, "alpha", 0f, 1f);


                        if (isFadeAnimation) {
                            AccelerateInterpolator accel = new AccelerateInterpolator();
                            DecelerateInterpolator decel = new DecelerateInterpolator();

                            shrink.setDuration(FADE_TIME / 2).setInterpolator(accel);
                            fadeout.setDuration(FADE_TIME / 2).setInterpolator(accel);
                            grow.setDuration(FADE_TIME / 2).setInterpolator(decel);
                            fadein.setDuration(FADE_TIME / 2).setInterpolator(decel);
                            s.play(shrink);
                            s.play(fadeout);
                            s.play(xMove.setDuration(0));
                            s.play(yMove.setDuration(0));
                            s.start();
                            updateScreensaverView();
                            e.play(fadein);
                            e.play(grow);
                            e.start();
                        } else {
                            s.play(xMove).with(yMove);
                            s.setDuration(SLIDE_TIME / 2);
                            s.play(shrink.setDuration(SLIDE_TIME / 2));
                            s.start();
                            updateScreensaverView();
                            e.setDuration(SLIDE_TIME / 2);
                            e.play(grow.setDuration(SLIDE_TIME / 2));
                            e.start();
                        }
                    }
                }
            } else {
                mSaverView.setAlpha(1);
                updateScreensaverView();
            }
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delay);
        }

        private void updateLocationLayout() {
            String imgUrl = mSharedPref.getString(IMAGE_URL, "");
            if (!TextUtils.isEmpty(imgUrl)) {
                new WeatherImgAsyncTask().execute(imgUrl);
            }

            String address = mSharedPref.getString(LOCATION_LABEL, "");
            if (!TextUtils.isEmpty(address)) {
                mLocationLabel.setText(address);
            }

            updateGpsStatus();
        }

        private void updateStatusLayout() {
            updatePhoneSignal();
            updateWifiSignal();
            if (isHotspotOn(mContext)) {
                mHotspotStatus.setImageResource(R.drawable.hotspot);
            } else {
                //mHotspotStatus.setVisibility(View.GONE);
                mHotspotStatus.setImageResource(R.drawable.hotspot);
                mHotspotStatus.setAlpha(0.3f);
            }

            if (isMute(mContext)) {
                mMuteStatus.setImageResource(R.drawable.mute_on);
            } else {
                mMuteStatus.setImageResource(R.drawable.mute_off);
            }
        }

        private void updateCameraLayout() {
            int mainCameraStatus = mSharedPref.getInt(MAIN_CAMERA_STATUS, 0);
            int subCameraStatus = mSharedPref.getInt(SUB_CAMERA_STATUS, 0);

            if (mainCameraStatus == 0) {
                mMaincamStatus.setVisibility(View.GONE);
            } else if (mainCameraStatus == 1) {
                mMaincamStatus.setVisibility(View.VISIBLE);
            } else if (mainCameraStatus == 2) {
                // the status is reserve
            }

            if (subCameraStatus == 0) {
                mSubcamStatus.setVisibility(View.GONE);
            } else if (subCameraStatus == 1) {
                mSubcamStatus.setVisibility(View.VISIBLE);
            } else if (subCameraStatus == 2) {
                // the status is reserve
            }
        }


        private void updateGpsStatus() {
            if (!isGPSOn(mContext)){
                mGpsStatus.setImageResource(R.drawable.location_signal);
                mGpsStatus.setAlpha(0.3f);
            } else {
                int satellitesCount = mSharedPref.getInt(SATELLITES_COUNT, 0);
                if (satellitesCount > 10) {
                    mGpsStatus.setImageResource(R.drawable.location_signal_high);
                } else {
                    mGpsStatus.setImageResource(R.drawable.location_signal_low);
                }
            }
        }

        private void updatePhoneSignal() {
            if (!ishasSimCard(mContext)) {
                mNetworkStatus.setImageResource(R.drawable.network_signal_no_sim);
                mNetworkType.setVisibility(View.GONE);
            } else {
                int signalLevel = mSharedPref.getInt(PHONE_SIGNAL_LEVEL, 0);
                if (signalLevel == 0) {
                    mNetworkStatus.setImageResource(R.drawable.network_signal_0);
                } else if (signalLevel == 1) {
                    mNetworkStatus.setImageResource(R.drawable.network_signal_1);
                } else if (signalLevel == 2) {
                    mNetworkStatus.setImageResource(R.drawable.network_signal_2);
                } else if (signalLevel == 3) {
                    mNetworkStatus.setImageResource(R.drawable.network_signal_3);
                } else if (signalLevel == 4) {
                    mNetworkStatus.setImageResource(R.drawable.network_signal_4);
                }

                //String networkType = getNetworkType(mContext);
                String networkType = getMobileNetworkType(mContext);
                if (networkType.equals("2G")) {
                    mNetworkType.setImageResource(R.drawable.network_type_2g);
                } else if (networkType.equals("3G")) {
                    mNetworkType.setImageResource(R.drawable.network_type_3g);
                } else if (networkType.equals("4G")) {
                    mNetworkType.setImageResource(R.drawable.network_type_4g);
                } else {
                    mNetworkType.setVisibility(View.GONE);
                }
            }
        }

        private void updateWifiSignal() {
            int rssi = Integer.MAX_VALUE;
            int level = -1;
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            NetworkInfo networkInfo = ((ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                    && networkInfo != null && networkInfo.isConnected() && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                rssi = wifiInfo.getRssi();
                if (DEBUG) Log.d(TAG, "rssi:" + rssi);
                if (rssi == Integer.MAX_VALUE) {
                    level = -1;
                } else {
                    level = WifiManager.calculateSignalLevel(rssi, 4);
                }
                if (DEBUG) Log.d(TAG, "level:" + level);

                if (level == 0) {
                    mWifiStatus.setImageResource(R.drawable.wifi_signal_0);
                } else if (level == 1){
                    mWifiStatus.setImageResource(R.drawable.wifi_signal_1);
                } else if (level == 2){
                    mWifiStatus.setImageResource(R.drawable.wifi_signal_2);
                } else if (level == 3){
                    mWifiStatus.setImageResource(R.drawable.wifi_signal_3);
                }
            } else {
                //mWifiStatus.setVisibility(View.GONE);
                mWifiStatus.setImageResource(R.drawable.wifi_signal_3);
                mWifiStatus.setAlpha(0.3f);
            }
        }

        private class WeatherImgAsyncTask extends AsyncTask<String, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(String... strings) {
                String imgUrl = strings[0];
                Bitmap bitmap = null;
                try {
                    URL url = new URL(imgUrl);
                    InputStream in = url.openStream();
                    bitmap = BitmapFactory.decodeStream(in);
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                mWeatherImg.setImageBitmap(bitmap);
            }
        }

    }

    public static boolean isGPSOn(Context context) {
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;
        try {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    }


    public static boolean isMute(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            Method method = audioManager.getClass().getDeclaredMethod("isMasterMute");
            return (boolean) method.invoke(audioManager);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isHotspotOn(Context context) {
        try {
            WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = manager.getClass().getDeclaredMethod("getWifiApState");
            int state = (int) method.invoke(manager);
            Field field = manager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
            int value = (int) field.get(manager);
            if (state == value) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getWifiApSSID(Context context) {
        String wifiApSSID = "";
        try {
            WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            Method method = manager.getClass().getDeclaredMethod("getWifiApConfiguration");
            WifiConfiguration configuration = (WifiConfiguration) method.invoke(manager);
            wifiApSSID = configuration.SSID;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wifiApSSID;
    }

    public static boolean ishasSimCard(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int simState = telephonyManager.getSimState();
        boolean result = true;
        switch (simState) {
            case TelephonyManager.SIM_STATE_ABSENT:
                result = false; // no sim card
                break;
            case TelephonyManager.SIM_STATE_UNKNOWN:
                result = false;
                break;
        }
        if (DEBUG) Log.d(TAG, "ishasSimCard:" + (result ? "hasSimCard" : "noSimCard"));
        return result;
    }

    public static String getMobileNetworkType(Context context) {
        String mobileNetworkType = "MOBILE_NETWORK_TYPE_UNKNOWN";
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            switch (telephonyManager.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_GPRS: // 1
                case TelephonyManager.NETWORK_TYPE_EDGE: // 2
                case TelephonyManager.NETWORK_TYPE_CDMA: // 4
                case TelephonyManager.NETWORK_TYPE_1xRTT: // 7
                case TelephonyManager.NETWORK_TYPE_IDEN: // 11
                case TelephonyManager.NETWORK_TYPE_GSM: //16
                    mobileNetworkType = "2G";
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS: // 3
                case TelephonyManager.NETWORK_TYPE_EVDO_0: // 5
                case TelephonyManager.NETWORK_TYPE_EVDO_A: // 6
                case TelephonyManager.NETWORK_TYPE_HSDPA: // 8
                case TelephonyManager.NETWORK_TYPE_HSUPA: // 9
                case TelephonyManager.NETWORK_TYPE_HSPA: // 10
                case TelephonyManager.NETWORK_TYPE_EVDO_B: // 12
                case TelephonyManager.NETWORK_TYPE_EHRPD:  // 14
                case TelephonyManager.NETWORK_TYPE_HSPAP:  // 15
                    //Current SDK not supported  TD-SCDMA networkType is 17
                    //case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // 17
                    mobileNetworkType = "3G";
                    break;
                case TelephonyManager.NETWORK_TYPE_LTE:    // 13
                case TelephonyManager.NETWORK_TYPE_IWLAN: // 18
                    mobileNetworkType = "4G";
                    break;
                case TelephonyManager.NETWORK_TYPE_UNKNOWN: // 0
                default:
                    mobileNetworkType = "MOBILE_NETWORK_TYPE_UNKNOWN";
                    break;
            }
        }
        return mobileNetworkType;
    }


    public static String getNetworkType(Context context) {
        String networkType = "NETWORK_TYPE_UNKNOWN";
        NetworkInfo networkInfo = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                networkType = "WIFI";
            } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                String subTypeName = networkInfo.getSubtypeName();
                if (DEBUG) Log.d(TAG, "Network getSubtypeName : " + subTypeName);
                int networkTypeID = networkInfo.getSubtype();
                if (DEBUG) Log.d(TAG, "Network getSubtype : " + networkTypeID);
                switch (networkTypeID) {
                    case TelephonyManager.NETWORK_TYPE_GPRS: // 1
                    case TelephonyManager.NETWORK_TYPE_EDGE: // 2
                    case TelephonyManager.NETWORK_TYPE_CDMA: // 4
                    case TelephonyManager.NETWORK_TYPE_1xRTT: // 7
                    case TelephonyManager.NETWORK_TYPE_IDEN: // 11
                    case TelephonyManager.NETWORK_TYPE_GSM: //16
                        networkType = "2G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_UMTS: // 3
                    case TelephonyManager.NETWORK_TYPE_EVDO_0: // 5
                    case TelephonyManager.NETWORK_TYPE_EVDO_A: // 6
                    case TelephonyManager.NETWORK_TYPE_HSDPA: // 8
                    case TelephonyManager.NETWORK_TYPE_HSUPA: // 9
                    case TelephonyManager.NETWORK_TYPE_HSPA: // 10
                    case TelephonyManager.NETWORK_TYPE_EVDO_B: // 12
                    case TelephonyManager.NETWORK_TYPE_EHRPD:  // 14
                    case TelephonyManager.NETWORK_TYPE_HSPAP:  // 15
                        //Current SDK not supported  TD-SCDMA networkType is 17
                        //case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // 17
                        networkType = "3G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_LTE:    //13
                    case TelephonyManager.NETWORK_TYPE_IWLAN: //18
                        networkType = "4G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN: // 0
                        networkType = "NETWORK_TYPE_UNKNOWN";
                        break;
                    default:
                        if (subTypeName.equalsIgnoreCase("TD-SCDMA")
                                || subTypeName.equalsIgnoreCase("WCDMA")
                                || subTypeName.equalsIgnoreCase("CDMA2000")) {
                            networkType = "3G";
                        } else {
                            networkType = "NETWORK_TYPE_UNKNOWN";
                        }
                        break;
                }
            }
        }
        if (DEBUG) Log.d(TAG, "Network Type : " + networkType);
        return networkType;
    }

    public static class ScreensaverReceiver extends BroadcastReceiver {
        private String imgUrl, address;
        private int mainCameraStatus, subCameraStatus;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "ScreensaverReceiver onReceive, action: " + action);

            if (action == null) {
                return;
            }
            Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();

            if (action.equals("com.didi.recorder.action.SYNC_WEATHER")) {
                imgUrl = intent.getStringExtra("imgUrl");
                address = intent.getStringExtra("address");
                if (DEBUG) Log.d(TAG, "imgUrl:" + imgUrl + "\naddress" + address);
                if (!TextUtils.isEmpty(imgUrl)){
                    editor.putString(ScreensaverMoveSaverRunnable.IMAGE_URL, imgUrl);
                }
                if (!TextUtils.isEmpty(address)){
                    editor.putString(ScreensaverMoveSaverRunnable.LOCATION_LABEL, address);
                }
            }

            if (action.equals("com.didi.recorder.action.SYNC_RECORDING_STATUS")) {
                mainCameraStatus = intent.getExtras().getInt("KEY_CAMERA_0");
                subCameraStatus = intent.getExtras().getInt("KEY_CAMERA_1");
                if (DEBUG) Log.d(TAG, "mainCameraStatus:" + mainCameraStatus + " subCameraStatus" + subCameraStatus);
                editor.putInt(ScreensaverMoveSaverRunnable.MAIN_CAMERA_STATUS, mainCameraStatus);
                editor.putInt(ScreensaverMoveSaverRunnable.SUB_CAMERA_STATUS, subCameraStatus);
            }

            editor.commit();
        }
    }


    static class MobilePhoneStateListener extends PhoneStateListener {
        int level = -1;
        Context mContext;
        public MobilePhoneStateListener(Context context) {
            mContext = context;
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            Method method = null;
            try {
                method = signalStrength.getClass().getDeclaredMethod("getLevel");
                level = (int) method.invoke(signalStrength);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (DEBUG) Log.d(TAG, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                    ((signalStrength == null) ? "" : (" level=" + level)));
            Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
            editor.putInt(ScreensaverMoveSaverRunnable.PHONE_SIGNAL_LEVEL, level);
            editor.commit();
        }
    }


    static class GpsStatusListener implements GpsStatus.Listener {
        Context mContext;
        LocationManager gpsLocationManager;
        public GpsStatusListener(Context context) {
            mContext = context;
            gpsLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        }
        @Override
        public void onGpsStatusChanged(int event) {
            switch (event) {
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    break;
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    GpsStatus gpsStatus = gpsLocationManager.getGpsStatus(null);
                    int maxSatellites = gpsStatus.getMaxSatellites();
                    Iterator<GpsSatellite> iters = gpsStatus.getSatellites().iterator();
                    int count = 0;
                    while (iters.hasNext() && count <= maxSatellites) {
                        iters.next();
                        count ++;
                    }
                    Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                    editor.putInt(ScreensaverMoveSaverRunnable.SATELLITES_COUNT, count);
                    editor.commit();
                    break;
                case GpsStatus.GPS_EVENT_STARTED:
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    break;
            }
        }
    };

}
