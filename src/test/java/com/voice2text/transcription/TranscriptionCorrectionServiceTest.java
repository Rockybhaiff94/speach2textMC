package com.voice2text.transcription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TranscriptionCorrectionServiceTest {

    private TranscriptionCorrectionService correctionService;
    private PersonalizedLearningStore learningStore;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        File storeFile = tempDir.resolve("learned_corrections.json").toFile();
        learningStore = new PersonalizedLearningStore(storeFile);
        correctionService = new TranscriptionCorrectionService(learningStore);
    }

    @Test
    public void testPhoodToFoodInHindiContext() {
        // Target: "मुझे phood चाहिए" -> "मुझे food चाहिए"
        String input = "मुझे phood चाहिए";
        String corrected = correctionService.correctFinalizedText(input);
        assertEquals("मुझे food चाहिए", corrected);

        // Romanized Hindi context
        String romanizedInput = "mujhe phood chahiye";
        String romanizedCorrected = correctionService.correctFinalizedText(romanizedInput);
        assertEquals("mujhe food chahiye", romanizedCorrected);
    }

    @Test
    public void testTestBilToTestBuildInContext() {
        // Target: "ये एक test bil है" -> "ये एक test build है"
        String input = "ये एक test bil है";
        String corrected = correctionService.correctFinalizedText(input);
        assertEquals("ये एक test build है", corrected);

        // English context: "this is a test bil"
        String engInput = "this is a test bil";
        String engCorrected = correctionService.correctFinalizedText(engInput);
        assertEquals("this is a test build", engCorrected);

        // "new bil ready"
        assertEquals("new build ready", correctionService.correctFinalizedText("new bil ready"));
    }

    @Test
    public void testValidWordsUntouched() {
        // "bill" is a valid English word and should NOT be replaced when not matching build context
        String billInput = "pay the electricity bill please";
        assertEquals("pay the electricity bill please", correctionService.correctFinalizedText(billInput));

        // Valid Hindi words remain untouched
        String hindiInput = "नमस्ते भाई कैसे हो आप";
        assertEquals("नमस्ते भाई कैसे हो आप", correctionService.correctFinalizedText(hindiInput));

        // Standard English sentence
        String englishInput = "hello everyone let us explore the new cave";
        assertEquals("hello everyone let us explore the new cave", correctionService.correctFinalizedText(englishInput));
    }

    @Test
    public void testUnknownWordsWithoutConfidenceUntouched() {
        // Unknown word or fantasy name without confidence match should remain intact
        String unknown = "look at the zxqyvr over there";
        assertEquals("look at the zxqyvr over there", correctionService.correctFinalizedText(unknown));

        // Unknown player username
        String username = "welcome Technoblade and xXGamerXx to the server";
        assertEquals("welcome Technoblade and xXGamerXx to the server", correctionService.correctFinalizedText(username));
    }

    @Test
    public void testHindiScriptAndEnglishPreserved() {
        // Naturally mixed Hindi and English
        String mixed = "आज हम diamond armor बनाएंगे";
        assertEquals("आज हम diamond armor बनाएंगे", correctionService.correctFinalizedText(mixed));

        String mixed2 = "ये server बहुत lag कर रहा है";
        assertEquals("ये server बहुत lag कर रहा है", correctionService.correctFinalizedText(mixed2));
    }

    @Test
    public void testMinecraftCommandsAndCoordinatesUntouched() {
        // Minecraft commands must never be modified by correction layer
        String tpCommand = "/tp @p ~ ~10 ~";
        assertEquals("/tp @p ~ ~10 ~", correctionService.correctFinalizedText(tpCommand));

        String giveCommand = "/give @s diamond_sword 1";
        assertEquals("/give @s diamond_sword 1", correctionService.correctFinalizedText(giveCommand));

        String gamemodeCommand = "/gamemode creative";
        assertEquals("/gamemode creative", correctionService.correctFinalizedText(gamemodeCommand));
    }

    @Test
    public void testMinecraftGamingVocabularyCorrections() {
        // Common gaming phonetic fumbles
        assertEquals("found some diamond in cave", correctionService.correctFinalizedText("found some dimond in cave"));
        assertEquals("watch out creeper behind you", correctionService.correctFinalizedText("watch out creper behind you"));
        assertEquals("craft a diamond pickaxe", correctionService.correctFinalizedText("craft a diamond pickex"));
        assertEquals("back to spawn", correctionService.correctFinalizedText("back to sapwn"));
        assertEquals("jaldi VC mein aao", correctionService.correctFinalizedText("jaldi weesy mein aao"));
    }

    @Test
    public void testCasingPreservation() {
        // Title case and upper case matching
        assertEquals("Food", correctionService.correctFinalizedText("Phood"));
        assertEquals("FOOD", correctionService.correctFinalizedText("PHOOD"));
        assertEquals("Test Build", correctionService.correctFinalizedText("Test Bil"));
    }
}

