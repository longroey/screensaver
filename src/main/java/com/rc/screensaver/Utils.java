package com.rc.screensaver;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.AlarmManager;
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
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
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
import android.widget.TextClock;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by quanlong.luo on 2018-3-13.
 */

public class Utils {
    private static String TAG = "Utils";

    /***
     * Formats the time in the TextClock according to the Locale with a special
     * formatting treatment for the am/pm label.
     * @param clock - TextClock to format
     * @param amPmFontSize - size of the am/pm label since it is usually smaller
     *        than the clock time size.
     */
    public static void setTimeFormat(TextClock clock, int amPmFontSize) {
        if (clock != null) {
            // Get the best format for 12 hours mode according to the locale
            clock.setFormat12Hour(get12ModeFormat(amPmFontSize));
            // Get the best format for 24 hours mode according to the locale
            clock.setFormat24Hour(get24ModeFormat());
        }
    }

    /***
     * @param amPmFontSize - size of am/pm label (label removed is size is 0).
     * @return format string for 12 hours mode time
     */
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

    /** Clock views can call this to refresh their date. **/
    public static void updateDate(String dateFormat, String dateFormatForAccessibility, View clock) {
        Date now = new Date();
        TextView dateDisplay;
        dateDisplay = (TextView) clock.findViewById(R.id.date);
        if (dateDisplay != null) {
            final Locale l = Locale.getDefault();
            String fmt = DateFormat.getBestDateTimePattern(l, dateFormat);
            SimpleDateFormat sdf = new SimpleDateFormat(fmt, l);
            dateDisplay.setText(sdf.format(now));
            fmt = DateFormat.getBestDateTimePattern(l, dateFormatForAccessibility);
            sdf = new SimpleDateFormat(fmt, l);
            dateDisplay.setContentDescription(sdf.format(now));
        }
    }

    /** Clock views can call this to refresh their alarm to the next upcoming value. **/
    public static void refreshAlarm(Context context, View clock) {
        final String nextAlarm = getNextAlarm(context);
        TextView nextAlarmView;
        nextAlarmView = (TextView) clock.findViewById(R.id.next_alarm);
        if (!TextUtils.isEmpty(nextAlarm) && nextAlarmView != null) {
            nextAlarmView.setText(
                    context.getString(R.string.control_set_alarm_with_existing, nextAlarm));
            nextAlarmView.setContentDescription(context.getResources().getString(
                    R.string.next_alarm_description, nextAlarm));
        }
    }

    /**
     * @return The next alarm from {@link AlarmManager}
     */
    public static String getNextAlarm(Context context) {
        String timeString = null;
        final AlarmManager.AlarmClockInfo info = ((AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE)).getNextAlarmClock();
        if (info != null) {
            final long triggerTime = info.getTriggerTime();
            final Calendar alarmTime = Calendar.getInstance();
            alarmTime.setTimeInMillis(triggerTime);
            timeString = getFormattedTime(context, alarmTime);
        }
        return timeString;
    }

    public static String getFormattedTime(Context context, Calendar time) {
        String skeleton = DateFormat.is24HourFormat(context) ? "EHm" : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return (String) DateFormat.format(pattern, time);
    }

    /**
     * For screensavers to dim the lights if necessary.
     */
    public static void dimClockView(Context context, boolean dim, View clockView) {
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setColorFilter(new PorterDuffColorFilter(
                (dim ? context.getResources().getColor(R.color.dim_clock) : context.getResources().getColor(R.color.bright_clock)),
                PorterDuff.Mode.MULTIPLY));
        clockView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    /**
     * For screensavers whether to display date and alarm.
     */
    public static void setDisplayDateAlarmView(Context context, View clock) {
        TextView dateDisplay, alarmDisplay;
        dateDisplay = (TextView) clock.findViewById(R.id.date);
        alarmDisplay = (TextView) clock.findViewById(R.id.next_alarm);
        boolean display = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ScreensaverSettingsActivity.KEY_DISPLAY_DATE_ALARM, false);
        dateDisplay.setVisibility(display ? View.VISIBLE : View.GONE);
        if (display && !TextUtils.isEmpty(getNextAlarm(context))) {
            alarmDisplay.setVisibility(View.VISIBLE);
        } else {
            alarmDisplay.setVisibility(View.GONE);
        }
    }

    /**
     * Runnable for use with screensaver and dream, to move the clock every minute.
     * registerViews() must be called prior to posting.
     */
    public static class ScreensaverMoveSaverRunnable implements Runnable {
        private final long MOVE_DELAY = 10 * 1000; // SCREEN_SAVER_MOVE_DELAY;
        private final long SLIDE_TIME = 1 * 1000;
        private final long FADE_TIME = 1 * 1000;
        private final String SCREENSAVER_MODE_LOCATION = "0";
        private final String SCREENSAVER_MODE_CONNECTION = "1";
        private final String SCREENSAVER_MODE_CAMERA_STATUS = "2";
        private final String SCREENSAVER_MODE_LOOP = "3";

