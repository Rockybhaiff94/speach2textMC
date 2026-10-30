package com.voice2text.speech;

import com.voice2text.model.ModelFamily;
import com.voice2text.model.SpeechModelInfo;
import com.voice2text.speech.indic.IndicConformerSpeechEngine;
import com.voice2text.speech.shruti.ShrutiSpeechEngine;
import com.voice2text.speech.vosk.VoskSpeechEngine;
import com.voice2text.speech.whisper.WhisperSpeechEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SpeechEngineManagerTest {

    private SpeechEngineManager engineManager;

    @BeforeEach
    public void setUp() {
        engineManager = new SpeechEngineManager();
    }

    @AfterEach
    public void tearDown() {
        engineManager.closeAll();
    }

    @Test
    public void testEngineProvisioningByFamily() {
        // Vosk
        SpeechRecognitionEngine voskEngine = engineManager.instantiateEngine(ModelFamily.VOSK);
        assertTrue(voskEngine instanceof VoskSpeechEngine);

        // Whisper
        SpeechRecognitionEngine whisperEngine = engineManager.instantiateEngine(ModelFamily.WHISPER);
        assertTrue(whisperEngine instanceof WhisperSpeechEngine);

        // Hinglish Specialized
        SpeechRecognitionEngine hinglishEngine = engineManager.instantiateEngine(ModelFamily.HINGLISH_SPECIALIZED);
        assertTrue(hinglishEngine instanceof WhisperSpeechEngine);

        // IndicConformer
        SpeechRecognitionEngine indicEngine = engineManager.instantiateEngine(ModelFamily.INDIC_CONFORMER);
        assertTrue(indicEngine instanceof IndicConformerSpeechEngine);

        // Shruti
        SpeechRecognitionEngine shrutiEngine = engineManager.instantiateEngine(ModelFamily.SHRUTI);
        assertTrue(shrutiEngine instanceof ShrutiSpeechEngine);
    }

    @Test
    public void testWhisperEngineStreamingLifecycle(@TempDir Path tempDir) throws IOException {
        WhisperSpeechEngine engine = new WhisperSpeechEngine();
        assertEquals(EngineState.UNINITIALIZED, engine.getState());

        // Create mock weights in tempDir
        File modelBin = tempDir.resolve("model.bin").toFile();
        try (FileOutputStream fos = new FileOutputStream(modelBin)) {
            fos.write(new byte[1024]);
        }

        engine.initialize(tempDir.toFile().getAbsolutePath());
        assertEquals(EngineState.READY, engine.getState());
        assertEquals(modelBin.getAbsolutePath(), engine.getModelFile().getAbsolutePath());

        // Listen for callbacks
        final boolean[] receivedFinal = {false};
        engine.setListener(new RecognitionListener() {
            @Override
            public void onPartialResult(RecognitionResult result) {
            }

            @Override
            public void onFinalResult(RecognitionResult result) {
                receivedFinal[0] = true;
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
            }

            @Override
            public void onStateChanged(EngineState oldState, EngineState newState) {
            }
        });

        engine.start();
        assertEquals(EngineState.LISTENING, engine.getState());
        assertTrue(engine.isRunning());

        // Stream 1 second of 16kHz audio (32000 bytes)
        byte[] audio = new byte[32000];
        engine.acceptAudio(audio, audio.length);

        engine.stop();
        assertEquals(EngineState.READY, engine.getState());
        assertFalse(engine.isRunning());

        engine.close();
        assertEquals(EngineState.CLOSED, engine.getState());
    }

    @Test
    public void testVerificationCompatibility(@TempDir Path tempDir) {
        SpeechModelInfo info = new SpeechModelInfo(
                "whisper-small",
                "multilingual",
                "Whisper Small",
                ModelFamily.WHISPER,
                "OpenAI Whisper",
                "Description",
                "99+ Languages",
                "High",
                "Roman Hindi",
                "2 GB RAM",
                "Whisper Local",
                "Q5_1",
                "Supported",
                "https://example.com/model.bin",
                "",
                480000000L,
                480000000L,
                "",
                "",
                "",
                "MIT"
        );

        File dir = tempDir.toFile();
        assertTrue(engineManager.verifyActivationCompatibility(info, dir));
        assertFalse(engineManager.verifyActivationCompatibility(null, dir));
        assertFalse(engineManager.verifyActivationCompatibility(info, new File(dir, "nonexistent")));
    }
}

