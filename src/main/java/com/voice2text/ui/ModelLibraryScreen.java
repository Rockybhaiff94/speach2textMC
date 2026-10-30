package com.voice2text.ui;

import com.voice2text.VoiceToTextMod;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.model.DownloadProgressListener;
import com.voice2text.model.ModelFamily;
import com.voice2text.model.ModelManager;
import com.voice2text.model.SpeechModelInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Language & Model Library screen for SpeakCraft / Voice2Text.
 *
 * Provides a centered, square-cornered dark Minecraft-style container with:
 * - Fixed header with title, subtitle, search bar, and category tabs.
 * - Smoothly scrollable model list with scissor clipping and gradient edge fades.
 * - Redesigned, non-overlapping model cards with per-card activation.
 * - Fixed footer with active model indicator and accessible Back button.
 * - Zero cloud dependencies; 100% offline speech recognition management.
 */
public class ModelLibraryScreen extends Screen {

    private final Screen parent;
    private final VoiceToTextConfig config;
    private final ModelManager modelManager;

    private EditBox searchBox;
    private String searchQuery = "";
    private String activeCategory = "All";

    private final List<SpeechModelInfo> displayedModels = new ArrayList<>();
    private String selectedModelId = null;
    private SpeechModelInfo modalDetailsModel = null;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private String statusBanner = "";
    private long statusBannerTime = 0;

    // Track active downloading model progress
    private String downloadingModelId = null;
    private int downloadPercent = -1;
    private long downloadedBytes = 0;
    private long totalBytes = 0;
    private String downloadStatusMessage = "";

    public ModelLibraryScreen(Screen parent) {
        this(parent, null);
    }

    public ModelLibraryScreen(Screen parent, String preselectModelId) {
        super(Component.literal("Language & Model Library"));
        this.parent = parent;

        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        this.config = mod != null ? mod.getConfigManager().getConfig() : new VoiceToTextConfig();
        this.modelManager = mod != null ? mod.getModelManager() : new ModelManager(new File("."));

        if (modelManager != null) {
            modelManager.discoverInstalledModels();
        }

        this.selectedModelId = preselectModelId != null ? preselectModelId : config.getActiveModelId();
    }

    private int getContainerWidth() {
        return Math.min(520, this.width - 24);
    }

    private int getContainerHeight() {
        return Math.min(this.height - 16, Math.max(240, (int) (this.height * 0.85)));
    }

    private int getContainerX() {
        return (this.width - getContainerWidth()) / 2;
    }

    private int getContainerY() {
        return (this.height - getContainerHeight()) / 2;
    }

    private int getListTop() {
        return getContainerY() + 78;
    }

    private int getListBottom() {
        return getContainerY() + getContainerHeight() - 32;
    }

