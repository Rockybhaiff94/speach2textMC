package com.voice2text.ui;

import com.voice2text.VoiceToTextMod;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.model.DownloadProgressListener;
import com.voice2text.model.ModelInstallationState;
import com.voice2text.model.ModelManager;
import com.voice2text.model.SpeechModelInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;

/**
 * Compact modal dialog for viewing model details, reinstalling, or permanently deleting an installed AI model.
 */
public class ModelManageScreen extends Screen {

    public enum ScreenMode {
        VIEW,
        CONFIRM_DELETE,
        REINSTALLING
    }

    private final Screen parent;
    private final String modelId;
    private final ModelManager modelManager;
    private final VoiceToTextConfig config;

    private ScreenMode currentMode = ScreenMode.VIEW;

    // Reinstallation progress
    private String reinstallationStatus = "";
    private int reinstallationPercent = -1;
    private long bytesDownloaded = 0;
    private long totalBytesExpected = 0;

    public ModelManageScreen(Screen parent, String modelId) {
        super(Component.literal("Model Management"));
        this.parent = parent;
        this.modelId = modelId;

        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        this.modelManager = mod != null ? mod.getModelManager() : new ModelManager(new File("."));
        this.config = mod != null ? mod.getConfigManager().getConfig() : new VoiceToTextConfig();
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();

        int centerX = this.width / 2;
        int btnW = 145;
        int btnH = 20;

        boolean isInstalled = modelManager.isModelInstalled(modelId);
        boolean isActive = modelId.equals(config.getActiveModelId());

        if (currentMode == ScreenMode.CONFIRM_DELETE) {
            int actionY = this.height / 2 + 50;

            addRenderableWidget(Button.builder(
                    Component.literal("§c✔ Confirm Deletion"),
                    button -> executePermanentDeletion()
            ).tooltip(Tooltip.create(Component.literal("Permanently deletes model files and frees disk space.")))
             .bounds(centerX - btnW - 4, actionY, btnW, btnH).build());

            addRenderableWidget(Button.builder(
                    Component.literal("Cancel"),
                    button -> {
                        currentMode = ScreenMode.VIEW;
                        rebuildWidgets();
                    }
            ).bounds(centerX + 4, actionY, btnW, btnH).build());
            return;
        }

        if (currentMode == ScreenMode.REINSTALLING) {
            int actionY = this.height / 2 + 50;

            addRenderableWidget(Button.builder(
                    Component.literal("§c✕ Cancel Reinstallation"),
                    button -> cancelReinstallation()
            ).bounds(centerX - (btnW / 2), actionY, btnW, btnH).build());
            return;
        }

        // --- NORMAL VIEW MODE ---
        int actionY = this.height / 2 + 45;

        // Button 1: Reinstall Model
        addRenderableWidget(Button.builder(
                Component.literal("⟳ Reinstall Model"),
                button -> startReinstallation()
        ).tooltip(Tooltip.create(Component.literal("Downloads a fresh verified copy and replaces files safely without data loss on failure.")))
         .bounds(centerX - btnW - 4, actionY, btnW, btnH).build());

        // Button 2: Delete Model Permanently
        addRenderableWidget(Button.builder(
                Component.literal("§c🗑 Delete Model"),
                button -> {
                    currentMode = ScreenMode.CONFIRM_DELETE;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Permanently deletes this model's files from disk.")))
         .bounds(centerX + 4, actionY, btnW, btnH).build());

        // Button 3: Set as Active (if installed and not active)
        if (isInstalled && !isActive) {
            addRenderableWidget(Button.builder(
                    Component.literal("§a✔ Set as Active Engine"),
                    button -> {
                        config.setActiveModelId(modelId);
                        VoiceToTextMod mod = VoiceToTextMod.getInstance();
                        if (mod != null) {
                            mod.getConfigManager().save(config);
                            if (mod.getController() != null) {
                                mod.getController().switchModel(modelId);
                            }
                        }
                        rebuildWidgets();
                    }
            ).bounds(centerX - btnW - 4, actionY + 24, btnW, btnH).build());
        }

        // Button 4: Back / Done
        int backX = (isInstalled && !isActive) ? centerX + 4 : centerX - (btnW / 2);
        int backY = (isInstalled && !isActive) ? actionY + 24 : actionY + 24;
        addRenderableWidget(Button.builder(
                Component.literal("Back"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parent);
                    }
                }
        ).bounds(backX, backY, btnW, btnH).build());
    }

