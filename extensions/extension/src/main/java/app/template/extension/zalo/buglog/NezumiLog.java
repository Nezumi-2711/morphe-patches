package app.template.extension.zalo.buglog;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import app.template.extension.zalo.settings.NezumiPrefs;

/**
 * The one logger every Nezumi extension uses. Every call goes to logcat as before; when the
 * "Bug log" patch is applied and the user turned bug logging on in Nezumi's Settings, I/W/E lines
 * are also redacted and appended to a rotating file under {@code files/nezumi_logs/} that the user
 * can export and send to the developer. Without the patch, or with the toggle off, the file side
 * is a no-op.
 */
public final class NezumiLog {
    private NezumiLog() { }

    private static volatile Context appContext;
    private static volatile LogStore store;
    private static volatile boolean enabled;
    private static volatile boolean mainProcess;
    private static volatile String processName;

    /** Replaced by the Bug log patch with the patch-bundle version the APK was built from. */
    static String bundleVersion() {
        return "unknown";
    }

    /**
     * Replaced by the Bug log patch with the patches applied to this APK, one per line:
     * {@code <patch-id> [hook|no-hook]} followed by the build-time option values, if any.
     */
    static String appliedPatches() {
        return "";
    }

    /**
     * Replaced by the Bug log patch with every Zalo method a patch modified, one
     * {@code Lclass;->method(args)Return=<patch-id>} per line, for crash attribution.
     */
    static String touchedMethods() {
        return "";
    }

    private static volatile Attribution attribution;

    static Attribution attribution() {
        Attribution current = attribution;
        if (current == null) attribution = current = Attribution.parse(touchedMethods());
        return current;
    }

    private static final boolean[] FIRED = new boolean[Patch.values().length];

    /**
     * Hook breadcrumb, called through {@link Hits} from the start of every hooked Zalo method:
     * logs {@code I/<id>/Hook hook fired} the first time per process, then costs one array check.
     */
    static void hit(Patch patch) {
        int index = patch.ordinal();
        if (FIRED[index]) return;
        FIRED[index] = true;
        i(patch, "Hook", "hook fired");
    }

