/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.TransactionTooLargeException;
import android.os.UserHandle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.animation.Interpolator;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.launcher3.config.FeatureFlags;

import org.zimmob.zimlx.ZimAppKt;
import org.zimmob.zimlx.ZimLauncher;
import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.backup.RestoreBackupActivity;
import org.zimmob.zimlx.override.OverviewState;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
public final class Utilities {

    public static final boolean ATLEAST_P =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    public static final boolean ATLEAST_OREO_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
    public static final boolean ATLEAST_OREO =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    public static final boolean ATLEAST_NOUGAT_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
    public static final boolean ATLEAST_NOUGAT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    public static final boolean ATLEAST_MARSHMALLOW =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    public static final boolean ATLEAST_LOLLIPOP_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;


    //public static boolean HIDDEN_APIS_ALLOWED = !ATLEAST_P || HiddenApiCompat.tryAccess();

    public static final int SINGLE_FRAME_MS = 16;

    /**
     * Indicates if the device has a debug build. Should only be used to store additional info or
     * add extra logging and not for changing the app behavior.
     */
    public static final boolean IS_DEBUG_DEVICE = Build.TYPE.toLowerCase().contains("debug");
    // An intent extra to indicate the horizontal scroll of the wallpaper.
    public static final String EXTRA_WALLPAPER_OFFSET = "com.android.launcher3.WALLPAPER_OFFSET";
    public static final int COLOR_EXTRACTION_JOB_ID = 1;
    public static final int WALLPAPER_COMPAT_JOB_ID = 2;
    public static final String ALLOW_ROTATION_PREFERENCE_KEY = "pref_allowRotation";
    private static final String TAG = "Launcher.Utilities";
    private static final Pattern sTrimPattern =
            Pattern.compile("^[\\s|\\p{javaSpaceChar}]*(.*)[\\s|\\p{javaSpaceChar}]*$");
    private static final int[] sLoc0 = new int[2];
    private static final int[] sLoc1 = new int[2];
    private static final float[] sPoint = new float[2];
    private static final Matrix sMatrix = new Matrix();
    private static final Matrix sInverseMatrix = new Matrix();
    // These values are same as that in {@link AsyncTask}.
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;
    /**
     * An {@link Executor} to be used with async task with no limit on the queue size.
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    public static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    public static boolean isAllowRotationPrefEnabled(Context context) {
        return getPrefs(context).getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                getAllowRotationDefaultValue(context));
    }

    public static boolean getAllowRotationDefaultValue(Context context) {
        if (ATLEAST_NOUGAT) {
            // If the device was scaled, used the original dimensions to determine if rotation
            // is allowed of not.
            Resources res = context.getResources();
            int originalSmallestWidth = res.getConfiguration().smallestScreenWidthDp
                    * res.getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEVICE_STABLE;
            return originalSmallestWidth >= 600;
        }
        return false;
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant        The descendant to which the passed coordinate is relative.
     * @param ancestor          The root view to make the coordinates relative to.
     * @param coord             The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *                          sometimes this is relevant as in a child's coordinates within the descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     * this scale factor is assumed to be equal in X and Y, and so if at any point this
     * assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(
            View descendant, View ancestor, int[] coord, boolean includeRootScroll) {
        sPoint[0] = coord[0];
        sPoint[1] = coord[1];

        float scale = 1.0f;
        View v = descendant;
        while (v != ancestor && v != null) {
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v != descendant || includeRootScroll) {
                sPoint[0] -= v.getScrollX();
                sPoint[1] -= v.getScrollY();
            }

            v.getMatrix().mapPoints(sPoint);
            sPoint[0] += v.getLeft();
            sPoint[1] += v.getTop();
            scale *= v.getScaleX();

            v = (View) v.getParent();
        }

        coord[0] = Math.round(sPoint[0]);
        coord[1] = Math.round(sPoint[1]);
        return scale;
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToAncestor(View, View, int[], boolean)}.
     */
    public static void mapCoordInSelfToDescendant(View descendant, View root, int[] coord) {
        sMatrix.reset();
        View v = descendant;
        while (v != root) {
            sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
            sMatrix.postConcat(v.getMatrix());
            sMatrix.postTranslate(v.getLeft(), v.getTop());
            v = (View) v.getParent();
        }
        sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
        sMatrix.invert(sInverseMatrix);

        sPoint[0] = coord[0];
        sPoint[1] = coord[1];
        sInverseMatrix.mapPoints(sPoint);
        coord[0] = Math.round(sPoint[0]);
        coord[1] = Math.round(sPoint[1]);
    }

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     */
    public static boolean pointInView(View v, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < (v.getWidth() + slop) &&
                localY < (v.getHeight() + slop);
    }

