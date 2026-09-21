package com.suda.yzune.wakeupschedule;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WakeUpProxyProvider extends ContentProvider {
    private static final String TAG = "WakeUpProxyProvider";
    private static final String SOURCE_AUTHORITY =
            "com.xiaomanjun.sleepdownschedule.coloros.course";
    private static final String[] COLUMNS = {"code", "data"};
    private static final long CACHE_TTL_MS = 500L;
    private static final long SOURCE_TIMEOUT_MS = 400L;
    private static final String CALLER_PARAMETER = "sleepdown_proxy_caller";
    private static final String REFRESH_METHOD = "refresh";
    private static final String REFRESH_ACCEPTED = "refresh_accepted";
    private static final Uri REFRESH_URI =
            Uri.parse("content://com.suda.yzune.wakeupschedule.provider/refresh");

    private static final ConcurrentHashMap<String, Snapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();
    private static final ExecutorService SOURCE_EXECUTOR = Executors.newCachedThreadPool(
            new ThreadFactory() {
                private int nextId;

                @Override
                public synchronized Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "wakeup-source-" + ++nextId);
                    thread.setDaemon(true);
                    return thread;
                }
            });

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        List<String> segments = uri.getPathSegments();
        String path = segments.isEmpty() ? "" : segments.get(0);
        if ("refresh".equals(path)) {
            return null;
        }
        if (!isSupported(path)) {
            return null;
        }

        long startedAt = SystemClock.elapsedRealtime();
        String key = cacheKey(uri, path);
        Snapshot cached = SNAPSHOTS.get(key);
        long now = SystemClock.elapsedRealtime();
        if (cached != null && now - cached.createdAtMs <= CACHE_TTL_MS) {
            Log.d(TAG, "缓存命中 path=" + uri.getPath() + " jsonLength="
                    + cached.data.length() + " costMs=" + (now - startedAt));
            return oneRow(cached.code, cached.data);
        }

        SourceResponse response = readSourceWithRetry(uri, path, getCallingPackage());
        if (response.isUsable(path)) {
            Snapshot fresh = new Snapshot(response.code, response.data,
                    SystemClock.elapsedRealtime());
            SNAPSHOTS.put(key, fresh);
            Log.d(TAG, "实时取源成功 path=" + uri.getPath()
                    + " caller=" + getCallingPackage()
                    + " jsonLength=" + response.data.length()
                    + " costMs=" + (SystemClock.elapsedRealtime() - startedAt));
            return oneRow(fresh.code, fresh.data);
        }

        if (cached != null) {
            Log.w(TAG, "实时取源失败，回退快照 path=" + uri.getPath() + " reason="
                    + response.reason + " jsonLength=" + cached.data.length()
                    + " costMs=" + (SystemClock.elapsedRealtime() - startedAt));
            return oneRow(cached.code, cached.data);
        }

        Log.w(TAG, "实时取源失败，无可用快照 path=" + uri.getPath() + " reason="
                + response.reason + " costMs=" + (SystemClock.elapsedRealtime() - startedAt));
        return fallback(path);
    }

    private SourceResponse readSourceWithRetry(Uri uri, String path, String callerPackage) {
        Uri.Builder sourceBuilder = uri.buildUpon().authority(SOURCE_AUTHORITY);
        if (callerPackage != null && !callerPackage.trim().isEmpty()) {
            sourceBuilder.appendQueryParameter(CALLER_PARAMETER, callerPackage);
        }
        Uri sourceUri = sourceBuilder.build();
        SourceResponse last = SourceResponse.failure("未执行");
        for (int attempt = 1; attempt <= 2; attempt++) {
            CancellationSignal cancellation = new CancellationSignal();
            Future<SourceResponse> future = SOURCE_EXECUTOR.submit(
                    () -> readSourceOnce(sourceUri, cancellation));
            try {
                SourceResponse response = future.get(SOURCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (response.isUsable(path)) {
                    return response;
                }
                last = response;
                Log.w(TAG, "源返回不可用 path=" + uri.getPath() + " attempt=" + attempt
                        + " reason=" + response.reason + " jsonLength="
                        + (response.data == null ? 0 : response.data.length()));
            } catch (TimeoutException error) {
                cancellation.cancel();
                future.cancel(true);
                last = SourceResponse.failure("超时");
                Log.w(TAG, "源查询超时 path=" + uri.getPath() + " attempt=" + attempt);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                cancellation.cancel();
                future.cancel(true);
                return SourceResponse.failure("线程中断");
            } catch (CancellationException error) {
                last = SourceResponse.failure("已取消");
            } catch (ExecutionException error) {
                last = SourceResponse.failure(error.getCause() == null
                        ? "执行失败" : String.valueOf(error.getCause().getMessage()));
                Log.w(TAG, "源查询异常 path=" + uri.getPath() + " attempt=" + attempt,
                        error.getCause());
            }
        }
        return last;
    }

    private SourceResponse readSourceOnce(Uri sourceUri, CancellationSignal cancellation)
            throws Exception {
        Context context = getContext();
        if (context == null) {
            return SourceResponse.failure("Provider 上下文为空");
        }
        ContentResolver resolver = context.getContentResolver();
        try (Cursor source = resolver.query(sourceUri, null, null, null, null, cancellation)) {
            if (source == null || !source.moveToFirst()) {
                return SourceResponse.failure("源 Cursor 为空");
            }
            int codeColumn = source.getColumnIndex("code");
            int dataColumn = source.getColumnIndex("data");
            if (codeColumn < 0 || dataColumn < 0) {
                return SourceResponse.failure("源 Cursor 缺少字段");
            }
            String data = source.getString(dataColumn);
            if (data == null || data.trim().isEmpty()) {
                return SourceResponse.failure("源 data 为空");
            }
            return new SourceResponse(source.getInt(codeColumn), data, "");
        }
    }

    private static String cacheKey(Uri uri, String path) {
        if (!("course_list".equals(path)
                || "next_course_list".equals(path))) return path;
        List<String> segments = uri.getPathSegments();
        if (segments.size() > 1) {
            return path + "|" + String.join("/", segments.subList(1, segments.size()));
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return path + "|" + ("next_course_list".equals(path) ? today.plusDays(1) : today);
    }

    private static boolean isSupported(String path) {
        return "has_init".equals(path)
                || "show_table_id".equals(path)
                || "table_list".equals(path)
                || "course_list".equals(path)
                || "next_course_list".equals(path);
    }

    private static Cursor fallback(String path) {
        switch (path) {
            case "has_init":
                return oneRow(0, "{\"has_init\":true}");
            case "show_table_id":
                return oneRow(0, "{\"table_id\":1}");
            case "table_list":
                return oneRow(0, "[{\"id\":1,\"tableName\":\"SleepDown课程表\"}]");
            case "course_list":
            case "next_course_list":
                return oneRow(0, "[]");
            default:
                return null;
        }
    }

    private static Cursor oneRow(int code, String data) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{code, data});
        return cursor;
    }

    public static void notifySystem(Context context) {
        clearSnapshotsAndNotify(context);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> clearSnapshotsAndNotify(context), 1_000L);
    }

    private static void clearSnapshotsAndNotify(Context context) {
        SNAPSHOTS.clear();
        context.getContentResolver().notifyChange(REFRESH_URI, null);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!REFRESH_METHOD.equals(method)) {
            return super.call(method, arg, extras);
        }
        Bundle result = new Bundle();
        Context context = getContext();
        if (context == null) {
            result.putBoolean(REFRESH_ACCEPTED, false);
            return result;
        }
        clearSnapshotsAndNotify(context);
        result.putBoolean(REFRESH_ACCEPTED, true);
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    private static final class Snapshot {
        final int code;
        final String data;
        final long createdAtMs;

        Snapshot(int code, String data, long createdAtMs) {
            this.code = code;
            this.data = data;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class SourceResponse {
        final int code;
        final String data;
        final String reason;

        SourceResponse(int code, String data, String reason) {
            this.code = code;
            this.data = data;
            this.reason = reason;
        }

        static SourceResponse failure(String reason) {
            return new SourceResponse(-1, null, reason);
        }

        boolean isUsable(String path) {
            return WakeUpSourceResponsePolicy.isUsable(code, data);
        }
    }
}
