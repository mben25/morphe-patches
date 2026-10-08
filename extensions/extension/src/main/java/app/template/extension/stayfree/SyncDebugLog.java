package app.template.extension.stayfree;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Appends every local-sync HTTP request to a plain-text file so a failing device pairing can be
 * diagnosed on-device without adb/logcat. Each line records the method, target (path + query, so
 * {@code install_id} and {@code code} are visible), whether it came over loopback (the app) or the
 * LAN (the browser extension), the response status, and the full stack trace of any failure.
 * <p>
 * The file lives in the app's <em>external</em> files dir so it can be pulled with
 * {@code adb pull} (no root) or read with a file manager:
 * <pre>
 * /sdcard/Android/data/com.burockgames.timeclocker/files/stayfree-sync.log
 * </pre>
 * It is best-effort: any I/O problem is swallowed so logging never breaks the server. The file is
 * capped at {@link #MAX_BYTES} and rotated once to keep it pullable.
 */
final class SyncDebugLog {
    private static final long MAX_BYTES = 512 * 1024;
    private static final int MAX_BODY_CHARS = 2048;

    private final File file;
    private final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    SyncDebugLog(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        this.file = new File(dir, "stayfree-sync.log");
    }

    synchronized void request(String method, String target, boolean loopback, String remote,
                              byte[] body, int status, Throwable failure) {
        try {
            rotateIfNeeded();
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.print(timestamp.format(new Date()));
                out.print("  ");
                out.print(loopback ? "APP " : "EXT ");
                out.print('[');
                out.print(remote == null ? "?" : remote);
                out.print("]  ");
                out.print(method);
                out.print(' ');
                out.print(target);
                out.print("  -> ");
                out.print(status);
                if (body != null && body.length > 0 && isPairingOrConfig(target)) {
                    String text = new String(body, StandardCharsets.UTF_8);
                    if (text.length() > MAX_BODY_CHARS) text = text.substring(0, MAX_BODY_CHARS) + "…";
                    out.print("  body=");
                    out.print(text.replace('\n', ' ').replace('\r', ' '));
                }
                out.println();
                if (failure != null) failure.printStackTrace(out);
            }
        } catch (Throwable t) {
            Log.w(LocalSync.TAG, "Could not write sync debug log", t);
        }
    }

    /** Every Retrofit base URL StayFree builds, and what it was rewritten to. */
    synchronized void baseUrl(String original, String rewritten) {
        try {
            rotateIfNeeded();
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.print(timestamp.format(new Date()));
                out.print("  BASEURL  ");
                out.print(original);
                if (rewritten != null && !rewritten.equals(original)) {
                    out.print("  -> ");
                    out.print(rewritten);
                }
                out.println();
            }
        } catch (Throwable t) {
            Log.w(LocalSync.TAG, "Could not write sync debug log", t);
        }
    }

    private static boolean isPairingOrConfig(String target) {
        return target != null && (target.contains("pairing") || target.contains("sync/config")
                || target.contains("web/upload") || target.contains("devices"));
    }

    private void rotateIfNeeded() {
        if (file.length() < MAX_BYTES) return;
        File old = new File(file.getPath() + ".1");
        //noinspection ResultOfMethodCallIgnored
        old.delete();
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(old);
    }
}
