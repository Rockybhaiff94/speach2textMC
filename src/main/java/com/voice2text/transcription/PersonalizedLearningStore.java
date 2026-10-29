package com.voice2text.transcription;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe persistent learning store for user-confirmed speech recognition corrections.
 *
 * Requirements:
 * - Stores mappings from recognized mistakes to confirmed intended words/phrases.
 * - Supports optional context (e.g., predecessor or successor words).
 * - Bounded storage (default 1,000 entries) with LRU/LFU eviction.
 * - Resilient to file corruption: backs up corrupted JSON to .bak and recovers safely.
 * - Supports resetting learned corrections independently of main mod settings.
 * - Never learns unconfirmed ASR output.
 */
public class PersonalizedLearningStore {

    public static final int CURRENT_VERSION = 1;
    public static final int DEFAULT_MAX_ENTRIES = 1000;

    public static class LearnedEntry {
        private final String mistake;
        private final String correction;
        private final String context;
        private int usageCount;
        private long lastUpdated;

        public LearnedEntry(String mistake, String correction, String context, int usageCount, long lastUpdated) {
            this.mistake = mistake;
            this.correction = correction;
            this.context = context != null ? context : "";
            this.usageCount = usageCount;
            this.lastUpdated = lastUpdated;
        }

        public String getMistake() {
            return mistake;
        }

        public String getCorrection() {
            return correction;
        }

        public String getContext() {
            return context;
        }

        public int getUsageCount() {
            return usageCount;
        }

        public long getLastUpdated() {
            return lastUpdated;
        }

