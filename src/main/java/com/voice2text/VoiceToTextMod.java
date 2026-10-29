package com.voice2text;

import com.voice2text.compat.MinecraftChatBridge;
import com.voice2text.compat.VanillaMinecraftChatBridge;
import com.voice2text.config.ConfigManager;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.input.KeybindManager;
import com.voice2text.model.ModelManager;
import com.voice2text.ui.ListeningOverlay;
import com.voice2text.ui.VoiceConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.nio.file.Files;

/**
 * Main Fabric client mod entrypoint for SpeakCraft (Voice-to-Text).
 */
public class VoiceToTextMod implements ClientModInitializer {

    public static final String MOD_ID = "voice2text";
    private static VoiceToTextMod instance;

    /**
     * Dedicated SpeakCraft controls category registered with Fabric/Minecraft.
     * Appears in Options -> Controls -> Key Binds -> SpeakCraft.
     */
    public static final KeyMapping.Category SPEAKCRAFT_CATEGORY;
    static {
        KeyMapping.Category category;
        Identifier catId = Identifier.fromNamespaceAndPath("speakcraft", "main");
        try {
            category = KeyMapping.Category.register(catId);
        } catch (IllegalArgumentException e) {
            category = new KeyMapping.Category(catId);
        }
        SPEAKCRAFT_CATEGORY = category;
    }

    private ConfigManager configManager;
    private ModelManager modelManager;
    private MinecraftChatBridge chatBridge;
    private VoiceToTextController controller;
    private ListeningOverlay listeningOverlay;

    private KeyMapping activationKeyMapping;
    private KeyMapping toggleKeyMapping;
    private KeyMapping stopKeyMapping;
    private KeyMapping configKeyMapping;

    @Override
    public void onInitializeClient() {
        System.setProperty("jna.encoding", "UTF-8");
        instance = this;

        // Instance dedicated directory: .minecraft/voice2text/
        File baseDir = FabricLoader.getInstance().getGameDir().resolve("voice2text").toFile();
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        File configFile = new File(baseDir, "config.json");
        File modelsDir = new File(baseDir, "models");
        File downloadsDir = new File(baseDir, "downloads");
        File tempDir = new File(baseDir, "temp");
        File logsDir = new File(baseDir, "logs");

        if (!modelsDir.exists()) modelsDir.mkdirs();
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        if (!tempDir.exists()) tempDir.mkdirs();
        if (!logsDir.exists()) logsDir.mkdirs();

        // Migration from legacy locations if present
        migrateLegacyFiles(configFile, modelsDir);

        this.configManager = new ConfigManager(configFile);
        this.modelManager = new ModelManager(modelsDir, downloadsDir, tempDir);
        this.chatBridge = new VanillaMinecraftChatBridge();

        this.controller = new VoiceToTextController(
                configManager,
                modelManager,
                chatBridge,
                task -> Minecraft.getInstance().execute(task)
        );

        this.listeningOverlay = new ListeningOverlay(controller);

        VoiceToTextConfig config = configManager.getConfig();

        // Register All 4 SpeakCraft Key Mappings under the SpeakCraft Category
        this.activationKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.speakcraft.activation",
                config.getActivationKeyCode(),
                SPEAKCRAFT_CATEGORY
        ));

        this.toggleKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.speakcraft.toggle",
                config.getDedicatedToggleKeyCode(),
                SPEAKCRAFT_CATEGORY
        ));

        this.stopKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.speakcraft.stop",
                config.getStopKeyCode(),
                SPEAKCRAFT_CATEGORY
        ));

        this.configKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.speakcraft.config",
                config.getConfigKeyCode(),
                SPEAKCRAFT_CATEGORY
        ));

        this.controller.getKeybindManager().setKeyMappings(
                this.activationKeyMapping,
                this.toggleKeyMapping,
                this.stopKeyMapping,
                this.configKeyMapping
        );

        // Register client tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            controller.tick(now);

            // Synchronize any key changes made in Minecraft Controls screen
            if (controller.getKeybindManager().syncFromMinecraftKeyMappings()) {
                configManager.save(config);
            }

            while (configKeyMapping.consumeClick()) {
                if (client.gui != null && !(client.gui.screen() instanceof VoiceConfigScreen)) {
                    client.gui.setScreen(new VoiceConfigScreen(client.gui.screen()));
                }
            }
        });

        // Register shutdown hook for clean engine resource deallocation
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (controller != null) {
                controller.shutdown();
            }
        }, "SpeakCraft-ShutdownHook"));

        // Preload speech recognition model in background if already installed
        this.controller.preloadModelAsync();

        System.out.println("[SpeakCraft] SpeakCraft Real-Time Voice-to-Text mod initialized successfully.");
    }

    private void migrateLegacyFiles(File newConfigFile, File newModelsDir) {
        try {
            // 1. From .minecraft/config/speakcraft/
            File oldConfigDir = FabricLoader.getInstance().getConfigDir().resolve("speakcraft").toFile();
            File oldConfigFile = new File(oldConfigDir, "config.json");
            if (!newConfigFile.exists() && oldConfigFile.exists()) {
                Files.copy(oldConfigFile.toPath(), newConfigFile.toPath());
            }
            File oldModelsDir = new File(oldConfigDir, "models");
            migrateDirectoryContents(oldModelsDir, newModelsDir);

            // 2. From .minecraft/config/voice2text.json
            File legacyConfig = FabricLoader.getInstance().getConfigDir().resolve("voice2text.json").toFile();
            if (!newConfigFile.exists() && legacyConfig.exists()) {
                Files.copy(legacyConfig.toPath(), newConfigFile.toPath());
            }

            // 3. From .minecraft/models
            File legacyRootModels = FabricLoader.getInstance().getGameDir().resolve("models").toFile();
            migrateDirectoryContents(legacyRootModels, newModelsDir);
        } catch (Throwable ignored) {
        }
    }

    private void migrateDirectoryContents(File sourceDir, File targetDir) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) return;
        File[] files = sourceDir.listFiles();
        if (files == null || files.length == 0) return;
        if (!targetDir.exists()) targetDir.mkdirs();

        for (File f : files) {
            File dest = new File(targetDir, f.getName());
            if (!dest.exists()) {
                try {
                    Files.move(f.toPath(), dest.toPath());
                } catch (Throwable t) {
                    try {
                        Files.copy(f.toPath(), dest.toPath());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    public static VoiceToTextMod getInstance() {
        return instance;
    }

    public VoiceToTextController getController() {
        return controller;
    }

    public ListeningOverlay getListeningOverlay() {
        return listeningOverlay;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public com.voice2text.config.VoiceToTextConfig getConfig() {
        return configManager != null ? configManager.getConfig() : null;
    }

    public ModelManager getModelManager() {
        return modelManager;
    }

    public MinecraftChatBridge getChatBridge() {
        return chatBridge;
    }

    public KeybindManager getKeybindManager() {
        return controller != null ? controller.getKeybindManager() : null;
    }

    public KeyMapping getActivationKeyMapping() {
        return activationKeyMapping;
    }

    public KeyMapping getToggleKeyMapping() {
        return toggleKeyMapping;
    }

    public KeyMapping getStopKeyMapping() {
        return stopKeyMapping;
    }

    public KeyMapping getConfigKeyMapping() {
        return configKeyMapping;
    }
}
