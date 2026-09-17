package app.mtcapsule.extension.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import androidx.datastore.preferences.PreferencesProto;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Export/import for MT Capsule's own "App Settings" screen (theme, capsule haptics, etc).
 *
 * MT Capsule 15.9 backs that screen with a Jetpack "settings" Preferences DataStore, not
 * SharedPreferences: `Lfu1;` (15.9's obfuscated DataStore holder class) declares four
 * delegated stores - "settings" via `Lrc9;->g("settings", ...)` (a plain key/value
 * Preferences DataStore) plus three *typed proto* DataStores ("notifications.pb",
 * "screen_profiles.pb", "gestures.pb", each with its own custom Serializer class). Only
 * "settings" is handled here: the other three use app-specific protobuf message schemas
 * that only exist as obfuscated smali (no .proto source, no generated getters to read from
 * Java), so reproducing their wire format would mean guessing field numbers from
 * disassembly - exactly the kind of "invented API" this bundle's own conventions warn
 * against. "settings" is the one the strings.xml `app_settings_*` labels (theme, capsule
 * haptics, outline in night mode, ...) actually back, confirmed by walking `Lzz4;`/`Lni1;`,
 * which build the backing file path as
 * `<filesDir>/datastore/` + storeName + `.preferences_pb` - the stock AndroidX
 * `preferencesDataStoreFile(name)` default, not a custom location.
 *
 * Rather than reflectively driving the app's own (obfuscated, suspend-fun) DataStore
 * accessor, this reads/writes `settings.preferences_pb` directly using the real
 * `androidx.datastore.preferences.PreferencesProto` classes (bundled via the
 * datastore-preferences-core dependency declared on this module - see its build.gradle.kts
 * for why that specific artifact/version). Those are the exact generated protobuf-lite
 * classes DataStore's own `PreferencesSerializer` uses to (de)serialize the file, so this
 * produces byte-identical output to what the app would write itself, without needing to
 * fake DataStore's Kotlin suspend `readFrom`/`writeTo`/`updateData` signatures (which
 * compile to a Continuation-based calling convention that is painful to invoke from Java).
 *
 * Entry point: MT Capsule is a Compose app - the App Settings screen has no XML layout and
 * its list rows are anonymous Compose lambdas, so there is no stable, obfuscation-proof
 * bytecode anchor to hang a new list item off without reverse engineering the composition
 * from resource-id integer literals (Compose does not emit CONST_STRING for row labels;
 * they are resolved from the resource table at draw time). `MainAppActivity` itself,
 * however, is named directly in AndroidManifest.xml, so R8 cannot rename it - that makes
 * its `onCreate` a rock solid anchor. Export/Import are exposed there as long-press app
 * shortcuts (`ShortcutManager`), which matches this bundle's documented fallback for cases
 * where injecting a brand new UI list entry would be fragile: a real, user-reachable entry
 * point (long-press the launcher icon) without touching a single Compose bytecode pattern.
 */
@SuppressWarnings("unused")
public class SettingsBackupPatch {

    private static final String DATASTORE_NAME = "settings";
    private static final String DATASTORE_FILE_NAME = DATASTORE_NAME + ".preferences_pb";
    private static final String BACKUP_FILE_NAME = "mtcapsule_settings_backup.json";

    private static final String ACTION_EXPORT = "app.mtcapsule.extension.settings.EXPORT";
    private static final String ACTION_IMPORT = "app.mtcapsule.extension.settings.IMPORT";
    private static final String SHORTCUT_ID_EXPORT = "mtcapsule_export_settings";
    private static final String SHORTCUT_ID_IMPORT = "mtcapsule_import_settings";