        public void incrementUsage() {
            this.usageCount++;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    private final File storageFile;
    private final int maxEntries;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Composite key: "normalizedMistake|normalizedContext"
    private final Map<String, LearnedEntry> entries = new ConcurrentHashMap<>();

    public PersonalizedLearningStore(File storageFile) {
        this(storageFile, DEFAULT_MAX_ENTRIES);
    }

    public PersonalizedLearningStore(File storageFile, int maxEntries) {
        this.storageFile = storageFile;
        this.maxEntries = maxEntries;
        load();
    }

    /**
     * Normalizes a token key for lookup (trims, NFC Unicode normalization, lowercases Latin characters).
     */
    public static String normalizeKey(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC);
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    public static String makeKey(String mistake, String context) {
        return normalizeKey(mistake) + "|" + normalizeKey(context);
    }

    /**
     * Finds a learned correction for a mistake token given surrounding context tokens.
     * Evaluates context-specific mappings first, then falls back to non-contextual mappings.
     *
     * @param mistake the recognized mistake token
     * @param prevToken the preceding token (or null/empty)
     * @param nextToken the following token (or null/empty)
     * @return the corrected text, or null if no learned correction matches
     */
    public String lookupCorrection(String mistake, String prevToken, String nextToken) {
        if (mistake == null || mistake.trim().isEmpty()) {
            return null;
        }

        String normMistake = normalizeKey(mistake);

        // 1. Try with previous token context
        if (prevToken != null && !prevToken.trim().isEmpty()) {
            String key = makeKey(normMistake, prevToken);
            LearnedEntry entry = entries.get(key);
            if (entry != null) {
                entry.incrementUsage();
                return entry.getCorrection();
            }
        }

        // 2. Try with next token context
        if (nextToken != null && !nextToken.trim().isEmpty()) {
            String key = makeKey(normMistake, nextToken);
            LearnedEntry entry = entries.get(key);
            if (entry != null) {
                entry.incrementUsage();
                return entry.getCorrection();
            }
        }

        // 3. Try non-contextual mapping (context = "")
        String generalKey = makeKey(normMistake, "");
        LearnedEntry entry = entries.get(generalKey);
        if (entry != null) {
            entry.incrementUsage();
            return entry.getCorrection();
        }

        return null;
    }

    /**
     * Records a user-confirmed correction.
     *
     * @param mistake the recognized word that was incorrect
     * @param correction the intended replacement word or phrase
     * @param context optional surrounding context token (or null/empty for general)
     */
    public synchronized void recordCorrection(String mistake, String correction, String context) {
        if (mistake == null || mistake.trim().isEmpty() || correction == null || correction.trim().isEmpty()) {
            return;
        }

        String normMistake = normalizeKey(mistake);
        String normContext = normalizeKey(context);
        String key = makeKey(normMistake, normContext);

        LearnedEntry existing = entries.get(key);
        if (existing != null) {
            // Update existing entry
            entries.put(key, new LearnedEntry(
                    normMistake,
                    correction.trim(),
                    normContext,
                    existing.getUsageCount() + 1,
                    System.currentTimeMillis()
            ));
        } else {
            // Evict if at capacity
            if (entries.size() >= maxEntries) {
                evictOldestOrLeastUsed();
            }
            entries.put(key, new LearnedEntry(
                    normMistake,
                    correction.trim(),
                    normContext,
                    1,
                    System.currentTimeMillis()
            ));
        }

        save();
    }

    private void evictOldestOrLeastUsed() {
        if (entries.isEmpty()) return;
        // Find entry with smallest usage count, breaking ties with oldest timestamp
        String toRemove = entries.entrySet().stream()
                .min(Comparator.comparingInt((Map.Entry<String, LearnedEntry> e) -> e.getValue().getUsageCount())
                        .thenComparingLong(e -> e.getValue().getLastUpdated()))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (toRemove != null) {
            entries.remove(toRemove);
        }
    }

    public synchronized void reset() {
        entries.clear();
        save();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<LearnedEntry> getAllEntries() {
        return new ArrayList<>(entries.values());
    }

    public synchronized void load() {
        entries.clear();
        if (storageFile == null || !storageFile.exists() || !storageFile.isFile()) {
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("entries") && root.get("entries").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("entries");
                for (JsonElement elem : array) {
                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        String mistake = obj.has("mistake") ? obj.get("mistake").getAsString() : null;
                        String correction = obj.has("correction") ? obj.get("correction").getAsString() : null;
                        String context = obj.has("context") ? obj.get("context").getAsString() : "";
                        int usage = obj.has("usageCount") ? obj.get("usageCount").getAsInt() : 1;
                        long updated = obj.has("lastUpdated") ? obj.get("lastUpdated").getAsLong() : System.currentTimeMillis();

                        if (mistake != null && !mistake.isEmpty() && correction != null && !correction.isEmpty()) {
                            String key = makeKey(mistake, context);
                            entries.put(key, new LearnedEntry(normalizeKey(mistake), correction, normalizeKey(context), usage, updated));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Corrupted or malformed file: back up to .bak and start fresh
            System.err.println("[Voice2Text] Warning: Failed to parse learned corrections (" + t.getMessage() + "). Backing up to .bak.");
            try {
                File bakFile = new File(storageFile.getParentFile(), storageFile.getName() + ".bak");
                if (storageFile.exists()) {
                    if (bakFile.exists()) bakFile.delete();
                    storageFile.renameTo(bakFile);
                }
            } catch (Throwable ignored) {
            }
            entries.clear();
        }
    }

    public synchronized void save() {
        if (storageFile == null) return;
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("maxEntries", maxEntries);

            JsonArray array = new JsonArray();
            for (LearnedEntry entry : entries.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("mistake", entry.getMistake());
                obj.addProperty("correction", entry.getCorrection());
                obj.addProperty("context", entry.getContext());
                obj.addProperty("usageCount", entry.getUsageCount());
                obj.addProperty("lastUpdated", entry.getLastUpdated());
                array.add(obj);
            }
            root.add("entries", array);

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (Throwable t) {
            System.err.println("[Voice2Text] Failed to save learned corrections: " + t.getMessage());
        }
    }
}