        private View mContentView;
        private View mSaverView;
        TextView locationLabelView, connectionLabelView, cameraStatusLabelView;
        ImageView weatherImg;
        private final Handler mHandler;
        private Context mContext;
        String locationLabel, connectionLabel, cameraStatusLabel;
        private static final String SCREENSAVER_IMAGE_URL = "screensaver_image_url";
        private static final String SCREENSAVER_LOCATION_LABEL = "screensaver_location_label";
        private static final String MAIN_CAMERA_STATUS = "main_camera_status";
        private static final String SUB_CAMERA_STATUS = "sub_camera_status";

        boolean mIsLoopMode;
        int mMode = 0;
        private static TimeInterpolator mSlowStartWithBrakes;

        public ScreensaverMoveSaverRunnable(Context context, Handler handler) {
            mContext = context;
            mHandler = handler;

            mSlowStartWithBrakes = new TimeInterpolator() {
                @Override
                public float getInterpolation(float x) {
                    return (float) (Math.cos((Math.pow(x, 3) + 1) * Math.PI) / 2.0f) + 0.5f;
                }
            };
        }

        /**
         * For the screensaver animation is fade or slide style
         */
        public boolean isFadeAnimationStyle() {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String style = sharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_ANIMATION_STYLE, "");
            if (style.equals("fade")) {
                return true;
            }
            return false;
        }

        public void registerViews(View contentView, View saverView) {
            mContentView = contentView;
            mSaverView = saverView;

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String defaultScreensaverMode = mContext.getResources().getString(R.string.default_screensaver_mode);
            String mode = sharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_MODE, defaultScreensaverMode);
            Log.d(TAG, "mode:" + mode);
            if (mode.equals(SCREENSAVER_MODE_LOOP)) {
                mIsLoopMode = true;
            } else {
                mIsLoopMode = false;
                if (mode.equals(SCREENSAVER_MODE_LOCATION)) {
                    mMode = 0;
                } else if (mode.equals(SCREENSAVER_MODE_CONNECTION)) {
                    mMode = 1;
                } else if (mode.equals(SCREENSAVER_MODE_CAMERA_STATUS)) {
                    mMode = 2;
                }
            }
            Log.d(TAG, "mIsLoopMode:" + mIsLoopMode + "mMode:" + mMode);

            weatherImg = (ImageView) mSaverView.findViewById(R.id.weather_image);
            locationLabelView = (TextView) mSaverView.findViewById(R.id.location_label);
            connectionLabelView = (TextView) mSaverView.findViewById(R.id.connection_label);
            cameraStatusLabelView = (TextView) mSaverView.findViewById(R.id.cammera_status_label);