    private void startReinstallation() {
        currentMode = ScreenMode.REINSTALLING;
        reinstallationPercent = 0;
        bytesDownloaded = 0;
        reinstallationStatus = "Starting download...";
        rebuildWidgets();

        // Release file locks before reinstallation
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getController() != null) {
            mod.getController().releaseModelResources(modelId);
        }

        modelManager.reinstallModelAsync(modelId, new DownloadProgressListener() {
            @Override
            public void onProgress(long bytesRead, long totalBytes, int percent) {
                bytesDownloaded = bytesRead;
                totalBytesExpected = totalBytes;
                reinstallationPercent = percent;
            }

            @Override
            public void onStatus(String statusMessage) {
                reinstallationStatus = statusMessage;
            }

            @Override
            public void onCompleted() {
                currentMode = ScreenMode.VIEW;
                reinstallationStatus = "Reinstallation completed successfully!";

                VoiceToTextMod mod = VoiceToTextMod.getInstance();
                if (mod != null && mod.getController() != null && modelId.equals(config.getActiveModelId())) {
                    mod.getController().switchModel(modelId);
                }

                if (minecraft != null) {
                    minecraft.execute(ModelManageScreen.this::rebuildWidgets);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                currentMode = ScreenMode.VIEW;
                reinstallationStatus = "§cReinstallation failed: " + errorMessage;
                if (minecraft != null) {
                    minecraft.execute(ModelManageScreen.this::rebuildWidgets);
                }
            }
        });
    }

    private void cancelReinstallation() {
        modelManager.cancelDownload(modelId);
        currentMode = ScreenMode.VIEW;
        reinstallationStatus = "§eReinstallation cancelled by user.";
        rebuildWidgets();
    }

