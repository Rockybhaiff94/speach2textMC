package com.voice2text.transcription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TranscriptionBufferTest {

    private TranscriptionBuffer buffer;

    @BeforeEach
    public void setUp() {
        buffer = new TranscriptionBuffer();
        buffer.setAutoCapitalization(true);
        buffer.setVoiceCommandsEnabled(true);
    }

    @Test
    public void testCleanStreamingAndCommit() {
        buffer.startSession("", 0);

        // First partial
        buffer.updatePartial("hello");
        assertEquals("Hello", buffer.getDisplayText());

        // Revised partial
        buffer.updatePartial("hello everyone");
        assertEquals("Hello everyone", buffer.getDisplayText());

        // Final segment arrives
        buffer.commitFinal("hello everyone");
        assertEquals("Hello everyone", buffer.getDisplayText());

        // Next utterance partial
        buffer.updatePartial("welcome to my world");
        assertEquals("Hello everyone welcome to my world", buffer.getDisplayText());

        // Next utterance final
        buffer.commitFinal("welcome to my world");
        assertEquals("Hello everyone welcome to my world", buffer.getDisplayText());

        String finalized = buffer.finalizeSession();
        assertEquals("Hello everyone welcome to my world", finalized);
    }

    @Test
    public void testPreventsDuplicateWordsOnPartialFinalOverlap() {
        buffer.startSession("", 0);

        // Commit first segment
        buffer.commitFinal("hello world");
        assertEquals("Hello world", buffer.getCommittedSpeech());

        // ASR sometimes repeats "world" in the next partial: "world how are you"
        buffer.updatePartial("world how are you");
        // Overlapping "world" must be stripped so we don't get "Hello world world how are you"
        assertEquals("Hello world how are you", buffer.getDisplayText());

        buffer.commitFinal("world how are you");
        assertEquals("Hello world how are you", buffer.getFullCommittedText());
    }

    @Test
    public void testRemoveOverlapStaticHelper() {
        assertEquals("how are you", TranscriptionBuffer.removeOverlap("hello world", "world how are you"));
        assertEquals("how are you", TranscriptionBuffer.removeOverlap("hello world", "hello world how are you"));
        assertEquals("totally new", TranscriptionBuffer.removeOverlap("hello world", "totally new"));
        assertEquals("", TranscriptionBuffer.removeOverlap("hello world", "hello world"));
    }

    @Test
    public void testPreservesExistingDraftAndCursorPosition() {
        // Player had typed "Greetings " and had cursor at the end
        buffer.startSession("Greetings ", 10);

        buffer.updatePartial("traveler");
        assertEquals("Greetings traveler", buffer.getDisplayText());

        buffer.commitFinal("traveler");
        assertEquals("Greetings traveler", buffer.getDisplayText());

        String result = buffer.finalizeSession();
        assertEquals("Greetings traveler", result);
    }

    @Test
    public void testPreservesPrefixAndSuffixDrafts() {
        // Player typed "[World] !" with cursor between "]" and "!"
        buffer.startSession("[World] !", 7);

        buffer.commitFinal("hello");
        assertEquals("[World] hello !", buffer.getDisplayText());
    }

    @Test
    public void testVoicePunctuationCommands() {
        buffer.startSession("", 0);

        // Player says: "Hello period how are you question mark"
        buffer.commitFinal("hello period how are you question mark");

        String result = buffer.finalizeSession();
        assertEquals("Hello. How are you?", result);
    }

    @Test
    public void testReconcileManualEditDuringDictation() {
        buffer.startSession("", 0);
        buffer.commitFinal("hello");

        // Player manually edits text in chat box to "hello everyone"
        buffer.reconcileManualEdit("hello everyone");

        // Next partial arrives
        buffer.updatePartial("and welcome");
        assertEquals("hello everyone and welcome", buffer.getDisplayText());
    }

    @Test
    public void testChatMessageSplitterHandles256Limit() {
        StringBuilder longMsg = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longMsg.append("word").append(i).append(" ");
        }
        String text = longMsg.toString().trim();
        assertTrue(text.length() > 256);

        List<String> chunks = ChatMessageSplitter.splitMessage(text, 256);
        assertTrue(chunks.size() > 1);

        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 256);
            assertFalse(chunk.isEmpty());
        }

        // Verify no content was lost
        String reassembled = String.join(" ", chunks);
        assertEquals(text, reassembled);
    }
}