            updateScreensaverView();
        }

        private void updateScreensaverView() {
            locationLabelView.setVisibility(View.GONE);
            connectionLabelView.setVisibility(View.GONE);
            cameraStatusLabelView.setVisibility(View.GONE);
            // TODO: 2018-3-15 设置对应label显示
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String imgUrl = sharedPref.getString(SCREENSAVER_IMAGE_URL, "");
            if (!TextUtils.isEmpty(imgUrl)) {
                new WeatherImgAsyncTask().execute(imgUrl);
            }

            String address = sharedPref.getString(SCREENSAVER_LOCATION_LABEL, "");
            locationLabelView.setText(address);

            connectionLabelView.setText(getConnectionStatusLabel());

            int mainCameraStatus = sharedPref.getInt(MAIN_CAMERA_STATUS, 0);
            int subCameraStatus = sharedPref.getInt(SUB_CAMERA_STATUS, 0);
            Drawable mainCameraDrawable = null;
            Drawable subCameraDrawable = null;

            if (mainCameraStatus == 0) {
                mainCameraDrawable = null;
            } else if (mainCameraStatus == 1) {
                mainCameraDrawable = mContext.getResources().getDrawable(R.drawable.main_camera);
            } else if (mainCameraStatus == 2) {
                // the status is reserve
            }

            if (subCameraStatus == 0) {
                subCameraDrawable = null;
            } else if (subCameraStatus == 1) {
                subCameraDrawable = mContext.getResources().getDrawable(R.drawable.sub_camera);
            } else if (subCameraStatus == 2) {
                // the status is reserve
            }
            cameraStatusLabelView.setCompoundDrawablesWithIntrinsicBounds(mainCameraDrawable, null, subCameraDrawable, null);
            cameraStatusLabelView.setText(getCameraStatusLabel(mainCameraStatus, subCameraStatus));
            // TODO: 2018-3-15

            if (mIsLoopMode) {
                mMode = ++mMode % 3;
            }
            Log.d(TAG, "========>mIsLoopMode:" + mIsLoopMode + "mMode:" + mMode);
            if (mMode == 0) {
                locationLabelView.setVisibility(View.VISIBLE);
            }
            if (mMode == 1) {
                connectionLabelView.setVisibility(View.VISIBLE);
            }
            if (mMode == 2) {
                cameraStatusLabelView.setVisibility(View.VISIBLE);
            }
            Log.d(TAG, "mMode:" + mMode);
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
            Log.d(TAG, "mContentView Width:" + mContentView.getWidth() + " mContentView Height:" + mContentView.getHeight()
                    + "\nmSaverView Width:" + mSaverView.getWidth() + " mSaverView Height:" + mSaverView.getHeight()
                    + "\nxrange:" + xrange + " yrange:" + yrange);

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

                        shrink.setDuration(FADE_TIME).setInterpolator(accel);
                        fadeout.setDuration(FADE_TIME).setInterpolator(accel);
                        grow.setDuration(FADE_TIME).setInterpolator(decel);
                        fadein.setDuration(FADE_TIME).setInterpolator(decel);
                        s.play(shrink);
                        s.play(fadeout);
                        s.play(xMove.setDuration(0)).after(FADE_TIME);
                        s.play(yMove.setDuration(0)).after(FADE_TIME);
                        s.play(fadein).after(FADE_TIME);
                        s.play(grow).after(FADE_TIME);
                    } else {
                        s.play(xMove).with(yMove);
                        s.setDuration(SLIDE_TIME);
                        s.play(shrink.setDuration(SLIDE_TIME / 2));
                        s.play(grow.setDuration(SLIDE_TIME / 2)).after(shrink);
                        s.setInterpolator(mSlowStartWithBrakes);
                    }
                    s.start();
                }

            }
            updateScreensaverView();
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delay);
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
                weatherImg.setImageBitmap(bitmap);
            }
        }

        private String getConnectionStatusLabel() {
            String connectionStatus = mContext.getString(R.string.connection_status_label,
                    isWifiOn(mContext) ? mContext.getString(R.string.status_on) : mContext.getString(R.string.status_off),
                    isWifiApOn(mContext) ?  mContext.getString(R.string.status_on) : mContext.getString(R.string.status_off),
                    isGPSOn(mContext) ?  mContext.getString(R.string.status_on) : mContext.getString(R.string.status_off));
            if (!getMobileNetworkType(mContext).equals("MOBILE_NETWORK_TYPE_UNKNOWN")) {
                connectionStatus = connectionStatus + mContext.getString(R.string.mobilenetworktype) + getMobileNetworkType(mContext);
            }
            return connectionStatus;
        }

        private String getCameraStatusLabel(int mainCameraStatus, int subCameraStatus) {
            return mContext.getString(R.string.camera_status_label,
                    mainCameraStatus == 1 ? mContext.getString(R.string.camera_status_recording) : mContext.getString(R.string.camera_status_unrecording),
                    subCameraStatus == 1 ? mContext.getString(R.string.camera_status_recording) : mContext.getString(R.string.camera_status_unrecording));
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

    public static boolean isWifiOn(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            return true;
        }
        return false;
    }

    public static String getWifiSignalStrength(Context context) {
        String signalStrength = "";
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        int strength = wifiInfo.getRssi();
        if (strength > -50) {
            signalStrength = context.getResources().getString(R.string.wifi_signal_strength_strong);
        } else if (strength > -70) {
            signalStrength = context.getResources().getString(R.string.wifi_signal_strength_general);
        } else {
            signalStrength = context.getResources().getString(R.string.wifi_signal_strength_weak);
        }
        return signalStrength;
    }

    public static boolean isWifiApOn(Context context) {
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
                Log.d(TAG, "Network getSubtypeName : " + subTypeName);
                int networkTypeID = networkInfo.getSubtype();
                Log.d(TAG, "Network getSubtype : " + networkTypeID);
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
        Log.d(TAG, "Network Type : " + networkType);
        return networkType;
    }

    public static class ScreensaverReceiver extends BroadcastReceiver {
        private String imgUrl, address;
        private int mainCameraStatus, subCameraStatus;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.v(TAG, "ScreensaverReceiver onReceive, action: " + action);

            if (action == null) {
                return;
            }
            Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();

            if (action.equals("com.didi.recorder.action.SYNC_WEATHER")) {
                imgUrl = intent.getStringExtra("imgUrl");
                address = intent.getStringExtra("address");
                Log.d(TAG, "imgUrl:" + imgUrl + "\naddress" + address);
                if (!TextUtils.isEmpty(imgUrl)){
                    editor.putString(ScreensaverMoveSaverRunnable.SCREENSAVER_IMAGE_URL, imgUrl);
                }
                if (!TextUtils.isEmpty(address)){
                    editor.putString(ScreensaverMoveSaverRunnable.SCREENSAVER_LOCATION_LABEL, address);
                }
            }

            if (action.equals("com.didi.recorder.action.SYNC_RECORDING_STATUS")) {
                mainCameraStatus = intent.getExtras().getInt("KEY_CAMERA_0");
                subCameraStatus = intent.getExtras().getInt("KEY_CAMERA_1");
                Log.d(TAG, "mainCameraStatus:" + mainCameraStatus + " subCameraStatus" + subCameraStatus);
                editor.putInt(ScreensaverMoveSaverRunnable.MAIN_CAMERA_STATUS, mainCameraStatus);
                editor.putInt(ScreensaverMoveSaverRunnable.SUB_CAMERA_STATUS, subCameraStatus);
            }

            editor.commit();
        }
    }

}
