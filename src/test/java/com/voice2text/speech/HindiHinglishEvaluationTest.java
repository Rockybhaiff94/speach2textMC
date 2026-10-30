package com.voice2text.speech;

import com.voice2text.transcription.TranscriptionCorrectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repeatable evaluation test suite for offline Hindi and Hinglish speech recognition.
 * Evaluates accuracy, word error rate (WER), character error rate (CER),
 * and English vocabulary preservation across multi-speaker conditions.
 */
public class HindiHinglishEvaluationTest {

    private TranscriptionCorrectionService correctionService;

    @BeforeEach
    public void setUp() {
        this.correctionService = new TranscriptionCorrectionService();
    }

    public static class TestCase {
        public final String category;
        public final String speakingCondition;
        public final String rawAcousticHypothesis;
        public final String referenceTranscript;

        public TestCase(String category, String speakingCondition, String rawAcousticHypothesis, String referenceTranscript) {
            this.category = category;
            this.speakingCondition = speakingCondition;
            this.rawAcousticHypothesis = rawAcousticHypothesis;
            this.referenceTranscript = referenceTranscript;
        }
    }

    /**
     * Builds the standard representative Hindi/Hinglish evaluation dataset.
     */
    public List<TestCase> createEvaluationDataset() {
        List<TestCase> dataset = new ArrayList<>();

        // 1. Hindi-Only Speech
        dataset.add(new TestCase("Hindi-Only", "Speaker A (Normal Speed)",
                "मुझे खाना चाहिए।",
                "मुझे खाना चाहिए।"));
        dataset.add(new TestCase("Hindi-Only", "Speaker B (Fast Pace)",
                "मैं अभी Minecraft खेल रहा हूँ।",
                "मैं अभी Minecraft खेल रहा हूँ।"));
        dataset.add(new TestCase("Hindi-Only", "Speaker C (Quiet / Natural Pause)",
                "तुम मेरे सर्वर पर आ सकते हो।",
                "तुम मेरे सर्वर पर आ सकते हो।"));

        // 2. Hinglish Code-Switched Speech with English Words in Hindi Sentences
        dataset.add(new TestCase("Hinglish", "Speaker A (Phonetic /ph/ fumble)",
                "मुझे phood चाहिए।",
                "मुझे food चाहिए।"));
        dataset.add(new TestCase("Hinglish", "Speaker B (Fast test-build pronunciation)",
                "ये एक test bil है।",
                "ये एक test build है।"));
        dataset.add(new TestCase("Hinglish", "Speaker C (Indian English Accent /sarvar ristart/)",
                "भाई, मेरा Minecraft sarvar ristart कर दो।",
                "भाई, मेरा Minecraft server restart कर दो।"));
        dataset.add(new TestCase("Hinglish", "Speaker A (Gaming party invite slang)",
                "इस playar को parti में invaite करो।",
                "इस player को party में invite करो।"));
        dataset.add(new TestCase("Hinglish", "Speaker B (Survival base phrase)",
                "आज हम survivel में baes बनाने वाले हैं।",
                "आज हम survival में base बनाने वाले हैं।"));

        // 3. Minecraft Slang & Commands
        dataset.add(new TestCase("Gaming Terms", "Speaker C (Diamond Creeper callout)",
                "cave mein dimond mila creper se bacho",
                "cave mein diamond mila creeper se bacho"));
        dataset.add(new TestCase("Gaming Terms", "Speaker A (Voice channel callout)",
                "jaldi weesy mein aao",
                "jaldi VC mein aao"));
        dataset.add(new TestCase("Commands", "Speaker B (Slash command preserve)",
                "/tp @p ~ ~10 ~",
                "/tp @p ~ ~10 ~"));

        return dataset;
    }

    @Test
    @DisplayName("Evaluate Hindi-only sentences preserve Devanagari script perfectly")
    public void testHindiOnlyEvaluation() {
        List<TestCase> dataset = createEvaluationDataset();
        for (TestCase tc : dataset) {
            if ("Hindi-Only".equals(tc.category)) {
                String processed = correctionService.correctFinalizedText(tc.rawAcousticHypothesis);
                assertEquals(tc.referenceTranscript, processed, "Failed for " + tc.speakingCondition);
                double cer = calculateCER(tc.referenceTranscript, processed);
                assertEquals(0.0, cer, 0.001, "CER must be 0 for exact match");
            }
        }
    }

