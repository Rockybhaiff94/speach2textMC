package com.voice2text.transcription;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HinglishTransliterationTest {

    @Test
    public void testPureEnglishUntouched() {
        String input = "Hello world, let's join the server!";
        String output = HinglishTransliterationService.transliterate(input);
        assertEquals("Hello world, let's join the server!", output);
    }

    @Test
    public void testPromptTargetBhaiMujheEkNewGamingPCBananaHai() {
        // Spoken: "Bhai mujhe ek new gaming PC banana hai."
        // With mixed Hindi and English
        String mixed = "भाई मुझे एक new gaming PC बनाना है";
        String output = HinglishTransliterationService.transliterate(mixed);
        assertEquals("bhai mujhe ek new gaming PC banana hai", output);

        // With fully phonetically transcribed Devanagari loanwords
        String devanagari = "भाई मुझे एक न्यू गेमिंग पीसी बनाना है";
        String output2 = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("bhai mujhe ek new gaming PC banana hai", output2);
    }

    @Test
    public void testMinecraftCommandsAndSymbolsUntouched() {
        String input = "/tp @p ~ ~10 ~ and /give player diamond 64";
        String output = HinglishTransliterationService.transliterate(input);
        assertEquals("/tp @p ~ ~10 ~ and /give player diamond 64", output);
    }

    @Test
    public void testPromptTarget1BhaiGameMeinAaJao() {
        // Target: "Bhai game mein aa jao."
        String devanagari = "भाई गेम में आ जाओ";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("bhai game mein aa jao", output);
    }

    @Test
    public void testPromptTarget2ServerParJaldiJoinKaro() {
        // Target: "Server par jaldi join karo."
        String devanagari = "सर्वर पर जल्दी जॉइन करो";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("server par jaldi join karo", output);
    }

    @Test
    public void testPromptTarget3MeraMinecraftOpenNahiHoRaha() {
        // Target: "Mera Minecraft open nahi ho raha."
        String devanagari = "मेरा माइनक्राफ्ट ओपन नहीं हो रहा";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("mera minecraft open nahi ho raha", output);
    }

    @Test
    public void testPromptTarget4GuysAajHumSurvivalKhelenge() {
        // Target: "Guys, aaj hum survival khelenge."
        // With Latin loanword "Guys"
        String mixed = "Guys, आज हम सर्वाइवल खेलेंगे";
        String output = HinglishTransliterationService.transliterate(mixed);
        assertEquals("Guys, aaj hum survival khelenge", output);

        // With Devanagari transcribed "गाइज़"
        String devanagari = "गाइज़, आज हम सर्वाइवल खेलेंगे";
        String output2 = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("guys, aaj hum survival khelenge", output2);
    }

    @Test
    public void testPromptTarget5TumLogVoiceChatMeinAaJao() {
        // Target: "Tum log voice chat mein aa jao."
        String devanagari = "तुम लोग वॉइस चैट में आ जाओ";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("tum log voice chat mein aa jao", output);
    }

    @Test
    public void testPromptTarget6IskoConfigMeinChangeKarnaPadega() {
        // Target: "Isko config mein change karna padega."
        String devanagari = "इसको कॉन्फ़िग में चेंज करना पड़ेगा";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("isko config mein change karna padega", output);
    }

    @Test
    public void testCandraVowelsAndNuktas() {
        // Candra-O matra 0x0949 in block, login, voice
        assertEquals("block", HinglishTransliterationService.transliterate("ब्लॉक"));
        assertEquals("login", HinglishTransliterationService.transliterate("लॉगिन"));
        assertEquals("voice", HinglishTransliterationService.transliterate("वॉइस"));
        assertEquals("offline", HinglishTransliterationService.transliterate("ऑफलाइन"));

        // Decomposed and precomposed nuktas (ज़, फ़)
        assertEquals("zombie", HinglishTransliterationService.transliterate("ज़ॉम्बी"));
        assertEquals("guys", HinglishTransliterationService.transliterate("गाइज़"));
    }

    @Test
    public void testMatraAnusvaraCombinations() {
        // में -> mein, हैं -> hain, लोगों -> logon, कहाँ -> kahan
        assertEquals("mein", HinglishTransliterationService.transliterate("में"));
        assertEquals("hain", HinglishTransliterationService.transliterate("हैं"));
        assertEquals("logon", HinglishTransliterationService.transliterate("लोगों"));
        assertEquals("kahan", HinglishTransliterationService.transliterate("कहाँ"));
    }

    @Test
    public void testHindiMedialAndFinalSchwaDeletion() {
        // Final schwa: "कमल कल घर चल" -> "kamal kal ghar chal"
        assertEquals("kamal kal ghar chal", HinglishTransliterationService.transliterate("कमल कल घर चल"));

        // Medial schwa in VC_CV: "करना" -> "karna" (not "karana")
        assertEquals("karna", HinglishTransliterationService.transliterate("करना"));
        assertEquals("padega", HinglishTransliterationService.transliterate("पड़ेगा"));
        assertEquals("chalna", HinglishTransliterationService.transliterate("चलना"));
    }

    @Test
    public void testMixedPunctuationPreservation() {
        String input = "bhai, क्या हाल hai? /spawn";
        String output = HinglishTransliterationService.transliterate(input);
        assertEquals("bhai, kya haal hai? /spawn", output);
    }

    @Test
    public void testPromptTargetKaiseHo() {
        // Target: "कैसे हो" -> "kaise ho"
        String devanagari = "कैसे हो";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("kaise ho", output);
    }

    @Test
    public void testPromptTargetKyaKarRaheHo() {
        // Target: "क्या कर रहे हो" -> "kya kar rahe ho"
        String devanagari = "क्या कर रहे हो";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("kya kar rahe ho", output);
    }

    @Test
    public void testPromptTargetMeraServerChaluNahiHoRaha() {
        // Target: "मेरा सर्वर चालू नहीं हो रहा" -> "mera server chalu nahi ho raha"
        String devanagari = "मेरा सर्वर चालू नहीं हो रहा";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("mera server chalu nahi ho raha", output);
    }

    @Test
    public void testPromptTargetJaldiVCMeinAaJao() {
        // Target: "जल्दी वीसी में आ जाओ" -> "jaldi VC mein aa jao"
        String devanagari = "जल्दी वीसी में आ जाओ";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("jaldi VC mein aa jao", output);
    }

    @Test
    public void testPromptTargetMainAbhiMinecraftKhelRahaHoon() {
        // Target: "मैं अभी Minecraft खेल रहा हूँ" -> "main abhi Minecraft khel raha hoon"
        String devanagari = "मैं अभी Minecraft खेल रहा हूँ";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("main abhi Minecraft khel raha hoon", output);
    }

    @Test
    public void testMojibakeRecovery() throws Exception {
        // UTF-8 bytes for "कैसे हो" misdecoded through Windows-1252
        byte[] utf8Bytes = "कैसे हो".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String mojibake = new String(utf8Bytes, java.nio.charset.Charset.forName("Windows-1252"));

        // Verify mojibake contains non-Devanagari lead bytes
        assertTrue(HinglishTransliterationService.containsDevanagariMojibake(mojibake));

        // Transliterate should auto-detect and repair mojibake back to Romanized Hinglish
        String output = HinglishTransliterationService.transliterate(mojibake);
        assertEquals("kaise ho", output);
    }

    @Test
    public void testHindiPunctuationDanda() {
        // Hindi Purna Viram danda (।) maps to dot
        String devanagari = "भाई गेम में आ जाओ।";
        String output = HinglishTransliterationService.transliterate(devanagari);
        assertEquals("bhai game mein aa jao.", output);
    }

    @Test
    public void testEmptyNullAndFailedOutput() {
        assertNull(HinglishTransliterationService.transliterate(null));
        assertEquals("", HinglishTransliterationService.transliterate(""));
        assertEquals("   ", HinglishTransliterationService.transliterate("   "));
    }

    @Test
    public void testUserExpectedExamplesInHinglishTransliteration() {
        // 1. English speech: "I am talking to you right now."
        assertEquals("I am talking to you right now.",
                HinglishTransliterationService.transliterate("I am talking to you right now."));
        assertEquals("I am talking to you right now",
                HinglishTransliterationService.transliterate("आई एम टॉकिंग टू यू राइट नाउ"));

        // 2. Hindi speech: "Mera naam Ashish hai."
        assertEquals("Mera naam Ashish hai.",
                HinglishTransliterationService.transliterate("Mera naam Ashish hai."));

        // 3. Hinglish speech: "Mera naam is Ashish and I am working."
        assertEquals("Mera naam is Ashish and I am working.",
                HinglishTransliterationService.transliterate("Mera naam is Ashish and I am working."));
        assertEquals("mera naam is Ashish and I am working",
                HinglishTransliterationService.transliterate("मेरा नाम is आशीष and आई एम वर्किंग"));

        // 4. Code-switched speech: "Today mujhe Minecraft khelna hai."
        assertEquals("Today mujhe Minecraft khelna hai.",
                HinglishTransliterationService.transliterate("Today mujhe Minecraft khelna hai."));
        assertEquals("Today mujhe Minecraft khelna hai",
                HinglishTransliterationService.transliterate("Today मुझे Minecraft खेलना है"));
    }
}
