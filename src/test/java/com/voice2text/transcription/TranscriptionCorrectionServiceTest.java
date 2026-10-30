package com.voice2text.transcription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TranscriptionCorrectionServiceTest {

    private TranscriptionCorrectionService correctionService;

    @BeforeEach
    public void setUp() {
        correctionService = new TranscriptionCorrectionService();
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

        // Key prompt phrases:
        // "भाई, मेरा Minecraft server restart कर दो।"
        assertEquals("भाई, मेरा Minecraft server restart कर दो।",
                correctionService.correctFinalizedText("भाई, मेरा Minecraft sarvar ristart कर दो।"));
        assertEquals("भाई मेरा Minecraft server restart कर दो",
                correctionService.correctFinalizedText("भाई मेरा Minecraft sarvar ristart कर दो"));

        // "इस player को party में invite करो।"
        assertEquals("इस player को party में invite करो।",
                correctionService.correctFinalizedText("इस playar को parti में invaite करो।"));

        // "आज हम survival में base बनाने वाले हैं।"
        assertEquals("आज हम survival में base बनाने वाले हैं।",
                correctionService.correctFinalizedText("आज हम survivel में baes बनाने वाले हैं।"));

        // Pure Hindi sentences preserved
        assertEquals("मुझे खाना चाहिए।", correctionService.correctFinalizedText("मुझे खाना चाहिए।"));
        assertEquals("मैं अभी Minecraft खेल रहा हूँ।", correctionService.correctFinalizedText("मैं अभी Minecraft खेल रहा हूँ।"));
        assertEquals("तुम मेरे सर्वर पर आ सकते हो।", correctionService.correctFinalizedText("तुम मेरे सर्वर पर आ सकते हो।"));
    }

    @Test
    public void testCasingPreservation() {
        // Title case and upper case matching
        assertEquals("Food", correctionService.correctFinalizedText("Phood"));
        assertEquals("FOOD", correctionService.correctFinalizedText("PHOOD"));
        assertEquals("Test Build", correctionService.correctFinalizedText("Test Bil"));
    }

    @Test
    public void testUserExpectedExamplesInCorrectionService() {
        // 1. "I am talking to you right now." - Both direct and phonetic repairs
        assertEquals("I am talking to you right now.",
                correctionService.correctFinalizedText("I am talking to you right now."));
        assertEquals("I am talking to you right now.",
                correctionService.correctFinalizedText("Aai em talking tu yu right nau."));
        assertEquals("I am talking to you right now",
                correctionService.correctFinalizedText("aai em talking tu yu right nau"));

        // 2. "Mera naam Ashish hai."
        assertEquals("Mera naam Ashish hai.",
                correctionService.correctFinalizedText("Mera naam Ashish hai."));
        assertEquals("Mera naam Ashish hai",
                correctionService.correctFinalizedText("mera naam ashish hai"));

        // 3. "Mera naam is Ashish and I am working."
        assertEquals("Mera naam is Ashish and I am working.",
                correctionService.correctFinalizedText("Mera naam is Ashish and I am working."));
        assertEquals("Mera naam is Ashish and I am working",
                correctionService.correctFinalizedText("mera naam is ashish and i am working"));
        assertEquals("Mera naam is Ashish and I am working",
                correctionService.correctFinalizedText("mera naam iz ashish and aai em varkimng"));

        // 4. "Today mujhe Minecraft khelna hai."
        assertEquals("Today mujhe Minecraft khelna hai.",
                correctionService.correctFinalizedText("Today mujhe Minecraft khelna hai."));
        assertEquals("Today mujhe Minecraft khelna hai",
                correctionService.correctFinalizedText("today mujhe minecraft khelna hai"));
    }
}