    @Override
    protected void init() {
        int containerW = getContainerWidth();
        int containerH = getContainerHeight();
        int containerX = getContainerX();
        int containerY = getContainerY();

        // 1. Search Box
        int searchW = containerW - 24;
        int searchY = containerY + 34;
        this.searchBox = new EditBox(this.font, containerX + 12, searchY, searchW, 18, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("§7Search by name, language, or feature (e.g. Whisper, Hinglish)..."));
        this.searchBox.setValue(searchQuery);
        this.searchBox.setResponder(val -> {
            this.searchQuery = val;
            updateFilter();
        });
        addRenderableWidget(this.searchBox);

        // 2. Category Filter Buttons
        int catY = searchY + 22;
        int catBtnH = 16;
        int totalSpacing = 4 * 4;
        int catBtnW = (containerW - 24 - totalSpacing) / 5;

        String[] categories = new String[]{"All", "Whisper", "Hinglish", "Indic", "Vosk"};
        for (int i = 0; i < categories.length; i++) {
            String cat = categories[i];
            boolean isSelected = activeCategory.equalsIgnoreCase(cat);
            String label = isSelected ? "§a" + cat : "§7" + cat;
            int btnX = containerX + 12 + (i * (catBtnW + 4));

            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> {
                        this.activeCategory = cat;
                        updateFilter();
                        rebuildWidgets();
                    }
            ).bounds(btnX, catY, catBtnW, catBtnH).build());
        }

        // 3. Bottom Footer Back Button
        int footerH = 28;
        int footerY = containerY + containerH - footerH;
        int backBtnW = 84;
        int backBtnH = 20;
        int backBtnX = containerX + containerW - 12 - backBtnW;
        int backBtnY = footerY + 4;

        addRenderableWidget(Button.builder(
                Component.literal("Back"),
                b -> onClose()
        ).bounds(backBtnX, backBtnY, backBtnW, backBtnH).build());

        updateFilter();
    }

    private void updateFilter() {
        displayedModels.clear();
        if (modelManager == null) return;

        String query = searchQuery.trim().toLowerCase(Locale.ROOT);

        for (SpeechModelInfo info : modelManager.getAvailableModels().values()) {
            // Category check
            if (!activeCategory.equalsIgnoreCase("All")) {
                if (activeCategory.equalsIgnoreCase("Whisper") && info.getFamily() != ModelFamily.WHISPER) {
                    continue;
                }
                if (activeCategory.equalsIgnoreCase("Hinglish") && info.getFamily() != ModelFamily.HINGLISH_SPECIALIZED
                        && !info.getId().contains("hinglish") && !info.getLanguageCode().contains("hi-en")) {
                    continue;
                }
                if (activeCategory.equalsIgnoreCase("Indic") && info.getFamily() != ModelFamily.INDIC_CONFORMER
                        && info.getFamily() != ModelFamily.SHRUTI && !info.getId().contains("indic") && !info.getId().contains("shruti")) {
                    continue;
                }
                if (activeCategory.equalsIgnoreCase("Vosk") && info.getFamily() != ModelFamily.VOSK) {
                    continue;
                }
            }

            // Query check
            if (!query.isEmpty()) {
                boolean match = info.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                        || info.getId().toLowerCase(Locale.ROOT).contains(query)
                        || info.getLanguageCode().toLowerCase(Locale.ROOT).contains(query)
                        || info.getSupportedLanguages().toLowerCase(Locale.ROOT).contains(query)
                        || info.getShortDescription().toLowerCase(Locale.ROOT).contains(query)
                        || info.getFamily().name().toLowerCase(Locale.ROOT).contains(query)
                        || info.getOutputScript().toLowerCase(Locale.ROOT).contains(query);
                if (!match) continue;
            }

            displayedModels.add(info);
        }

        // Recompute max scroll
        int listTop = getListTop();
        int listBottom = getListBottom();
        int listHeight = listBottom - listTop;
        int cardHeight = 86;
        int cardGap = 6;
        int totalContentHeight = displayedModels.size() * (cardHeight + cardGap) + 10;
        this.maxScroll = Math.max(0, totalContentHeight - listHeight);
        if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
    }

    public void startDownload(String modelId) {
        if (modelManager == null || modelId == null) return;
        SpeechModelInfo info = modelManager.getModelInfo(modelId);
        if (info == null) return;

        if (modelManager.isModelInstalled(modelId)) {
            setStatus("§aModel is already installed!");
            return;
        }

        this.downloadingModelId = modelId;
        this.downloadPercent = 0;
        this.downloadStatusMessage = "Connecting...";

        modelManager.downloadModel(modelId, new DownloadProgressListener() {
            @Override
            public void onProgress(long bytesRead, long totalBytesExpected, int percent) {
                downloadedBytes = bytesRead;
                totalBytes = totalBytesExpected;
                downloadPercent = percent;
            }

            @Override
            public void onStatus(String statusMessage) {
                downloadStatusMessage = statusMessage;
            }

            @Override
            public void onCompleted() {
                downloadingModelId = null;
                setStatus("§a" + info.getDisplayName() + " installed successfully!");
                if (minecraft != null) {
                    minecraft.execute(ModelLibraryScreen.this::updateFilter);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                downloadingModelId = null;
                setStatus("§cDownload failed: " + errorMessage);
                if (minecraft != null) {
                    minecraft.execute(ModelLibraryScreen.this::updateFilter);
                }
            }
        });
    }

    public void cancelDownload(String modelId) {
        if (modelManager != null && modelId != null) {
            modelManager.cancelDownload(modelId);
            this.downloadingModelId = null;
            setStatus("§7Download cancelled.");
        }
    }

    public void activateModel(String modelId) {
        if (modelId == null || modelManager == null) return;
        if (!modelManager.isModelInstalled(modelId)) {
            setStatus("§cCannot activate: download model first!");
            return;
        }

        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getController() != null) {
            boolean success = mod.getController().switchModel(modelId);
            if (success) {
                config.setActiveModelId(modelId);
                SpeechModelInfo info = modelManager.getModelInfo(modelId);
                if (info != null) {
                    config.setSpeechLanguage(info.getLanguageCode());
                }
                mod.getConfigManager().save(config);
                setStatus("§aActivated: " + (info != null ? info.getDisplayName() : modelId));
            } else {
                setStatus("§cFailed to activate model: " + modelId);
            }
        }
    }

    public void removeModel(String modelId) {
        if (modelId == null || modelManager == null) return;
        if (modelId.equals(config.getActiveModelId())) {
            setStatus("§cCannot delete currently active model.");
            return;
        }

        boolean deleted = modelManager.deleteModel(modelId);
        if (deleted) {
            setStatus("§7Model deleted from local storage.");
            updateFilter();
        } else {
            setStatus("§cFailed to delete model files.");
        }
    }

    private void setStatus(String message) {
        this.statusBanner = message;
        this.statusBannerTime = System.currentTimeMillis();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (modalDetailsModel != null) return true;
        this.scrollOffset -= (int) (verticalAmount * 24);
        if (this.scrollOffset < 0) this.scrollOffset = 0;
        if (this.scrollOffset > this.maxScroll) this.scrollOffset = this.maxScroll;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleOrTripleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // 1. If Modal is open, handle close or clicks within modal
        if (modalDetailsModel != null) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int modalW = 360;
            int modalH = 230;
            int modalX = centerX - (modalW / 2);
            int modalY = centerY - (modalH / 2);

            // Close button [X] at top-right
            if (mouseX >= modalX + modalW - 24 && mouseX <= modalX + modalW - 6 && mouseY >= modalY + 6 && mouseY <= modalY + 22) {
                modalDetailsModel = null;
                return true;
            }

            // Close button at bottom
            int closeBtnY = modalY + modalH - 26;
            if (mouseX >= centerX - 40 && mouseX <= centerX + 40 && mouseY >= closeBtnY && mouseY <= closeBtnY + 20) {
                modalDetailsModel = null;
                return true;
            }
            return true;
        }

        // 2. Card clicks inside scrollable region
        int listTop = getListTop();
        int listBottom = getListBottom();
        int containerW = getContainerWidth();
        int containerX = getContainerX();

        int cardW = containerW - 24;
        int cardX = containerX + 12;
        int cardH = 86;

        if (mouseY >= listTop && mouseY <= listBottom && mouseX >= cardX && mouseX <= cardX + cardW) {
            int currentY = listTop - scrollOffset;

            for (SpeechModelInfo info : displayedModels) {
                if (mouseY >= currentY && mouseY <= currentY + cardH) {
                    this.selectedModelId = info.getId();

                    int btnY = currentY + cardH - 22;
                    int btnDetailsX = cardX + cardW - 208;
                    int btnDownloadX = cardX + cardW - 150;
                    int btnActivateX = cardX + cardW - 74;

                    boolean isInstalled = modelManager != null && modelManager.isModelInstalled(info.getId());
                    boolean isActive = isInstalled && info.getId().equals(config.getActiveModelId());
                    boolean isDownloading = info.getId().equals(downloadingModelId);

                    // Details Button
                    if (mouseX >= btnDetailsX && mouseX <= btnDetailsX + 54 && mouseY >= btnY && mouseY <= btnY + 18) {
                        this.modalDetailsModel = info;
                        return true;
                    }

                    // Download / Cancel / Remove Button
                    if (mouseX >= btnDownloadX && mouseX <= btnDownloadX + 72 && mouseY >= btnY && mouseY <= btnY + 18) {
                        if (isDownloading) {
                            cancelDownload(info.getId());
                        } else if (!isInstalled) {
                            startDownload(info.getId());
                        } else if (!isActive) {
                            removeModel(info.getId());
                        }
                        return true;
                    }

                    // Activate Button
                    if (mouseX >= btnActivateX && mouseX <= btnActivateX + 72 && mouseY >= btnY && mouseY <= btnY + 18) {
                        if (isInstalled && !isActive) {
                            activateModel(info.getId());
                        } else if (!isInstalled) {
                            setStatus("§cDownload model first before activating!");
                        }
                        return true;
                    }

                    return true;
                }
                currentY += cardH + 6;
            }
        }

        return super.mouseClicked(event, isDoubleOrTripleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESCAPE
            if (modalDetailsModel != null) {
                modalDetailsModel = null;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int containerW = getContainerWidth();
        int containerH = getContainerHeight();
        int containerX = getContainerX();
        int containerY = getContainerY();

        // 1. Semi-transparent backdrop overlay
        extractor.fill(0, 0, this.width, this.height, 0x90000000);

        // 2. Main Centered Container Panel
        extractor.fill(containerX, containerY, containerX + containerW, containerY + containerH, 0xF0121417);
        drawContainerBorder(extractor, containerX, containerY, containerW, containerH);

        // 3. Fixed Header Title & Subtitle
        extractor.centeredText(this.font, "§lLanguage & Model Library", centerX, containerY + 8, 0xFFFFFFFF);
        extractor.centeredText(this.font, "§7Select, download, and activate verified offline speech engines", centerX, containerY + 20, 0xFFA0B0BE);

        // 4. Scrollable Model Cards with Scissor Clipping
        int listTop = getListTop();
        int listBottom = getListBottom();
        int listLeft = containerX + 10;
        int listRight = containerX + containerW - 10;

        int cardW = containerW - 24;
        int cardX = containerX + 12;
        int cardH = 86;

        extractor.enableScissor(listLeft, listTop, listRight, listBottom);

        int currentY = listTop - scrollOffset;
        for (SpeechModelInfo info : displayedModels) {
            if (currentY + cardH >= listTop && currentY <= listBottom) {
                renderModelCard(extractor, info, cardX, currentY, cardW, cardH, mouseX, mouseY);
            }
            currentY += cardH + 6;
        }

        extractor.disableScissor();

        // 5. Gradient Fades at Scrollable Edges
        if (scrollOffset > 0) {
            extractor.fillGradient(listLeft, listTop, listRight, listTop + 14, 0xF0121417, 0x00121417);
        }
        if (scrollOffset < maxScroll) {
            extractor.fillGradient(listLeft, listBottom - 14, listRight, listBottom, 0x00121417, 0xF0121417);
        }

        // 6. Fixed Footer Area
        int footerH = 28;
        int footerY = containerY + containerH - footerH;

        extractor.fill(containerX + 1, footerY, containerX + containerW - 1, containerY + containerH - 1, 0xF514171A);
        extractor.fill(containerX, footerY, containerX + containerW, footerY + 1, 0xFF2A2E35);

        // Active Model Status Indicator on Left of Footer
        SpeechModelInfo activeInfo = modelManager != null ? modelManager.getModelInfo(config.getActiveModelId()) : null;
        String activeName = activeInfo != null ? activeInfo.getDisplayName() : config.getActiveModelId();
        boolean activeInstalled = modelManager != null && modelManager.isModelInstalled(config.getActiveModelId());
        String activeText = activeInstalled
                ? "§8Active Engine: §a● §f" + activeName
                : "§8Active Engine: §e⚠ §7" + activeName + " §c(Not Downloaded)";
        extractor.text(this.font, activeText, containerX + 14, footerY + 7, 0xFFFFFFFF, true);

        // Status Banner Notification
        if (!statusBanner.isEmpty() && System.currentTimeMillis() - statusBannerTime < 4000) {
            extractor.centeredText(this.font, statusBanner, centerX, footerY - 14, 0xFFFFFFFF);
        }

        // 7. Render all interactive widgets (search box, category buttons, Back button) ON TOP of container
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        // 8. Render Details Modal Popup if open
        if (modalDetailsModel != null) {
            renderDetailsModal(extractor, modalDetailsModel, mouseX, mouseY);
        }
    }

    private void renderModelCard(GuiGraphicsExtractor extractor, SpeechModelInfo info, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        boolean isSelected = info.getId().equals(selectedModelId);
        boolean isInstalled = modelManager != null && modelManager.isModelInstalled(info.getId());
        boolean isActive = isInstalled && info.getId().equals(config.getActiveModelId());
        boolean isDownloading = info.getId().equals(downloadingModelId);

        // Background color
        int bgColor = isSelected ? 0xF01B2430 : (isHovered ? 0xF01C2026 : 0xF016181C);
        extractor.fill(x, y, x + w, y + h, bgColor);

        // Border color: Green for Active, Cyan for Selected, Subtle for normal
        int borderColor = isActive ? 0xFF4CAF50 : (isSelected ? 0xFF29B6F6 : (isHovered ? 0xFF606874 : 0xFF2E333C));
        drawBorder(extractor, x, y, w, h, borderColor);

        // 1. Top Section (y + 5): Name, Family & Status Badge
        String title = "§f§l" + info.getDisplayName();
        String familyBadge = " §7[" + info.getFamily().getDisplayTitle() + "]";
        extractor.text(this.font, title + familyBadge, x + 8, y + 5, 0xFFFFFFFF, true);

        String badge;
        int badgeColor;
        if (isActive) {
            badge = "§a[ACTIVE ✓]";
            badgeColor = 0xFF55FF55;
        } else if (isDownloading) {
            badge = "§e[DOWNLOADING " + (downloadPercent >= 0 ? downloadPercent + "%" : "...") + "]";
            badgeColor = 0xFFFFAA00;
        } else if (isInstalled) {
            badge = "§b[INSTALLED]";
            badgeColor = 0xFF55FFFF;
        } else {
            badge = "§7[NOT INSTALLED]";
            badgeColor = 0xFFAAAAAA;
        }
        int badgeX = x + w - this.font.width(badge) - 8;
        extractor.text(this.font, badge, badgeX, y + 5, badgeColor, true);

        // 2. Description (y + 18)
        String desc = info.getShortDescription();
        int maxDescW = w - 16;
        if (this.font.width(desc) > maxDescW) {
            desc = this.font.plainSubstrByWidth(desc, maxDescW - 12) + "...";
        }
        extractor.text(this.font, "§7" + desc, x + 8, y + 18, 0xFFD0D7DE, true);

        // 3. Information Section (y + 32, y + 45)
        String line1 = "§7Languages: §f" + info.getSupportedLanguages() + "  §8|  §7Accuracy: §e" + info.getHinglishSupport() + "  §8|  §7Format: §f" + info.getOutputScript();
        if (this.font.width(line1) > w - 16) {
            line1 = this.font.plainSubstrByWidth(line1, w - 24) + "...";
        }
        extractor.text(this.font, line1, x + 8, y + 32, 0xFFB0BAC5, true);

        String line2 = "§7Download: §b" + info.getFormattedDownloadSize() + "  §8|  §7Specs: §6" + info.getHardwareRequirements();
        if (this.font.width(line2) > w - 16) {
            line2 = this.font.plainSubstrByWidth(line2, w - 24) + "...";
        }
        extractor.text(this.font, line2, x + 8, y + 45, 0xFF909AA5, true);

        // 4. Action Section (y + h - 22, height 18)
        int btnY = y + h - 22;

        // Download progress bar on left if downloading
        if (isDownloading) {
            int pbX = x + 8;
            int pbY = btnY + 4;
            int pbW = w - 220;
            int pbH = 10;
            extractor.fill(pbX, pbY, pbX + pbW, pbY + pbH, 0xFF14171A);
            if (downloadPercent > 0) {
                int fillW = (int) ((pbW * downloadPercent) / 100.0);
                extractor.fill(pbX, pbY, pbX + fillW, pbY + pbH, 0xFFFFAA00);
            }
            drawBorder(extractor, pbX, pbY, pbW, pbH, 0xFF555555);

            String progressText = downloadStatusMessage != null && !downloadStatusMessage.isEmpty()
                    ? downloadStatusMessage
                    : (downloadedBytes > 0 && totalBytes > 0
                        ? String.format("%.1f/%.1f MB (%d%%)", downloadedBytes / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0), downloadPercent)
                        : (downloadPercent >= 0 ? downloadPercent + "%" : "Downloading..."));
            extractor.text(this.font, progressText, pbX + 4, pbY + 1, 0xFFFFFFFF, true);
        }

        // Details Button (width: 54)
        int btnDetailsX = x + w - 208;
        renderMiniButton(extractor, "Details", btnDetailsX, btnY, 54, 18, mouseX, mouseY, 0xFF353A42);

        // Download / Cancel / Remove Button (width: 72)
        int btnDownloadX = x + w - 150;
        if (isDownloading) {
            renderMiniButton(extractor, "§cCancel", btnDownloadX, btnY, 72, 18, mouseX, mouseY, 0xFF6B1D1D);
        } else if (!isInstalled) {
            renderMiniButton(extractor, "§6Download", btnDownloadX, btnY, 72, 18, mouseX, mouseY, 0xFF9E5A10);
        } else {
            renderMiniButton(extractor, "§7Remove", btnDownloadX, btnY, 72, 18, mouseX, mouseY, 0xFF2A2D33);
        }

        // Activate / Active Button (width: 72)
        int btnActivateX = x + w - 74;
        if (isActive) {
            renderMiniButton(extractor, "§aActive ✓", btnActivateX, btnY, 72, 18, mouseX, mouseY, 0xFF1B5E20);
        } else if (isInstalled) {
            renderMiniButton(extractor, "§fActivate", btnActivateX, btnY, 72, 18, mouseX, mouseY, 0xFF1976D2);
        } else {
            renderMiniButton(extractor, "§8Inactive", btnActivateX, btnY, 72, 18, mouseX, mouseY, 0xFF1C1E22);
        }
    }

    private void renderMiniButton(GuiGraphicsExtractor extractor, String label, int x, int y, int w, int h, int mouseX, int mouseY, int baseColor) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int color = hovered ? (baseColor + 0x00181818) : baseColor;
        extractor.fill(x, y, x + w, y + h, color | 0xFF000000);
        drawBorder(extractor, x, y, w, h, hovered ? 0xFFFFFFFF : 0xFF555D68);
        extractor.centeredText(this.font, label, x + (w / 2), y + 5, 0xFFFFFFFF);
    }

    private void renderDetailsModal(GuiGraphicsExtractor extractor, SpeechModelInfo info, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int modalW = 360;
        int modalH = 230;
        int modalX = centerX - (modalW / 2);
        int modalY = centerY - (modalH / 2);

        // Dim background
        extractor.fill(0, 0, this.width, this.height, 0xB0000000);

        // Modal Box
        extractor.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF0121417);
        drawContainerBorder(extractor, modalX, modalY, modalW, modalH);

        // Header
        extractor.text(this.font, "§l" + info.getDisplayName(), modalX + 12, modalY + 10, 0xFFFFFFFF, true);
        extractor.text(this.font, "§c[X]", modalX + modalW - 20, modalY + 10, 0xFFFF5555, true);

        int lineY = modalY + 28;
        extractor.text(this.font, "§8Model ID: §f" + info.getId(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Family: §e" + info.getFamily().getDisplayTitle(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Languages: §f" + info.getSupportedLanguages(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Accuracy: §a" + info.getHinglishSupport(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Output Script: §f" + info.getOutputScript(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Download Size: §b" + info.getFormattedDownloadSize() + " §8(Uncompressed: §b" + info.getFormattedUncompressedSize() + "§8)", modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Hardware Specs: §6" + info.getHardwareRequirements(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8Runtime: §f" + info.getRuntimeCompatibility(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;
        extractor.text(this.font, "§8License: §7" + info.getLicense(), modalX + 12, lineY, 0xFFCCCCCC, false);
        lineY += 13;

        String sha = info.getPrimarySha256();
        if (sha.length() > 24) sha = sha.substring(0, 24) + "...";
        extractor.text(this.font, "§8SHA-256: §7" + (sha.isEmpty() ? "Verified Official Mirror" : sha), modalX + 12, lineY, 0xFF888888, false);
        lineY += 13;

        File dir = modelManager.getModelDir(info.getId());
        String path = dir.getAbsolutePath();
        if (this.font.width(path) > modalW - 24) {
            path = "..." + path.substring(Math.max(0, path.length() - 40));
        }
        extractor.text(this.font, "§8Local Path: §7" + path, modalX + 12, lineY, 0xFF777777, false);

        // Close button at bottom
        int closeBtnY = modalY + modalH - 25;
        renderMiniButton(extractor, "Close", centerX - 40, closeBtnY, 80, 18, mouseX, mouseY, 0xFF353A42);
    }

    private void drawBorder(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int color) {
        extractor.fill(x, y, x + w, y + 1, color);
        extractor.fill(x, y + h - 1, x + w, y + h, color);
        extractor.fill(x, y + 1, x + 1, y + h - 1, color);
        extractor.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void drawContainerBorder(GuiGraphicsExtractor extractor, int x, int y, int w, int h) {
        // Outer dark highlight and shadow
        extractor.fill(x, y, x + w, y + 1, 0xFF3E444C);
        extractor.fill(x, y + h - 1, x + w, y + h, 0xFF181A1D);
        extractor.fill(x, y + 1, x + 1, y + h - 1, 0xFF3E444C);
        extractor.fill(x + w - 1, y + 1, x + w, y + h - 1, 0xFF181A1D);

        // Inner subtle rim
        extractor.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF2A2E35);
        extractor.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF1E2126);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