    /** Called by the Bug log patch from {@code MainApplication.attachBaseContext}, in every process. */
    public static void install(Context context) {
        try {
            if (context == null || appContext != null) return;
            appContext = context;
            processName = currentProcessName(context);
            mainProcess = processName == null || processName.equals(context.getPackageName());
            String suffix = mainProcess || processName == null || !processName.contains(":")
                ? "" : "-" + processName.substring(processName.indexOf(':') + 1).replaceAll("[^A-Za-z0-9_]", "_");
            store = new LogStore(new File(context.getFilesDir(), LogStore.DIR), "nezumi" + suffix);
            enabled = NezumiPrefs.bool(context, NezumiPrefs.BUG_LOG, false);
            CrashCapture.ensureInstalled();
            if (context instanceof Application) {
                ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) { CrashCapture.ensureInstalled(); }
                    @Override public void onActivityStarted(Activity activity) { }
                    @Override public void onActivityResumed(Activity activity) { CrashCapture.ensureInstalled(); }
                    @Override public void onActivityPaused(Activity activity) { }
                    @Override public void onActivityStopped(Activity activity) { }
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                    @Override public void onActivityDestroyed(Activity activity) { }
                });
            }
            if (enabled) {
                store.startSession(NezumiLog::sessionHeader);
                if (mainProcess) ExitReasons.recordAsync(context);
            }
            hit(Patch.BUG_LOG);
        } catch (Throwable failure) {
            // Never let logging setup break app startup.
            Log.e("BugLog", "install failed", failure);
        }
    }

    /** True when the Bug log patch is applied, i.e. the toggle in Nezumi's Settings does something. */
    public static boolean isAvailable() {
        return store != null;
    }

    public static boolean isEnabled() {
        return enabled && store != null;
    }

    /** Turns file logging on or off for this process and persists the choice. */
    public static void setEnabled(Context context, boolean value) {
        NezumiPrefs.of(context).edit().putBoolean(NezumiPrefs.BUG_LOG, value).apply();
        if (value) {
            enabled = true;
            LogStore current = store;
            if (current != null) current.startSession(NezumiLog::sessionHeader);
            i(Patch.CORE, "NezumiLog", "bug log enabled");
            if (mainProcess) ExitReasons.recordAsync(context);
        } else {
            i(Patch.CORE, "NezumiLog", "bug log disabled");
            enabled = false;
            LogStore current = store;
            if (current != null) current.flush();
        }
    }

    static Context context() {
        return appContext;
    }

    static LogStore store() {
        return store;
    }

    // ---- logging API -------------------------------------------------------------------------
    // Every call names the patch it runs for, so a shared bug log can be split by patch, and a
    // tag that is the calling class's simple name.

    /** Debug: logcat only, never written to the bug-log file. */
    public static void d(Patch patch, String tag, String message) {
        Log.d(tag, prefixed(patch, message));
    }

    public static void i(Patch patch, String tag, String message) {
        Log.i(tag, prefixed(patch, message));
        file('I', patch, tag, message, null);
    }

    public static void w(Patch patch, String tag, String message) {
        Log.w(tag, prefixed(patch, message));
        file('W', patch, tag, message, null);
    }

    public static void w(Patch patch, String tag, String message, Throwable failure) {
        Log.w(tag, prefixed(patch, message), failure);
        file('W', patch, tag, message, failure);
    }

    public static void e(Patch patch, String tag, String message) {
        Log.e(tag, prefixed(patch, message));
        file('E', patch, tag, message, null);
    }

    public static void e(Patch patch, String tag, String message, Throwable failure) {
        Log.e(tag, prefixed(patch, message), failure);
        file('E', patch, tag, message, failure);
    }

    /** Records a swallowed failure: file only (with stack trace), keeps logcat as quiet as before. */
    public static void swallowed(Patch patch, String tag, String message, Throwable failure) {
        file('W', patch, tag, message, failure);
    }

    /**
     * Like {@link #swallowed} but writes each patch+tag+message at most once per process. For
     * fallbacks on hot paths (per row, per conversation) where a broken reflection target would
     * otherwise repeat the same stack trace thousands of times and rotate the useful lines away.
     */
    public static void swallowedOnce(Patch patch, String tag, String message, Throwable failure) {
        if (!enabled || store == null) return;
        if (SEEN.size() < 512 && SEEN.add(patch.id + '\u0000' + tag + '\u0000' + message)) {
            file('W', patch, tag, message + " (further occurrences suppressed)", failure);
        }
    }

    private static final java.util.Set<String> SEEN =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static String prefixed(Patch patch, String message) {
        return "[" + patch.id + "] " + message;
    }

    private static void file(char level, Patch patch, String tag, String message, Throwable failure) {
        LogStore current = store;
        if (!enabled || current == null) return;
        try {
            current.append(System.currentTimeMillis(), line(level, patch, tag, message, failure));
        } catch (Throwable ignored) {
            // Logging must never throw into the hooked code.
        }
    }

    /** Synchronous write for the crash handler: the process may be gone right after this returns. */
    static void fileNow(char level, Patch patch, String tag, String message, Throwable failure) {
        LogStore current = store;
        if (!enabled || current == null) return;
        current.appendNow(System.currentTimeMillis(), line(level, patch, tag, message, failure));
    }

    static void fileRaw(String text) {
        LogStore current = store;
        if (!enabled || current == null) return;
        current.append(System.currentTimeMillis(), LogRedactor.redact(text));
    }

    private static String line(char level, Patch patch, String tag, String message, Throwable failure) {
        return LogLine.body(level, patch.id, tag, Thread.currentThread().getName(),
            LogRedactor.redact(String.valueOf(message)),
            failure == null ? null : LogRedactor.redact(stackTrace(failure)));
    }

    static String stackTrace(Throwable failure) {
        StringWriter text = new StringWriter();
        try (PrintWriter writer = new PrintWriter(text)) {
            failure.printStackTrace(writer);
        }
        String result = text.toString();
        return result.endsWith("\n") ? result.substring(0, result.length() - 1) : result;
    }

    // ---- session header ----------------------------------------------------------------------

    /** Written at the start of every process (and again after a rotation), so each run is self-describing. */
    private static String sessionHeader() {
        Context context = appContext;
        int pid = android.os.Process.myPid();
        StringBuilder out = new StringBuilder();
        out.append("===== session ").append(pid).append(" start =====\n");
        out.append("time: ").append(LogLine.timestamp(System.currentTimeMillis())).append('\n');
        out.append("process: ").append(processName).append('\n');
        out.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" (").append(Build.DEVICE).append(")\n");
        out.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("abi: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        if (context != null) {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                out.append("app: ").append(context.getPackageName()).append(' ').append(info.versionName)
                    .append(" (").append(code).append(")\n");
            } catch (Exception failure) {
                out.append("app: ").append(context.getPackageName()).append(" (version unavailable: ")
                    .append(failure.getClass().getSimpleName()).append(")\n");
            }
        }
        out.append("bundle: Nezumi Patches ").append(bundleVersion()).append('\n');
        out.append("exit-info: ").append(Build.VERSION.SDK_INT >= 30 ? "supported" : "unsupported").append('\n');
        out.append("options:");
        if (context != null) {
            SharedPreferences prefs = NezumiPrefs.of(context);
            Map<String, ?> all = new TreeMap<>(prefs.getAll());
            int shownCount = 0;
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                if (key.contains(":") || key.startsWith("bug_log")) continue;
                Object value = entry.getValue();
                // Only flags and numbers; strings may hold file names or uris picked by the user.
                String shown = value instanceof Boolean || value instanceof Number ? String.valueOf(value) : "<set>";
                out.append("\n  ").append(key).append(" = ").append(shown);
                shownCount++;
            }
            if (shownCount == 0) out.append(" (all defaults)");
        }
        out.append("\napplied patches:");
        String applied = appliedPatches();
        if (applied == null || applied.isEmpty()) {
            out.append(" (unknown: built without the patch registry)");
        } else {
            for (String patch : applied.split("\n")) out.append("\n  ").append(patch);
        }
        out.append("\n=====");
        return out.toString();
    }

    private static String currentProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int length = in.read(buffer);
            int end = 0;
            while (end < length && buffer[end] != 0) end++;
            return end > 0 ? new String(buffer, 0, end, StandardCharsets.UTF_8) : context.getPackageName();
        } catch (Exception failure) {
            return context.getPackageName();
        }
    }
}
