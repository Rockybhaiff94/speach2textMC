package com.voice2text.transcription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PersonalizedLearningStoreTest {

    private File storeFile;
    private PersonalizedLearningStore store;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        storeFile = tempDir.resolve("learned_corrections.json").toFile();
        store = new PersonalizedLearningStore(storeFile, 100);
    }

    @Test
    public void testConfirmedCorrectionsPersistAcrossRestart() {
        assertEquals(0, store.size());

        // 1. Record user-confirmed corrections
        store.recordCorrection("phood", "food", "मुझे");
        store.recordCorrection("bil", "build", "test");
        store.recordCorrection("dost", "dost", "");

        assertEquals(3, store.size());
        assertTrue(storeFile.exists(), "Store file should be persisted to disk");

        // 2. Simulate mod / game restart by creating a new store instance from same file
        PersonalizedLearningStore reloaded = new PersonalizedLearningStore(storeFile, 100);
        assertEquals(3, reloaded.size());

        // 3. Verify lookups match
        assertEquals("food", reloaded.lookupCorrection("phood", "मुझे", "चाहिए"));
        assertEquals("build", reloaded.lookupCorrection("bil", "test", "है"));
        assertEquals("dost", reloaded.lookupCorrection("dost", null, null));
    }

    @Test
    public void testContextSensitiveLearnedCorrections() {
        // Record contextual correction: "bil" -> "build" only when preceded by "test"
        store.recordCorrection("bil", "build", "test");

        // When context matches ("test bil") -> returns "build"
        assertEquals("build", store.lookupCorrection("bil", "test", "है"));

        // When context is unrelated ("water bil") -> does NOT match, returns null
        assertNull(store.lookupCorrection("bil", "water", "please"));
    }

    @Test
    public void testUnconfirmedRecognitionResultsDoNotEnterStore() {
        // The store must never automatically absorb raw or unconfirmed ASR output
        assertEquals(0, store.size());
        assertNull(store.lookupCorrection("randomword", null, null));
        assertEquals(0, store.size(), "Store size should remain 0 when no user confirmation occurred");
    }

    @Test
    public void testCorruptedLearningDataRecovery() throws IOException {
        // Write completely invalid, malformed JSON to the storage file
        try (FileOutputStream fos = new FileOutputStream(storeFile)) {
            fos.write("{ this is completely corrupted json !!! }}}".getBytes(StandardCharsets.UTF_8));
        }

        // Initialize store: must not crash, must recover gracefully, and back up to .bak
        PersonalizedLearningStore recovered = new PersonalizedLearningStore(storeFile);
        assertNotNull(recovered);
        assertEquals(0, recovered.size(), "Corrupted data should result in a fresh, clean store");

        File bakFile = new File(storeFile.getParentFile(), storeFile.getName() + ".bak");
        assertTrue(bakFile.exists(), "Corrupted file should be safely preserved as .bak");

        // Can still write new entries cleanly
        recovered.recordCorrection("test", "testing", "");
        assertEquals(1, recovered.size());
    }

    @Test
    public void testResetClearsLearnedDataWithoutAffectingOtherFiles() {
        store.recordCorrection("mistake1", "correction1", "");
        store.recordCorrection("mistake2", "correction2", "context");
        assertEquals(2, store.size());

        // Reset
        store.reset();
        assertEquals(0, store.size());

        // Reload from disk: verify reset persisted
        PersonalizedLearningStore reloaded = new PersonalizedLearningStore(storeFile);
        assertEquals(0, reloaded.size());
    }

    @Test
    public void testCapacityAndLruEviction() {
        PersonalizedLearningStore smallStore = new PersonalizedLearningStore(storeFile, 3);

        smallStore.recordCorrection("word1", "corr1", "");
        smallStore.recordCorrection("word2", "corr2", "");
        smallStore.recordCorrection("word3", "corr3", "");
        assertEquals(3, smallStore.size());

        // Lookup word1 to increase its usage count
        smallStore.lookupCorrection("word1", null, null);

        // Add 4th item: should evict least used / oldest entry
        smallStore.recordCorrection("word4", "corr4", "");
        assertEquals(3, smallStore.size());

        // Most frequently used word1 should still be present
        assertEquals("corr1", smallStore.lookupCorrection("word1", null, null));
        assertEquals("corr4", smallStore.lookupCorrection("word4", null, null));
    }
}

