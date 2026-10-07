package app.template.extension.stayfree;

import android.content.Context;
import android.util.Log;

/**
 * Entry points for the StayFree "Local device sync" patch.
 * <p>
 * StayFree's cross-device sync normally goes through {@code api.stayfreeapps.com}, which only
 * knows a device's usage because the SensorTower usage SDK uploaded it. This replaces that server
 * with {@link LocalSyncServer}, running inside the patched app on the phone:
 * <ul>
 *   <li>every Retrofit service whose base URL is {@code https://api.stayfreeapps.com/} is pointed
 *   at {@code http://127.0.0.1:8787/}, where the server answers the sync API itself, proxies a
 *   short allowlist of read-only lookups (app categories/icons, averages) and drops everything
 *   else (all data-collection uploads);</li>
 *   <li>the StayFree browser extension, patched to talk to the phone's LAN address, pairs and
 *   uploads its website sessions to the same server;</li>
 *   <li>the phone's own app sessions are never uploaded anywhere: they are read on demand from
 *   {@code UsageStatsManager} when a paired browser asks for them.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class LocalSync {
    static final String TAG = "StayFreeLocalSync";

    public static final int PORT = 8787;

    static final String UPSTREAM_ORIGIN = "https://api.stayfreeapps.com";
    private static final String UPSTREAM_PREFIX = UPSTREAM_ORIGIN + "/";
    private static final String LOCAL_PREFIX = "http://127.0.0.1:" + PORT + "/";

    private static LocalSyncServer server;

    private LocalSync() {
    }

    /**
     * Called at the start of {@code Application.onCreate()}.
     */
    public static synchronized void start(Context context) {
        if (server != null) return;
        try {
            LocalSyncServer created = new LocalSyncServer(context.getApplicationContext(), PORT);
            created.start();
            server = created;
            Log.i(TAG, "Local sync server listening on port " + PORT);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start local sync server", t);
        }
    }

    static synchronized boolean isRunning() {
        return server != null;
    }

    /**
     * Called at the start of {@code Retrofit.Builder.baseUrl(String)}. Rewrites unconditionally,
     * even if the server failed to start: a failed lookup is preferable to sending pairing keys
     * and usage to the vendor server behind the user's back.
     */
    public static String rewriteBaseUrl(String url) {
        if (url != null && url.startsWith(UPSTREAM_PREFIX)) {
            return LOCAL_PREFIX + url.substring(UPSTREAM_PREFIX.length());
        }
        return url;
    }
}
