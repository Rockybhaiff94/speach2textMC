package com.voice2text.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Persists and loads VoiceToTextConfig to/from JSON file.
 */
public class ConfigManager {

    private final File configFile;
    private final Gson gson;
    private VoiceToTextConfig currentConfig;

    public ConfigManager(File configFile) {
        this.configFile = configFile;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.currentConfig = load();
    }

    public synchronized VoiceToTextConfig load() {
        if (!configFile.exists()) {
            VoiceToTextConfig defaults = new VoiceToTextConfig();
            defaults.validate();
            this.currentConfig = defaults;
            save(defaults);
            return defaults;
        }

        try (FileReader reader = new FileReader(configFile)) {
            VoiceToTextConfig loaded = gson.fromJson(reader, VoiceToTextConfig.class);
            if (loaded == null) {
                loaded = new VoiceToTextConfig();
            }
            boolean needsMigration = "LAYOUT_1".equalsIgnoreCase(loaded.getInterfaceLayout());
            loaded.validate();
            this.currentConfig = loaded;
            if (needsMigration) {
                save(loaded);
            }
            return loaded;
        } catch (Exception e) {
            System.err.println("[Voice2Text] Failed to read config file, falling back to defaults: " + e.getMessage());
            VoiceToTextConfig defaults = new VoiceToTextConfig();
            defaults.validate();
            this.currentConfig = defaults;
            return defaults;
        }
    }

    public synchronized void save(VoiceToTextConfig config) {
        if (config == null) {
            return;
        }
        config.validate();
        this.currentConfig = config;

        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("[Voice2Text] Failed to save config file: " + e.getMessage());
        }
    }

    public synchronized VoiceToTextConfig getConfig() {
        if (currentConfig == null) {
            currentConfig = load();
        }
        return currentConfig;
    }
}