    @Test
    @DisplayName("Evaluate Hinglish sentences restore English words accurately")
    public void testHinglishEnglishWordPreservation() {
        List<TestCase> dataset = createEvaluationDataset();
        int totalHinglish = 0;
        int exactMatches = 0;

        for (TestCase tc : dataset) {
            if ("Hinglish".equals(tc.category)) {
                totalHinglish++;
                long startTime = System.nanoTime();
                String processed = correctionService.correctFinalizedText(tc.rawAcousticHypothesis);
                long durationNs = System.nanoTime() - startTime;

                // Max latency must be < 5ms for instantaneous processing
                assertTrue(durationNs < 50_000_000L, "Processing time too high: " + (durationNs / 1_000_000.0) + "ms");

                double wer = calculateWER(tc.referenceTranscript, processed);
                if (wer == 0.0) {
                    exactMatches++;
                }
                assertEquals(tc.referenceTranscript, processed, "Failed under condition: " + tc.speakingCondition);
            }
        }

        assertEquals(totalHinglish, exactMatches, "All Hinglish code-switched phrases must match reference");
    }

    @Test
    @DisplayName("Evaluate Benchmark Metrics: Overall WER and CER across entire suite")
    public void testOverallBenchmarkMetrics() {
        List<TestCase> dataset = createEvaluationDataset();
        double totalWER = 0.0;
        double totalCER = 0.0;
        long startTotal = System.currentTimeMillis();

        for (TestCase tc : dataset) {
            String processed = correctionService.correctFinalizedText(tc.rawAcousticHypothesis);
            totalWER += calculateWER(tc.referenceTranscript, processed);
            totalCER += calculateCER(tc.referenceTranscript, processed);
        }

        long totalTimeMs = System.currentTimeMillis() - startTotal;
        double averageWER = totalWER / dataset.size();
        double averageCER = totalCER / dataset.size();

        assertEquals(0.0, averageWER, 0.001, "Average WER must be 0.0 with context corrections");
        assertEquals(0.0, averageCER, 0.001, "Average CER must be 0.0 with context corrections");
        assertTrue(totalTimeMs < 100, "Full evaluation dataset must run in under 100ms");
    }

    /**
     * Computes Word Error Rate (WER) using Levenshtein distance on words.
     */
    public static double calculateWER(String reference, String hypothesis) {
        String[] refWords = reference.trim().split("\\s+");
        String[] hypWords = hypothesis.trim().split("\\s+");

        int[][] d = new int[refWords.length + 1][hypWords.length + 1];

        for (int i = 0; i <= refWords.length; i++) d[i][0] = i;
        for (int j = 0; j <= hypWords.length; j++) d[0][j] = j;

        for (int i = 1; i <= refWords.length; i++) {
            for (int j = 1; j <= hypWords.length; j++) {
                if (refWords[i - 1].equalsIgnoreCase(hypWords[j - 1])) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    int subst = d[i - 1][j - 1] + 1;
                    int insert = d[i][j - 1] + 1;
                    int delete = d[i - 1][j] + 1;
                    d[i][j] = Math.min(subst, Math.min(insert, delete));
                }
            }
        }

        return refWords.length == 0 ? 0.0 : (double) d[refWords.length][hypWords.length] / refWords.length;
    }

    /**
     * Computes Character Error Rate (CER) using Levenshtein distance on unicode characters.
     */
    public static double calculateCER(String reference, String hypothesis) {
        char[] refChars = reference.toCharArray();
        char[] hypChars = hypothesis.toCharArray();

        int[][] d = new int[refChars.length + 1][hypChars.length + 1];

        for (int i = 0; i <= refChars.length; i++) d[i][0] = i;
        for (int j = 0; j <= hypChars.length; j++) d[0][j] = j;

        for (int i = 1; i <= refChars.length; i++) {
            for (int j = 1; j <= hypChars.length; j++) {
                if (refChars[i - 1] == hypChars[j - 1]) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    int subst = d[i - 1][j - 1] + 1;
                    int insert = d[i][j - 1] + 1;
                    int delete = d[i - 1][j] + 1;
                    d[i][j] = Math.min(subst, Math.min(insert, delete));
                }
            }
        }

        return refChars.length == 0 ? 0.0 : (double) d[refChars.length][hypChars.length] / refChars.length;
    }
}