    private void executePermanentDeletion() {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        boolean wasActive = modelId.equals(config.getActiveModelId());

        // 1. Active model protection: Release engine resources and file handles
        if (mod != null && mod.getController() != null) {
            mod.getController().releaseModelResources(modelId);
        }

        // 2. Perform deletion
        modelManager.deleteModel(modelId);

        // 3. Fallback switching if deleted model was active
        if (wasActive) {
            String fallbackId = VoiceConfigScreen.MODEL_WHISPER_SMALL.equals(modelId)
                    ? VoiceConfigScreen.MODEL_APEX : VoiceConfigScreen.MODEL_WHISPER_SMALL;
            if (modelManager.isModelInstalled(fallbackId)) {
                config.setActiveModelId(fallbackId);
                if (mod != null) {
                    mod.getConfigManager().save(config);
                    if (mod.getController() != null) {
                        mod.getController().switchModel(fallbackId);
                    }
                }
            }
        }

        // 4. Return to parent screen
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        SpeechModelInfo info = modelManager.getModelInfo(modelId);
        String displayName = info != null ? info.getDisplayName() : modelId;
        boolean isActive = modelId.equals(config.getActiveModelId());

        if (currentMode == ScreenMode.CONFIRM_DELETE) {
            extractor.centeredText(this.font, "§c§lPermanently Delete " + displayName + "?", centerX, centerY - 65, 0xFFFF5555);
            extractor.centeredText(this.font, "§7This will remove all downloaded files for this model from disk.", centerX, centerY - 45, 0xFFAAAAAA);

            File modelDir = modelManager.getModelDir(modelId);
            String pathStr = modelDir != null ? modelDir.getAbsolutePath() : "models/" + modelId;
            if (pathStr.length() > 55) {
                pathStr = "..." + pathStr.substring(pathStr.length() - 52);
            }
            extractor.centeredText(this.font, "§8Location: " + pathStr, centerX, centerY - 30, 0xFF888888);

            if (isActive) {
                extractor.centeredText(this.font, "§e⚠ Active Model Warning: This model is currently in use.", centerX, centerY - 10, 0xFFFFAA00);
                extractor.centeredText(this.font, "§eEngine resources will be safely released before deletion.", centerX, centerY + 3, 0xFFFFAA00);
            } else {
                extractor.centeredText(this.font, "§7This action cannot be undone.", centerX, centerY - 10, 0xFF888888);
            }
            return;
        }

        if (currentMode == ScreenMode.REINSTALLING) {
            extractor.centeredText(this.font, "§lReinstalling " + displayName, centerX, centerY - 60, 0xFFFFFFFF);
            extractor.centeredText(this.font, "§7Downloading fresh copy and verifying integrity...", centerX, centerY - 40, 0xFFAAAAAA);

            String progressText;
            if (reinstallationPercent >= 0) {
                double curMb = (double) bytesDownloaded / (1024 * 1024);
                double totMb = (double) totalBytesExpected / (1024 * 1024);
                progressText = String.format("§e%s: %.1f MB / %.1f MB (%d%%)", reinstallationStatus, curMb, totMb, reinstallationPercent);
            } else {
                progressText = reinstallationStatus;
            }
            extractor.centeredText(this.font, progressText, centerX, centerY - 10, 0xFFFFFFFF);
            extractor.centeredText(this.font, "§7Previous installation will remain intact if download fails.", centerX, centerY + 10, 0xFF888888);
            return;
        }

        // --- NORMAL DETAILS VIEW ---
        extractor.centeredText(this.font, "§lModel Details & Management", centerX, centerY - 80, 0xFFFFFFFF);
        extractor.centeredText(this.font, "§b" + displayName, centerX, centerY - 66, 0xFF55FFFF);

        int leftX = centerX - 145;
        int startY = centerY - 50;
        int lineSpacing = 13;

        // Model ID
        extractor.text(this.font, "§7Model ID: §f" + modelId, leftX, startY, 0xFFFFFFFF);

        // Architecture & Quantization
        String family = info != null ? info.getFamily().getDisplayTitle() : "Vosk / Kaldi";
        String quant = info != null ? info.getQuantization() : "Standard";
        extractor.text(this.font, "§7Architecture: §f" + family + " §8(" + quant + ")", leftX, startY + lineSpacing, 0xFFFFFFFF);

        // Runtime Engine
        String runtime = info != null ? info.getRuntimeCompatibility() : "Local Offline Runtime";
        extractor.text(this.font, "§7Runtime: §f" + runtime, leftX, startY + (lineSpacing * 2), 0xFFFFFFFF);

        // Disk Size
        String diskSize = modelManager.getFormattedDiskSize(modelId);
        extractor.text(this.font, "§7Size on Disk: §f" + diskSize, leftX, startY + (lineSpacing * 3), 0xFFFFFFFF);

        // Installation State
        ModelInstallationState state = modelManager.getModelInstallationState(modelId);
        extractor.text(this.font, "§7Status: " + state.getColoredLabel(), leftX, startY + (lineSpacing * 4), 0xFFFFFFFF);

        // Active Engine Status
        String activeStatus = isActive ? "§a✔ Active Speech Engine" : "§7Inactive";
        extractor.text(this.font, "§7Active State: " + activeStatus, leftX, startY + (lineSpacing * 5), 0xFFFFFFFF);

        // Storage Path
        File modelDir = modelManager.getModelDir(modelId);
        String pathStr = modelDir != null ? modelDir.getAbsolutePath() : "models/" + modelId;
        if (pathStr.length() > 50) {
            pathStr = "..." + pathStr.substring(pathStr.length() - 47);
        }
        extractor.text(this.font, "§8Storage: " + pathStr, leftX, startY + (lineSpacing * 6), 0xFF888888);

        // Status / Message footer
        if (!reinstallationStatus.isEmpty()) {
            extractor.centeredText(this.font, reinstallationStatus, centerX, centerY + 30, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
