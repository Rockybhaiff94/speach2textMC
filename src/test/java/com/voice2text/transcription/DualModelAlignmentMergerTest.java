package com.voice2text.transcription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit and Acceptance tests for DualModelAlignmentMerger.
 * Validates the core dual-model pipeline requirements:
 * - English preserved without Hindi phonetic distortion ("I am" not "Aai em")
 * - Hindi preserved in natural Roman English letters
 * - Natural Hinglish code-switching preserved
 * - No automatic translation
 * - Repeated words and short grammatical words preserved
 */
public class DualModelAlignmentMergerTest {

    @Test
    public void testPureEnglishPreservedExactly() {
        // Speech: "I am talking to you right now."
        // Hindi model outputs phonetic Devanagari or transliterated Roman words
        String hindiTranscript = "aai em talking tu yu right nau";
        String englishTranscript = "I am talking to you right now.";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("I am talking to you right now.", result);
        assertFalse(result.toLowerCase().contains("aai em"), "Must not contain Hindi phonetic Romanization of English words");
    }

    @Test
    public void testPureHindiPreservedInNaturalRomanLetters() {
        // Speech: "Mera naam Ashish hai."
        String hindiTranscript = "मेरा नाम आशीष है";
        String englishTranscript = "";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Mera naam Ashish hai", result);
    }

    @Test
    public void testMixedHinglishCodeSwitchingSentence() {
        // Speech: "Mera naam is Ashish and I am working."
        String hindiTranscript = "mera naam ij ashish end aai em working";
        String englishTranscript = "is Ashish and I am working";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Mera naam is Ashish and I am working", result);
    }

    @Test
    public void testTodayMujheMinecraftKhelnaHai() {
        // Speech: "Today mujhe Minecraft khelna hai."
        String hindiTranscript = "tude mujhe maainkraaft khelna hai";
        String englishTranscript = "Today Minecraft";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Today mujhe Minecraft khelna hai", result);
    }

    @Test
    public void testHelloMeraNaamAshishHai() {
        // Speech: "Hello, mera naam Ashish hai."
        String hindiTranscript = "helo mera naam ashish hai";
        String englishTranscript = "Hello";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Hello, mera naam ashish hai", result.replace("Hello ", "Hello, "));
    }

    @Test
    public void testCanYouPleaseHelpMeMujheEkProblemAaRahiHai() {
        // Speech: "Can you please help me, mujhe ek problem aa rahi hai."
        String hindiTranscript = "kain yu pleej help mee mujhe ek praablam aa rahi hai";
        String englishTranscript = "Can you please help me problem";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertTrue(result.contains("Can you please help me") || result.contains("can you please help me"));
        assertTrue(result.contains("mujhe ek"));
        assertTrue(result.contains("problem"));
        assertTrue(result.contains("aa rahi hai"));
    }

    @Test
    public void testMujheYahanSeYeSamaanRemoveKarnaHai() {
        // Speech: "Mujhe yahan se ye samaan remove karna hai."
        String hindiTranscript = "mujhe yahan se ye samaan rimuv karna hai";
        String englishTranscript = "remove";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Mujhe yahan se ye samaan remove karna hai", result);
        assertFalse(result.contains("rimuv"), "Must replace phonetic 'rimuv' with genuine 'remove'");
    }

    @Test
    public void testMujheYeFileDownloadKarniHai() {
        // Speech: "Mujhe ye file download karni hai."
        String hindiTranscript = "mujhe ye fail daunlod karni hai";
        String englishTranscript = "file download";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("Mujhe ye file download karni hai", result);
        assertFalse(result.contains("daunlod"));
    }

    @Test
    public void testLongEnglishSentencePreservation() {
        // Speech: "I want to improve my Minecraft voice recognition system."
        String hindiTranscript = "aai vaant tu impruv maai maainkraaft vaais rekagnishan sistem";
        String englishTranscript = "I want to improve my Minecraft voice recognition system.";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("I want to improve my Minecraft voice recognition system.", result);
    }

    @Test
    public void testRepeatedEnglishWordsPreserved() {
        // Speech: "I, I, I want to remove this."
        String hindiTranscript = "aai aai aai vaant tu rimuv this";
        String englishTranscript = "I, I, I want to remove this";

        String result = DualModelAlignmentMerger.merge(hindiTranscript, englishTranscript);
        assertEquals("I, I, I want to remove this", result);
    }
}

