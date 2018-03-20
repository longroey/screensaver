package com.rc.screensaver;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
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
import android.widget.TextClock;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by quanlong.luo on 2018-3-13.
 */

public class Utils {
    private static String TAG = "Utils";
    /** Types that may be used for clock displays. **/
    public static final String CLOCK_TYPE_DIGITAL = "digital";
    public static final String CLOCK_TYPE_ANALOG = "analog";



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
    public static void updateDate(
            String dateFormat, String dateFormatForAccessibility, View clock) {

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
                (dim ? context.getColor(R.color.dim_clock) : context.getColor(R.color.bright_clock)),
                PorterDuff.Mode.MULTIPLY));
        clockView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }

    /**
     * For screensavers whether to display data and alarm.
     */
    public static void setDisplayDataAlarmView(Context context, View clock) {
        TextView dateDisplay, alarmDisplay;
        dateDisplay = (TextView) clock.findViewById(R.id.date);
        alarmDisplay = (TextView) clock.findViewById(R.id.next_alarm);
        boolean display = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(ScreensaverSettingsActivity.KEY_DISPLAY_DATA_ALARM, false);
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
        private final long MOVE_DELAY = 20 * 1000; // SCREEN_SAVER_MOVE_DELAY;
        private final long SLIDE_TIME = 1 * 1000;
        private final long FADE_TIME = 1 * 1000;
        private final String SCREENSAVER_MODE_LOCATION = "0";
        private final String SCREENSAVER_MODE_CONNECTION = "1";
        private final String SCREENSAVER_MODE_CAMERA_STATUS = "2";
        private final String SCREENSAVER_MODE_LOOP = "3";

        private View mContentView;
        private View mSaverView;
        private final Handler mHandler;
        private Context mContext;
        TextView locationLabelView, connectionLabelView, cameraStatusLabelView;
        String locationLabel, connectionLabel, cameraStatusLabel;
        private final String LAST_LOCATION_LABEL = "last_location_label";
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

            locationLabelView = (TextView) mSaverView.findViewById(R.id.location_label);
            connectionLabelView = (TextView) mSaverView.findViewById(R.id.connection_label);
            cameraStatusLabelView = (TextView) mSaverView.findViewById(R.id.cammera_status_label);

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
            String defaultScreensaverMode = mContext.getResources().getString(R.string.default_screensaver_mode);
            String style = sharedPref.getString(ScreensaverSettingsActivity.KEY_SCREENSAVER_MODE, defaultScreensaverMode);
            Log.d(TAG, "style:" + style);
            if (style.equals(SCREENSAVER_MODE_LOOP)) {
                mIsLoopMode = true;
            } else {
                mIsLoopMode = false;
                if (style.equals(SCREENSAVER_MODE_LOCATION)) {
                    mMode = 0;
                } else if (style.equals(SCREENSAVER_MODE_CONNECTION)) {
                    mMode = 1;
                } else if (style.equals(SCREENSAVER_MODE_CAMERA_STATUS)) {
                    mMode = 2;
                }
            }
            Log.d(TAG, "mIsLoopMode:" + mIsLoopMode + "mMode:" + mMode);

            locationLabel = sharedPref.getString(LAST_LOCATION_LABEL, "");
            // TODO: 2018-3-15 设置对应label显示
            locationLabelView.setText(getLocationLabel(mContext));
            connectionLabelView.setText("Wifi:" + (isWifiOn(mContext) ? "ON" : "OFF") +
                    " WifiAp:" + (isWifiApOn(mContext) ? "ON" : "OFF") +
                    "\nGPS:" + (isGPSOn(mContext) ? "ON" : "OFF") +
                    " 4G:" + (getMobileNetworkType(mContext).equals("4G") ? "ON" : "OFF"));
            cameraStatusLabelView.setText("getCameraStatusLabel");
            // TODO: 2018-3-15
        }

        private void updateScreensaverView() {
            locationLabelView.setVisibility(View.GONE);
            connectionLabelView.setVisibility(View.GONE);
            cameraStatusLabelView.setVisibility(View.GONE);
            // TODO: 2018-3-15 设置对应label显示
            locationLabelView.setText(getLocationLabel(mContext));
            connectionLabelView.setText("Wifi:" + (isWifiOn(mContext) ? "ON" : "OFF") +
                    " WifiAp:" + (isWifiApOn(mContext) ? "ON" : "OFF") +
                    "\nGPS:" + (isGPSOn(mContext) ? "ON" : "OFF") +
                    " 4G:" + (getMobileNetworkType(mContext).equals("4G") ? "ON" : "OFF"));
            cameraStatusLabelView.setText("getCameraStatusLabel");
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


        private String getLocationLabel(Context context) {
            StringBuilder builder = new StringBuilder();
            Location location = null;
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();//Criteria类是设置定位的标准
            criteria.setPowerRequirement(Criteria.POWER_LOW);//设置低耗电
            criteria.setAltitudeRequired(false);//设置需要海拔
            criteria.setBearingAccuracy(Criteria.ACCURACY_COARSE);//设置COARSE精度标准
            criteria.setAccuracy(Criteria.ACCURACY_MEDIUM);//设置精度

            String provider = locationManager.getBestProvider(criteria, true);
            if (TextUtils.isEmpty(provider)) {
                //如果找不到最适合的定位，使用network, GPS定位
                List<String> prodiverlist = locationManager.getProviders(true);
                if (prodiverlist.contains(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                } else if (prodiverlist.contains(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                }
            }
            //高版本的权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: permission denied, so to do something.
                }
            }
            //获取最适合的定位方式的最后的定位权限
            Log.d(TAG, "provider is:" + provider);
            location = locationManager.getLastKnownLocation(provider);

            if (location != null) {
                // 设置不同经纬度测试
                //location.setLatitude(34.756292000);
                //location.setLongitude(107.028605000 );
                Log.d(TAG, "纬度：" + location.getLatitude() + "\n经度：" + location.getLongitude());
                try {
                    List<Address> addresses = new Geocoder(context).getFromLocation(
                            location.getLatitude(), location.getLongitude(),
                            3);
                    if (addresses.size() > 0) {
                        //取其中的一组地址 @ {
                        Address address = addresses.get(0);
                        //打印该组第一个地址
                        Log.d(TAG, "address" + address.getAddressLine(0));

                        if (address.getAdminArea() != null) {
                            builder.append(address.getAdminArea()).append(" ");//省
                            if (address.getLocality() != null) {
                                builder.append(address.getLocality()).append(" ");//市
                                if (address.getSubLocality() != null) {
                                    builder.append(address.getSubLocality()).append(" ");//区、县
                                /* if (address.getThoroughfare() != null) {
                                    builder.append(address.getThoroughfare()).append(" ");//路、街道
                                }*/
                                }
                            }
                        }
                        //取其中的一组地址 @ }
                    /* 遍历所有地址组 @ {
                    for (Address address : addresses) {
                        for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                            builder.append(address.getAddressLine(i)).append("\n");
                        }
                    }
                    //遍历所有地址组 @ } */
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.d(TAG, "location is null");
            }
            Log.d(TAG, builder.toString());
            if (!TextUtils.isEmpty(builder.toString())){
                locationLabel = builder.toString();
                Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                editor.putString(LAST_LOCATION_LABEL, locationLabel);
                editor.commit();
            }
            return locationLabel;
        }
    }


    public static boolean isGPSOn(Context context) {
        return Settings.Secure.isLocationProviderEnabled(context.getContentResolver(), LocationManager.GPS_PROVIDER);
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
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // 17
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
                Log.d(TAG, "Network getSubtype : " + Integer.valueOf(networkTypeID).toString());
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
                    case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // 17
                        networkType = "3G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_LTE:    //13
                    case TelephonyManager.NETWORK_TYPE_IWLAN: //18
                        networkType = "4G";
                        break;
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN: // 0
                    default:
                        networkType = "NETWORK_TYPE_UNKNOWN";
                        break;
                }
            }
        }
        Log.d(TAG, "Network Type : " + networkType);
        return networkType;
    }






    protected static String getLocationAddress(Context context) {
        //String address = "";
        StringBuilder builder = new StringBuilder();
        String provider = null;
        Location location = null;
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        List<String> prodiverlist = locationManager.getProviders(true);
        if (prodiverlist.contains(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else if (prodiverlist.contains(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        }
        Log.d(TAG, "is provider null:" + (provider == null));
        if (provider != null) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return "";
            }
            location = locationManager.getLastKnownLocation(provider);
        }
        Log.d(TAG, "is location null:" + (location == null));
        if (location != null) {

            builder.append("纬度：").append(location.getLatitude()).append("\n");
            builder.append("经度：").append(location.getLongitude()).append("\n");
            Log.d(TAG,builder.toString());
            try {
                List<Address> addresses = new Geocoder(context).getFromLocation(
                        location.getLatitude(), location.getLongitude(),
                        3);
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    // for (Address address : addresses) {
                    for (int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                        builder.append(address.getAddressLine(i)).append("\n");
                        // builder.append(address.getLocality()).append("\n");
                        // builder.append(address.getPostalCode()).append("\n");
                        // builder.append(address.getCountryName());
                    }
                    // }
                }
            }catch (Exception e) {
                e.printStackTrace();
            }


            //String url = "http://api.map.baidu.com/geocoder/v2/?ak=pPGNKs75nVZPloDFuppTLFO3WXebPgXg&callback=renderReverse&location="+latitude+","+longitude+"&output=json&pois=0";
            //new MyAsyncTask(url).execute();
        }
        return builder.toString();


    }

    /*class MyAsyncTask extends AsyncTask<Void,Void,Void> {
        String url = null;//要请求的网址
        String str = null;//服务器返回的数据
        String address = null;
        public MyAsyncTask(String url){
            this.url = url;
        }
        @Override
        protected Void doInBackground(Void... params) {
            str = GetHttpConnectionData.getData(url);
            return null;
        }
        @Override
        protected void onPostExecute(Void aVoid) {
            try {
                str = str.replace("renderReverse&&renderReverse","");
                str = str.replace("(","");
                str = str.replace(")","");
                JSONObject jsonObject = new JSONObject(str);
                JSONObject address = jsonObject.getJSONObject("result");
                String city = address.getString("formatted_address");
                String district = address.getString("sematic_description");
                tv_show.setText("当前位置："+city+district);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            super.onPostExecute(aVoid);
        }
    }
    */




}