    public static int[] getCenterDeltaInScreenSpace(View v0, View v1) {
        v0.getLocationInWindow(sLoc0);
        v1.getLocationInWindow(sLoc1);

        sLoc0[0] += (v0.getMeasuredWidth() * v0.getScaleX()) / 2;
        sLoc0[1] += (v0.getMeasuredHeight() * v0.getScaleY()) / 2;
        sLoc1[0] += (v1.getMeasuredWidth() * v1.getScaleX()) / 2;
        sLoc1[1] += (v1.getMeasuredHeight() * v1.getScaleY()) / 2;
        return new int[]{sLoc1[0] - sLoc0[0], sLoc1[1] - sLoc0[1]};
    }

    public static void scaleRectFAboutCenter(RectF r, float scale) {
        if (scale != 1.0f) {
            float cx = r.centerX();
            float cy = r.centerY();
            r.offset(-cx, -cy);
            r.left = r.left * scale;
            r.top = r.top * scale;
            r.right = r.right * scale;
            r.bottom = r.bottom * scale;
            r.offset(cx, cy);
        }
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            int cx = r.centerX();
            int cy = r.centerY();
            r.offset(-cx, -cy);

            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);

            r.offset(cx, cy);
        }
    }

    public static float shrinkRect(Rect r, float scaleX, float scaleY) {
        float scale = Math.min(Math.min(scaleX, scaleY), 1.0f);
        if (scale < 1.0f) {
            int deltaX = (int) (r.width() * (scaleX - scale) * 0.5f);
            r.left += deltaX;
            r.right -= deltaX;

            int deltaY = (int) (r.height() * (scaleY - scale) * 0.5f);
            r.top += deltaY;
            r.bottom -= deltaY;
        }
        return scale;
    }


    public static boolean isSystemApp(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        ComponentName cn = intent.getComponent();
        String packageName = null;
        if (cn == null) {
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if ((info != null) && (info.activityInfo != null)) {
                packageName = info.activityInfo.packageName;
            }
        } else {
            packageName = cn.getPackageName();
        }
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (NameNotFoundException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /*
     * Finds a system apk which had a broadcast receiver listening to a particular action.
     * @param action intent action used to find the apk
     * @return a pair of apk package name and the resources.
     */
    static Pair<String, Resources> findSystemApk(String action, PackageManager pm) {
        final Intent intent = new Intent(action);
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (info.activityInfo != null &&
                    (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                final String packageName = info.activityInfo.packageName;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    return Pair.create(packageName, res);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Failed to find resources for " + packageName);
                }
            }
        }
        return null;
    }

    /**
     * Compresses the bitmap to a byte array for serialization.
     */
    public static byte[] flattenBitmap(Bitmap bitmap) {
        // Try go guesstimate how much space the icon will take when serialized
        // to avoid unnecessary allocations/copies during the write.
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Could not write bitmap");
            return null;
        }
    }

    /**
     * Trims the string, removing all whitespace at the beginning and end of the string.
     * Non-breaking whitespaces are also removed.
     */
    public static String trim(CharSequence s) {
        if (s == null) {
            return null;
        }

        // Just strip any sequence of whitespace or java space characters from the beginning and end
        Matcher m = sTrimPattern.matcher(s);
        return m.replaceAll("$1");
    }

    /**
     * Calculates the height of a given string at a specific text size.
     */
    public static int calculateTextHeight(float textSizePx) {
        Paint p = new Paint();
        p.setTextSize(textSizePx);
        Paint.FontMetrics fm = p.getFontMetrics();
        return (int) Math.ceil(fm.bottom - fm.top);
    }

    public static int calculateTextHeight(float textSizePx, boolean twoLines) {
        Paint p = new Paint();
        p.setTextSize(textSizePx);
        Paint.FontMetrics fm = p.getFontMetrics();
        int result = (int) Math.ceil(fm.bottom - fm.top);
        return twoLines ? result * 2 : result;
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Returns true if the intent is a valid launch intent for a launcher activity of an app.
     * This is used to identify shortcuts which are different from the ones exposed by the
     * applications' manifest file.
     *
     * @param launchIntent The intent that will be launched when the shortcut is clicked.
     */
    public static boolean isLauncherAppTarget(Intent launchIntent) {
        if (launchIntent != null
                && Intent.ACTION_MAIN.equals(launchIntent.getAction())
                && launchIntent.getComponent() != null
                && launchIntent.getCategories() != null
                && launchIntent.getCategories().size() == 1
                && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && TextUtils.isEmpty(launchIntent.getDataString())) {
            // An app target can either have no extra or have ItemInfo.EXTRA_PROFILE.
            Bundle extras = launchIntent.getExtras();
            return extras == null || extras.keySet().isEmpty();
        }
        return false;
    }

    public static float dpiFromPx(int size, DisplayMetrics metrics) {
        float densityRatio = (float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }

    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, metrics));
    }

    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public static String createDbSelectionQuery(String columnName, Iterable<?> values) {
        return String.format(Locale.ENGLISH, "%s IN (%s)", columnName, TextUtils.join(", ", values));
    }

    public static boolean isBootCompleted() {
        return "1".equals(getSystemProperty("sys.boot_completed", "1"));
    }

    public static String getSystemProperty(String property, String defaultValue) {
        try {
            Class clazz = Class.forName("android.os.SystemProperties");
            Method getter = clazz.getDeclaredMethod("get", String.class);
            String value = (String) getter.invoke(null, property);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to read system properties");
        }
        return defaultValue;
    }

    /**
     * Ensures that a value is within given bounds. Specifically:
     * If value is less than lowerBound, return lowerBound; else if value is greater than upperBound,
     * return upperBound; else return value unchanged.
     */
    public static int boundToRange(int value, int lowerBound, int upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static long boundToRange(long value, long lowerBound, long upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }
    /**
     * Wraps a message with a TTS span, so that a different message is spoken than
     * what is getting displayed.
     *
     * @param msg    original message
     * @param ttsMsg message to be spoken
     */
    public static CharSequence wrapForTts(CharSequence msg, String ttsMsg) {
        SpannableString spanned = new SpannableString(msg);
        spanned.setSpan(new TtsSpan.TextBuilder(ttsMsg).build(),
                0, spanned.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return spanned;
    }

    /**
     * Replacement for Long.compare() which was added in API level 19.
     */
    public static int longCompare(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

    public static SharedPreferences getPrefs(Context context) {
        return getZimPrefs(context).getSharedPrefs();
    }

    public static SharedPreferences getDevicePrefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.DEVICE_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getReflectionPrefs(Context context) {
        return context.getSharedPreferences(
                LauncherFiles.REFLECTION_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static boolean isPowerSaverPreventingAnimation(Context context) {
        if (ATLEAST_P) {
            // Battery saver mode no longer prevents animations.
            return false;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager.isPowerSaveMode();
    }

    public static boolean isWallpaperAllowed(Context context) {
        if (ATLEAST_NOUGAT) {
            try {
                WallpaperManager wm = context.getSystemService(WallpaperManager.class);
                return (Boolean) wm.getClass().getDeclaredMethod("isSetWallpaperAllowed")
                        .invoke(wm);
            } catch (Exception e) {
            }
        }
        return true;
    }

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                if (FeatureFlags.IS_DOGFOOD_BUILD) {
                    Log.d(TAG, "Error closing", e);
                }
            }
        }
    }

    /**
     * Returns whether the collection is null or empty.
     */
    public static boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    public static boolean isBinderSizeError(Exception e) {
        return e.getCause() instanceof TransactionTooLargeException
                || e.getCause() instanceof DeadObjectException;
    }

    public static <T> T getOverrideObject(Class<T> clazz, Context context, int resId) {
        String className = context.getString(resId);
        if (!TextUtils.isEmpty(className)) {
            try {
                Class<?> cls = Class.forName(className);
                return (T) cls.getDeclaredConstructor(Context.class).newInstance(context);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | ClassCastException | NoSuchMethodException | InvocationTargetException e) {
                Log.e(TAG, "Bad overriden class", e);
            }
        }

        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a HashSet with a single element. We use this instead of Collections.singleton()
     * because HashSet ensures all operations, such as remove, are supported.
     */
    public static <T> HashSet<T> singletonHashSet(T elem) {
        HashSet<T> hashSet = new HashSet<>(1);
        hashSet.add(elem);
        return hashSet;
    }

    public static ZimPreferences getZimPrefs(Context context) {
        return ZimPreferences.Companion.getInstance(context);
    }

    private static List<Runnable> onStart = new ArrayList<>();

    /**
     * ATTENTION: Only ever call this from within LawnchairLauncher.kt
     */
    public /* private */ static void onLauncherStart() {
        Log.d(TAG, "onLauncherStart: " + onStart.size());
        for (Runnable r : onStart)
            r.run();
        onStart.clear();
    }


    /**
     * Cues a runnable to be executed after binding all launcher elements the next time
     */
    public static void cueAfterNextStart(Runnable runnable) {
        Log.d(TAG, "cueAfterNextStart: " + runnable);
        onStart.add(runnable);
    }

    public static void goToHome(Context context, Runnable onStart) {
        cueAfterNextStart(onStart);
        goToHome(context);
    }

    public static void goToHome(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ComponentName componentName = intent.resolveActivity(pm);
        if (!context.getPackageName().equals(componentName.getPackageName())) {
            intent = pm.getLaunchIntentForPackage(context.getPackageName());
        }
        context.startActivity(intent);
    }

    public static void restartLauncher(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName componentName = intent.resolveActivity(pm);
        if (!context.getPackageName().equals(componentName.getPackageName())) {
            intent = pm.getLaunchIntentForPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        restartLauncher(context, intent);
    }

    public static void restartLauncher(Context context, Intent intent) {
        context.startActivity(intent);

        // Create a pending intent so the application is restarted after System.exit(0) was called.
        // We use an AlarmManager to call this intent in 100ms
        PendingIntent mPendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);

        // Kill the application
        killLauncher();
    }

    public static void killLauncher() {
        System.exit(0);
    }

    public static void checkRestoreSuccess(Context context) {
        ZimPreferences prefs = Utilities.getZimPrefs(context);
        if (prefs.getRestoreSuccess()) {
            prefs.setRestoreSuccess(false);
            context.startActivity(new Intent(context, RestoreBackupActivity.class)
                    .putExtra(RestoreBackupActivity.EXTRA_SUCCESS, true));
        }
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Bitmap drawableToBitmap(Drawable drawable, boolean forceCreate) {
        if (!forceCreate && drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static void setLightUi(Window window) {
        int flags = window.getDecorView().getSystemUiVisibility();
        if (ATLEAST_MARSHMALLOW)
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (ATLEAST_OREO)
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public static boolean hasStoragePermission(Context context) {
        return hasPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestStoragePermission(Activity activity) {
        ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, ZimLauncher.REQUEST_PERMISSION_STORAGE_ACCESS);
    }

    public static void setupPirateLocale(Activity activity) {
        Locale locale = new Locale("pir");
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        Resources baseResources = activity.getBaseContext().getResources();
        baseResources.updateConfiguration(config, baseResources.getDisplayMetrics());
    }

    public static int setFlag(int flags, int flag, boolean value) {
        if (value) {
            return flags | flag;
        } else {
            return flags & ~flag;
        }
    }

    public static String upperCaseFirstLetter(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase(Locale.US) + str.substring(1);
    }

    public static UserHandle myUserHandle() {
        return android.os.Process.myUserHandle();
    }

    /**
     * Utility method to post a runnable on the handler, skipping the synchronization barriers.
     */
    public static void postAsyncCallback(Handler handler, Runnable callback) {
        Message msg = Message.obtain(handler, callback);
        msg.setAsynchronous(true);
        handler.sendMessage(msg);
    }

    public static Boolean isEmui() {
        return !TextUtils.isEmpty(getSystemProperty("ro.build.version.emui", ""));
    }

    public static Boolean isOnePlusStock() {
        return !TextUtils.isEmpty(getSystemProperty("ro.oxygen.version", "")) || !TextUtils
                .isEmpty(getSystemProperty("ro.hydrogen.version", ""));
    }

    /**
     * @param bitmap                the Bitmap to be scaled
     * @param threshold             the maxium dimension (either width or height) of the scaled bitmap
     * @param isNecessaryToKeepOrig is it necessary to keep the original bitmap? If not recycle the original bitmap to prevent memory leak.
     *                              <p>
     *                              Credit: https://gist.github.com/vxhviet/873d142b41217739a1302d337b7285ba
     */
    public static Bitmap getScaledDownBitmap(Bitmap bitmap, int threshold, boolean isNecessaryToKeepOrig) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = width;
        int newHeight = height;

        if (width > height && width > threshold) {
            newWidth = threshold;
            newHeight = (int) (height * (float) newWidth / width);
        }

        if (width > height && width <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if (width < height && height > threshold) {
            newHeight = threshold;
            newWidth = (int) (width * (float) newHeight / height);
        }

        if (width < height && height <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        if (width == height && width > threshold) {
            newWidth = threshold;
            newHeight = newWidth;
        }

        if (width == height && width <= threshold) {
            //the bitmap is already smaller than our required dimension, no need to resize it
            return bitmap;
        }

        return getResizedBitmap(bitmap, newWidth, newHeight, isNecessaryToKeepOrig);
    }

    private static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight, boolean isNecessaryToKeepOrig) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        if (!isNecessaryToKeepOrig) {
            bm.recycle();
        }
        return resizedBitmap;
    }

    public static boolean isRecentsEnabled() {
        LauncherAppState las = LauncherAppState.getInstanceNoCreate();
        if (las != null) {
            Context context = las.getContext();
            return ZimAppKt.getZimApp(context).getRecentsEnabled();
        }
        return false;
    }

    /*public static Drawable getIconForTask(Context context, int userId, String packageName) {
        IconCache ic = LauncherAppState.getInstanceNoCreate().getIconCache();
        LauncherAppsCompat lac = LauncherAppsCompat.getInstance(context);
        UserHandle user = UserHandle.of(userId);
        List<LauncherActivityInfo> al = lac.getActivityList(packageName, user);
        if (!al.isEmpty()) {
            Drawable fullResIcon = ic.getFullResIcon(al.get(0));
            if (user == Process.myUserHandle()) {
                return fullResIcon;
            } else {
                LauncherIcons li = LauncherIcons.obtain(context);
                BitmapInfo bitmapInfo = li.createBadgedIconBitmap(fullResIcon, user, 24);
                li.recycle();

                return new BitmapDrawable(context.getResources(), bitmapInfo.icon);
            }
        } else {
            return null;
        }
    }*/

    public static float getScrimProgress(Launcher launcher, LauncherState toState, float targetProgress) {
        if (Utilities.getZimPrefs(launcher).getDockGradientStyle()) return targetProgress;
        if (toState == LauncherState.OVERVIEW) {
            return OverviewState.getNormalVerticalProgress(launcher);
        }
        return targetProgress;
    }

    public static void openURLinBrowser(Context context, String url) {
        openURLinBrowser(context, url, null, null);
    }

    public static void openURLinBrowser(Context context, String url, Rect sourceBounds, Bundle options) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setSourceBounds(sourceBounds);
            if (options == null) {
                context.startActivity(intent);
            } else {
                context.startActivity(intent, options);
            }
        } catch (ActivityNotFoundException exc) {
            // Believe me, this actually happens.
            Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Maps t from one range to another range.
     *
     * @param t       The value to map.
     * @param fromMin The lower bound of the range that t is being mapped from.
     * @param fromMax The upper bound of the range that t is being mapped from.
     * @param toMin   The lower bound of the range that t is being mapped to.
     * @param toMax   The upper bound of the range that t is being mapped to.
     * @return The mapped value of t.
     */
    public static float mapToRange(float t, float fromMin, float fromMax,
                                   float toMin, float toMax, Interpolator interpolator) {
        if (fromMin == fromMax || toMin == toMax) {
            Log.e(TAG, "mapToRange: range has 0 length");
            return toMin;
        }
        float progress = Math.abs(t - fromMin) / Math.abs(fromMax - fromMin);
        return mapRange(interpolator.getInterpolation(progress), toMin, toMax);
    }

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    public static int parseResourceIdentifier(Resources res, String identifier, String packageName) {
        try {
            return Integer.parseInt(identifier.substring(1));
        } catch (NumberFormatException e) {
            return res.getIdentifier(identifier.substring(1), null, packageName);
        }
    }

    public static int resolveAttributeData(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static boolean hasHeadset(Context context) {
        if (ATLEAST_NOUGAT) {
            AudioManager manager = context.getSystemService(AudioManager.class);
            if (manager == null) {
                return false;
            }

            AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                switch (device.getType()) {
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                        return true;
                }
            }
        }

        return false;
    }

    public static Boolean isMiui() {
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.code", "")) ||
                !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name", ""));
    }

    private static final int SUGGESTIONS_DAY_START = 5;
    private static final int SUGGESTIONS_DAY_END = 21;


    public static boolean isDayTime() {
        Calendar calendar = Calendar.getInstance();
        int hours = calendar.get(Calendar.HOUR_OF_DAY);
        return hours > SUGGESTIONS_DAY_START && hours < SUGGESTIONS_DAY_END;
    }
}
