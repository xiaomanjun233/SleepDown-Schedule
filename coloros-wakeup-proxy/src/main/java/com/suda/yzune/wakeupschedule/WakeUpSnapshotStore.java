package com.suda.yzune.wakeupschedule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, derived export data only. The application remains the source of truth. */
final class WakeUpSnapshotStore {
    private static final int FORMAT = 1;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_BYTES = 512 * 1024;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long generation;

    WakeUpSnapshotStore(File file) throws IOException {
        this(file, true);
    }

    WakeUpSnapshotStore(File file, boolean load) throws IOException {
        this.file = file;
        if (!load || !file.exists()) return;
        if (file.length() > MAX_BYTES) throw new IOException("Snapshot exceeds limit");
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != FORMAT) throw new IOException("Unknown snapshot format");
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid snapshot count");
            for (int i = 0; i < count; i++) {
                String key = in.readUTF();
                entries.put(key, new Entry(readText(in), readText(in), in.readUTF(),
                        in.readLong(), in.readLong(), in.readLong()));
            }
        }
    }

    synchronized long generation() { return generation; }

    synchronized Entry get(String key, long now, String zone) {
        Entry entry = entries.get(key);
        return entry != null && entry.zone.equals(zone) && now >= entry.createdAt
                && now < entry.validUntil ? entry : null;
    }

    synchronized void replace(Map<String, Entry> fresh) throws IOException {
        persist(fresh);
        entries.clear();
        entries.putAll(fresh);
        generation++;
    }

    synchronized boolean put(String key, Entry entry, long expectedGeneration) throws IOException {
        if (generation != expectedGeneration) return false;
        Map<String, Entry> fresh = new LinkedHashMap<>(entries);
        fresh.entrySet().removeIf(item -> item.getValue().validUntil <= entry.createdAt
                || !item.getValue().zone.equals(entry.zone));
        fresh.put(key, entry);
        while (fresh.size() > MAX_ENTRIES) fresh.remove(fresh.keySet().iterator().next());
        persist(fresh);
        entries.clear();
        entries.putAll(fresh);
        return true;
    }

    private void persist(Map<String, Entry> fresh) throws IOException {
        if (fresh.size() > MAX_ENTRIES) throw new IOException("Too many snapshots");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT);
            out.writeInt(fresh.size());
            for (Map.Entry<String, Entry> item : fresh.entrySet()) {
                Entry entry = item.getValue();
                out.writeUTF(item.getKey());
                writeText(out, entry.data);
                writeText(out, entry.baseData);
                out.writeUTF(entry.zone);
                out.writeLong(entry.createdAt);
                out.writeLong(entry.validUntil);
                out.writeLong(entry.previewUntil);
            }
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Snapshot exceeds limit");
        File pending = new File(file.getPath() + ".new");
        try (FileOutputStream out = new FileOutputStream(pending)) {
            bytes.writeTo(out);
            out.getFD().sync();
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_BYTES) throw new IOException("Invalid snapshot length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    static final class Entry {
        final String data, baseData, zone;
        final long createdAt, validUntil, previewUntil;

        Entry(String data, String baseData, String zone, long createdAt, long validUntil, long previewUntil) {
            this.data = data;
            this.baseData = baseData;
            this.zone = zone;
            this.createdAt = createdAt;
            this.validUntil = validUntil;
            this.previewUntil = previewUntil;
        }

        String dataAt(long now) {
            return previewUntil > 0 && now >= previewUntil ? baseData : data;
        }
    }
}
