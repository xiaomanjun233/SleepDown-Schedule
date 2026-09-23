package com.suda.yzune.wakeupschedule;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public final class WakeUpProxyProvider extends ContentProvider {
    private static final String TAG = "WakeUpProxyProvider";
    private static final String SOURCE_PACKAGE = "com.xiaomanjun.sleepdownschedule";
    private static final String SOURCE_AUTHORITY = SOURCE_PACKAGE + ".coloros.course";
    private static final Uri REFRESH_URI = Uri.parse("content://com.suda.yzune.wakeupschedule.provider/refresh");
    private static final String[] COLUMNS = {"code", "data"};
    private static final ExecutorService SOURCE_EXECUTOR = Executors.newFixedThreadPool(2);
    // A blocked OEM provider must not create unbounded workers or queued queries.
    private static final Semaphore SOURCE_SLOTS = new Semaphore(2);
    private WakeUpSnapshotStore snapshots;

    @Override public boolean onCreate() {
        File file = new File(getContext().getNoBackupFilesDir(), "course-snapshots-v1.bin");
        try {
            snapshots = new WakeUpSnapshotStore(file);
        } catch (IOException invalidCache) {
            Log.w(TAG, "Cannot read course snapshots; waiting for source synchronization");
            try { snapshots = new WakeUpSnapshotStore(file, false); }
            catch (IOException impossible) { return false; }
        }
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        List<String> segments = uri.getPathSegments();
        String path = segments.isEmpty() ? "" : segments.get(0);
        if (!isSupported(path)) return null;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = WakeUpSourceResponsePolicy.requestedDate(segments, zone, LocalDate.now(zone));
        String key = isCourse(path) ? "course|" + date : path;
        long now = System.currentTimeMillis();
        WakeUpSnapshotStore.Entry cached = snapshots.get(key, now, zone.getId());
        if (cached != null && now - cached.createdAt <= 500L) return oneRow(0, cached.dataAt(now));

        long generation = snapshots.generation();
        String caller = getCallingPackage();
        Uri.Builder source = uri.buildUpon().authority(SOURCE_AUTHORITY);
        if (caller != null) source.appendQueryParameter("sleepdown_proxy_caller", caller);
        WakeUpSnapshotStore.Entry previous = cached;
        if (SOURCE_SLOTS.tryAcquire()) {
            Future<?> future = SOURCE_EXECUTOR.submit(() -> {
                try (Cursor cursor = getContext().getContentResolver().query(source.build(), null, null, null, null)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        Log.w(TAG, "Source unavailable path=" + path);
                        return;
                    }
                    int code = cursor.getInt(cursor.getColumnIndexOrThrow("code"));
                    String data = cursor.getString(cursor.getColumnIndexOrThrow("data"));
                    if (!WakeUpSourceResponsePolicy.isUsable(code, data)) return;
                    Bundle extras = cursor.getExtras();
                    long createdAt = System.currentTimeMillis();
                    // Metadata must outlive midnight too, or a successful pull today would
                    // invalidate initialization while pushed future course rows remain usable.
                    long validUntil = WakeUpSourceResponsePolicy.snapshotValidUntil(
                            isCourse(path), date, LocalDate.now(zone), zone);
                    WakeUpSnapshotStore.Entry fresh = new WakeUpSnapshotStore.Entry(data,
                            extras.getString("base_data", data), zone.getId(), createdAt, validUntil,
                            extras.getLong("preview_until", 0L));
                    boolean changed = snapshots.put(key, fresh, generation);
                    if (changed && (previous == null || !previous.dataAt(createdAt).equals(fresh.dataAt(createdAt)))) {
                        notifySystem(getContext());
                    }
                } catch (Exception error) {
                    Log.w(TAG, "Source read failed path=" + path + " type=" + error.getClass().getSimpleName());
                } finally {
                    SOURCE_SLOTS.release();
                }
            });
            try { future.get(800L, TimeUnit.MILLISECONDS); }
            catch (TimeoutException timeout) {
                // A late cold-start result still persists and notifies; no polling loop.
                Log.w(TAG, "Source deadline exceeded path=" + path);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (ExecutionException failed) { Log.w(TAG, "Source task failed path=" + path); }
        }
        now = System.currentTimeMillis();
        cached = snapshots.get(key, now, zone.getId());
        if (cached != null) return oneRow(0, cached.dataAt(now));
        Log.w(TAG, "No valid snapshot path=" + path);
        // Unavailable is not a successful empty timetable.
        return oneRow(-1, isCourse(path) || "table_list".equals(path) ? "[]" : "{}");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"refresh".equals(method)) return super.call(method, arg, extras);
        Context context = getContext();
        // Read endpoints remain public for the system; replacing snapshots is a signed write.
        String caller = getCallingPackage();
        if (!SOURCE_PACKAGE.equals(caller) || context.getPackageManager().checkSignatures(
                SOURCE_PACKAGE, context.getPackageName()) != android.content.pm.PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("Only SleepDown may synchronize course snapshots");
        }
        Bundle result = new Bundle();
        try {
            Map<String, WakeUpSnapshotStore.Entry> fresh = new LinkedHashMap<>();
            if (extras != null && extras.getInt("snapshot_version") == 1) {
                String zone = extras.getString("zone");
                long now = System.currentTimeMillis();
                long validUntil = extras.getLong("valid_until");
                Bundle rows = extras.getBundle("rows");
                Bundle bases = extras.getBundle("base_rows");
                if (zone == null || rows == null || validUntil <= now
                        || validUntil > now + TimeUnit.DAYS.toMillis(9)) throw new IllegalArgumentException();
                for (String key : rows.keySet()) {
                    String data = rows.getString(key);
                    if (!WakeUpSourceResponsePolicy.isUsable(0, data)) throw new IllegalArgumentException();
                    String base = bases == null ? data : bases.getString(key, data);
                    fresh.put(key, new WakeUpSnapshotStore.Entry(data, base, zone, now, validUntil,
                            extras.getLong("preview_until")));
                }
            }
            // Atomically replaces the entire export, including valid [] after deletions.
            snapshots.replace(fresh);
            notifySystem(context);
            result.putBoolean("refresh_accepted", true);
        } catch (IOException | IllegalArgumentException error) {
            Log.w(TAG, "Snapshot synchronization failed type=" + error.getClass().getSimpleName());
            result.putBoolean("refresh_accepted", false);
        }
        return result;
    }

    public static void notifySystem(Context context) {
        // Launching the component is not a data invalidation.
        context.getContentResolver().notifyChange(REFRESH_URI, null);
    }

    private static boolean isCourse(String path) {
        return "course_list".equals(path) || "next_course_list".equals(path);
    }
    private static boolean isSupported(String path) {
        return isCourse(path) || "has_init".equals(path) || "show_table_id".equals(path) || "table_list".equals(path);
    }
    private static Cursor oneRow(int code, String data) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{code, data});
        return cursor;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read-only provider"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only provider"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only provider"); }
}