    /**
     * Injection point. Called from the very start of `MainAppActivity.onCreate(Bundle)`.
     * Registering shortcuts is cheap and idempotent (setDynamicShortcuts replaces any
     * existing ones with the same IDs), so it is safe to do on every launch rather than
     * gating it behind a first-run check.
     */
    public static void onMainActivityCreate(Activity activity) {
        try {
            registerShortcuts(activity);
            handleShortcutIntent(activity, activity.getIntent());
        } catch (Exception e) {
            // Never let a settings-backup bug take the whole app down on launch.
            Toast.makeText(activity, "MT Capsule settings backup init failed: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private static void registerShortcuts(Activity activity) {
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        if (manager == null) {
            return;
        }

        ShortcutInfo exportShortcut = new ShortcutInfo.Builder(activity, SHORTCUT_ID_EXPORT)
                .setShortLabel("Export Settings")
                .setLongLabel("Export MT Capsule Settings")
                .setIcon(Icon.createWithResource(activity, android.R.drawable.ic_menu_save))
                .setIntent(shortcutIntent(activity, ACTION_EXPORT))
                .build();

        ShortcutInfo importShortcut = new ShortcutInfo.Builder(activity, SHORTCUT_ID_IMPORT)
                .setShortLabel("Import Settings")
                .setLongLabel("Import MT Capsule Settings")
                .setIcon(Icon.createWithResource(activity, android.R.drawable.ic_menu_upload))
                .setIntent(shortcutIntent(activity, ACTION_IMPORT))
                .build();

        List<ShortcutInfo> shortcuts = Arrays.asList(exportShortcut, importShortcut);
        manager.setDynamicShortcuts(shortcuts);
    }

    private static Intent shortcutIntent(Activity activity, String action) {
        Intent intent = new Intent(activity, activity.getClass());
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static void handleShortcutIntent(Activity activity, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ACTION_EXPORT.equals(action)) {
            exportSettings(activity);
        } else if (ACTION_IMPORT.equals(action)) {
            importSettings(activity);
        }
    }

    private static File dataStoreFile(Activity activity) {
        File filesDir = activity.getApplicationContext().getFilesDir();
        return new File(new File(filesDir, "datastore"), DATASTORE_FILE_NAME);
    }

    private static File backupFile(Activity activity) {
        // getExternalFilesDir(null) needs no runtime permission on any supported API level
        // (it is the app's own sandboxed external directory) and survives an uninstall
        // exactly as long as internal storage would, which is good enough for a
        // manual export/import round trip.
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) {
            // External storage can be temporarily unavailable (unmounted, etc).
            dir = activity.getFilesDir();
        }
        return new File(dir, BACKUP_FILE_NAME);
    }

    public static void exportSettings(Activity activity) {
        try {
            File source = dataStoreFile(activity);
            PreferencesProto.PreferenceMap map;
            if (source.exists()) {
                try (FileInputStream in = new FileInputStream(source)) {
                    map = PreferencesProto.PreferenceMap.parseFrom(in);
                }
            } else {
                // Nothing has been saved to the DataStore yet (e.g. a fresh install where
                // the user never left the defaults) - export an empty set instead of
                // treating "file does not exist yet" as an error.
                map = PreferencesProto.PreferenceMap.getDefaultInstance();
            }

            JSONArray entries = new JSONArray();
            for (Map.Entry<String, PreferencesProto.Value> entry : map.getPreferencesMap().entrySet()) {
                JSONObject jsonEntry = new JSONObject();
                jsonEntry.put("key", entry.getKey());
                writeValue(jsonEntry, entry.getValue());
                entries.put(jsonEntry);
            }
            JSONObject root = new JSONObject();
            root.put("dataStore", DATASTORE_NAME);
            root.put("entries", entries);

            File dest = backupFile(activity);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            Toast.makeText(activity, "Settings exported to " + dest.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(activity, "Failed to export settings: " + e, Toast.LENGTH_LONG).show();
        }
    }

    public static void importSettings(Activity activity) {
        try {
            File source = backupFile(activity);
            if (!source.exists()) {
                Toast.makeText(
                        activity,
                        "No settings backup found at " + source.getAbsolutePath(),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            String json = readFile(source);
            JSONObject root = new JSONObject(json);
            JSONArray entries = root.getJSONArray("entries");

            PreferencesProto.PreferenceMap.Builder builder = PreferencesProto.PreferenceMap.newBuilder();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                builder.putPreferences(entry.getString("key"), readValue(entry));
            }

            File dest = dataStoreFile(activity);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            // Overwrite the DataStore's backing file directly. DataStore<Preferences> has
            // no public "replace everything from raw bytes" call; the alternative would be
            // reflectively invoking the app's own obfuscated suspend `updateData`, which
            // means fabricating a Continuation and a Function2 across an app-versioned,
            // R8-renamed interface - far more fragile than writing the exact bytes
            // DataStore's own PreferencesSerializer would produce, which is what
            // PreferenceMap.toByteArray() guarantees here.
            //
            // Caveat: DataStore keeps an in-memory cache of the last-read Preferences per
            // process, so a currently running app process will not observe this change
            // until it is restarted - hence the toast telling the user to do exactly that.
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(builder.build().toByteArray());
            }

            Toast.makeText(activity, "Settings imported. Restart the app to apply.", Toast.LENGTH_LONG).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(activity, "Failed to import settings: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private static String readFile(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeValue(JSONObject json, PreferencesProto.Value value) throws JSONException {
        switch (value.getValueCase()) {
            case BOOLEAN:
                json.put("type", "boolean");
                json.put("value", value.getBoolean());
                break;
            case FLOAT:
                json.put("type", "float");
                json.put("value", value.getFloat());
                break;
            case INTEGER:
                json.put("type", "integer");
                json.put("value", value.getInteger());
                break;
            case LONG:
                json.put("type", "long");
                json.put("value", value.getLong());
                break;
            case STRING:
                json.put("type", "string");
                json.put("value", value.getString());
                break;
            case DOUBLE:
                json.put("type", "double");
                json.put("value", value.getDouble());
                break;
            case STRING_SET: {
                JSONArray set = new JSONArray();
                for (String s : value.getStringSet().getStringsList()) {
                    set.put(s);
                }
                json.put("type", "stringSet");
                json.put("value", set);
                break;
            }
            case VALUE_NOT_SET:
            default:
                json.put("type", "unset");
                break;
        }
    }

    private static PreferencesProto.Value readValue(JSONObject entry) throws JSONException {
        PreferencesProto.Value.Builder value = PreferencesProto.Value.newBuilder();
        String type = entry.optString("type", "unset");
        switch (type) {
            case "boolean":
                value.setBoolean(entry.getBoolean("value"));
                break;
            case "float":
                value.setFloat((float) entry.getDouble("value"));
                break;
            case "integer":
                value.setInteger(entry.getInt("value"));
                break;
            case "long":
                value.setLong(entry.getLong("value"));
                break;
            case "string":
                value.setString(entry.getString("value"));
                break;
            case "double":
                value.setDouble(entry.getDouble("value"));
                break;
            case "stringSet": {
                PreferencesProto.StringSet.Builder set = PreferencesProto.StringSet.newBuilder();
                JSONArray array = entry.getJSONArray("value");
                for (int i = 0; i < array.length(); i++) {
                    set.addStrings(array.getString(i));
                }
                value.setStringSet(set);
                break;
            }
            default:
                // "unset" (or an unrecognized future type) - skip, leaving no oneof set.
                break;
        }
        return value.build();
    }
}
