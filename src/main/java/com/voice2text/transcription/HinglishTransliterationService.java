package com.voice2text.transcription;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, offline transliteration engine converting spoken Hindi
 * and Indian English code-mixed speech (Devanagari script) into natural Roman Hindi (Hinglish).
 *
 * Requirements:
 * - Preserves English words, Minecraft terminology, commands, and numbers untouched.
 * - Converts Hindi words into standard colloquial Roman letters (e.g. bhai, yaar, karo, mein, hain, logon).
 * - Restores common English gaming and technical loanwords that speech models transcribe phonetically into Devanagari.
 * - Supports candra vowels (ॉ, ॅ), decomposed and precomposed nuktas (ज़, फ़), and anusvara nasal combinations.
 * - Implements Hindi schwa deletion for both word-final consonants and medial syllables.
 */
public class HinglishTransliterationService {

    private static final Pattern DEVANAGARI_BLOCK = Pattern.compile("[\\u0900-\\u097F]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("([\\u0900-\\u097F]+|[A-Za-z0-9_./\\-]+|[^\\s\\w])");

    // Dictionary of common English loanwords transcribed phonetically in Devanagari
    private static final Map<String, String> LOANWORDS_MAP = new HashMap<>();

    // Dictionary of common colloquial Hindi words with standard Hinglish spellings
    private static final Map<String, String> COMMON_HINDI_MAP = new HashMap<>();

    static {
        // --- 1. Gaming, Minecraft & Technical Loanwords ---
        LOANWORDS_MAP.put("माइनक्राफ्ट", "minecraft");
        LOANWORDS_MAP.put("माइनक्राफ़्ट", "minecraft");
        LOANWORDS_MAP.put("माइनक्राफ़्ट", "minecraft");
        LOANWORDS_MAP.put("सर्वर", "server");
        LOANWORDS_MAP.put("स्टार्ट", "start");
        LOANWORDS_MAP.put("इन्वेंटरी", "inventory");
        LOANWORDS_MAP.put("इन्वेन्टरी", "inventory");
        LOANWORDS_MAP.put("ओपन", "open");
        LOANWORDS_MAP.put("ओपेन", "open");
        LOANWORDS_MAP.put("फ्रेंड्स", "friends");
        LOANWORDS_MAP.put("फ्रेंड", "friend");
        LOANWORDS_MAP.put("वेट", "wait");
        LOANWORDS_MAP.put("जॉइन", "join");
        LOANWORDS_MAP.put("ज्वाइन", "join");
        LOANWORDS_MAP.put("गेम", "game");
        LOANWORDS_MAP.put("गेम्स", "games");
        LOANWORDS_MAP.put("गाइज़", "guys");
        LOANWORDS_MAP.put("गाइज", "guys");
        LOANWORDS_MAP.put("गायज़", "guys");
        LOANWORDS_MAP.put("गाइस", "guys");
        LOANWORDS_MAP.put("वॉइस", "voice");
        LOANWORDS_MAP.put("वाइस", "voice");
        LOANWORDS_MAP.put("वायस", "voice");
        LOANWORDS_MAP.put("वॉयस", "voice");
        LOANWORDS_MAP.put("कॉन्फ़िग", "config");
        LOANWORDS_MAP.put("कॉन्फिग", "config");
        LOANWORDS_MAP.put("कॉनफ़िग", "config");
        LOANWORDS_MAP.put("कन्फ़िग", "config");
        LOANWORDS_MAP.put("कन्फिग", "config");
        LOANWORDS_MAP.put("चेंज", "change");
        LOANWORDS_MAP.put("चेन्ज", "change");
        LOANWORDS_MAP.put("प्लेयर", "player");
        LOANWORDS_MAP.put("प्लेयर्स", "players");
        LOANWORDS_MAP.put("वर्ल्ड", "world");
        LOANWORDS_MAP.put("सर्वाइवल", "survival");
        LOANWORDS_MAP.put("सरवाइवल", "survival");
        LOANWORDS_MAP.put("क्रिएटिव", "creative");
        LOANWORDS_MAP.put("ब्लॉक", "block");
        LOANWORDS_MAP.put("ब्लॉक्स", "blocks");
        LOANWORDS_MAP.put("डायमंड", "diamond");
        LOANWORDS_MAP.put("आयरन", "iron");
        LOANWORDS_MAP.put("गोल्ड", "gold");
        LOANWORDS_MAP.put("स्वॉर्ड", "sword");
        LOANWORDS_MAP.put("पिकैक्स", "pickaxe");
        LOANWORDS_MAP.put("पिकएक्स", "pickaxe");
        LOANWORDS_MAP.put("किल", "kill");
        LOANWORDS_MAP.put("क्लिक", "click");
        LOANWORDS_MAP.put("लॉगिन", "login");
        LOANWORDS_MAP.put("लॉगआउट", "logout");
        LOANWORDS_MAP.put("पासवर्ड", "password");
        LOANWORDS_MAP.put("कमांड", "command");
        LOANWORDS_MAP.put("टेलीपोर्ट", "teleport");
        LOANWORDS_MAP.put("चेस्ट", "chest");
        LOANWORDS_MAP.put("ज़ॉम्बी", "zombie");
        LOANWORDS_MAP.put("ज़ोंबी", "zombie");
        LOANWORDS_MAP.put("जोम्बी", "zombie");
        LOANWORDS_MAP.put("क्रीपर", "creeper");
        LOANWORDS_MAP.put("विलेजर", "villager");
        LOANWORDS_MAP.put("हाउस", "house");
        LOANWORDS_MAP.put("होम", "home");
        LOANWORDS_MAP.put("स्पॉन", "spawn");
        LOANWORDS_MAP.put("पोर्टल", "portal");
        LOANWORDS_MAP.put("नेदर", "nether");
        LOANWORDS_MAP.put("एंड", "end");
        LOANWORDS_MAP.put("एंडर", "ender");
        LOANWORDS_MAP.put("ड्रैगन", "dragon");
        LOANWORDS_MAP.put("आर्मर", "armor");
        LOANWORDS_MAP.put("बटन", "button");
        LOANWORDS_MAP.put("लीवर", "lever");
        LOANWORDS_MAP.put("रेडस्टोन", "redstone");
        LOANWORDS_MAP.put("टॉर्च", "torch");
        LOANWORDS_MAP.put("बिल्ड", "build");
        LOANWORDS_MAP.put("माइन", "mine");
        LOANWORDS_MAP.put("क्राफ़्ट", "craft");
        LOANWORDS_MAP.put("क्राफ्ट", "craft");
        LOANWORDS_MAP.put("ऑनलाइन", "online");
        LOANWORDS_MAP.put("ऑफलाइन", "offline");
        LOANWORDS_MAP.put("मैसेज", "message");
        LOANWORDS_MAP.put("चैट", "chat");
        LOANWORDS_MAP.put("हेल्प", "help");
        LOANWORDS_MAP.put("सॉरी", "sorry");
        LOANWORDS_MAP.put("थैंक्स", "thanks");
        LOANWORDS_MAP.put("प्लीज", "please");
        LOANWORDS_MAP.put("यस", "yes");
        LOANWORDS_MAP.put("नो", "no");
        LOANWORDS_MAP.put("ओके", "okay");
        LOANWORDS_MAP.put("बाय", "bye");
        LOANWORDS_MAP.put("हाय", "hi");
        LOANWORDS_MAP.put("हेलो", "hello");
        LOANWORDS_MAP.put("टाइम", "time");
        LOANWORDS_MAP.put("वीसी", "VC");
        LOANWORDS_MAP.put("विसी", "VC");
        LOANWORDS_MAP.put("वी-सी", "VC");
        LOANWORDS_MAP.put("vc", "VC");
        LOANWORDS_MAP.put("डिसकॉर्ड", "discord");
        LOANWORDS_MAP.put("डिस्कॉर्ड", "discord");
        LOANWORDS_MAP.put("यूट्यूब", "youtube");
        LOANWORDS_MAP.put("व्हाट्सएप", "whatsapp");
        LOANWORDS_MAP.put("व्हाट्सऐप", "whatsapp");
        LOANWORDS_MAP.put("पीवीपी", "pvp");
        LOANWORDS_MAP.put("लैग", "lag");
        LOANWORDS_MAP.put("पिंग", "ping");
        LOANWORDS_MAP.put("मोड", "mod");
        LOANWORDS_MAP.put("मॉड", "mod");
        LOANWORDS_MAP.put("मोड्स", "mods");
        LOANWORDS_MAP.put("मॉड्स", "mods");
        LOANWORDS_MAP.put("माइक", "mic");
        LOANWORDS_MAP.put("हैक", "hack");
        LOANWORDS_MAP.put("हैकर", "hacker");
        LOANWORDS_MAP.put("हैकर्स", "hackers");
        LOANWORDS_MAP.put("टीम", "team");
        LOANWORDS_MAP.put("प्रो", "pro");
        LOANWORDS_MAP.put("नूब", "noob");
        LOANWORDS_MAP.put("नोब", "noob");
        LOANWORDS_MAP.put("किलर", "killer");
        LOANWORDS_MAP.put("बॉस", "boss");
        LOANWORDS_MAP.put("स्क्रीन", "screen");
        LOANWORDS_MAP.put("वीडियो", "video");
        LOANWORDS_MAP.put("लिंक", "link");
        LOANWORDS_MAP.put("चैनल", "channel");
        LOANWORDS_MAP.put("डाउनलोड", "download");
        LOANWORDS_MAP.put("अपडेट", "update");
        LOANWORDS_MAP.put("लेवल", "level");

        // --- 2. Common Conversational Hindi Vocabulary ---
        COMMON_HINDI_MAP.put("भाई", "bhai");
        COMMON_HINDI_MAP.put("यार", "yaar");
        COMMON_HINDI_MAP.put("में", "mein");
        COMMON_HINDI_MAP.put("मे", "mein");
        COMMON_HINDI_MAP.put("हैं", "hain");
        COMMON_HINDI_MAP.put("है", "hai");
        COMMON_HINDI_MAP.put("हो", "ho");
        COMMON_HINDI_MAP.put("हूँ", "hoon");
        COMMON_HINDI_MAP.put("हूं", "hoon");
        COMMON_HINDI_MAP.put("था", "tha");
        COMMON_HINDI_MAP.put("थी", "thi");
        COMMON_HINDI_MAP.put("थे", "the");
        COMMON_HINDI_MAP.put("पर", "par");
        COMMON_HINDI_MAP.put("पे", "pe");
        COMMON_HINDI_MAP.put("लोग", "log");
        COMMON_HINDI_MAP.put("लोगो", "logo");
        COMMON_HINDI_MAP.put("लोगों", "logon");
        COMMON_HINDI_MAP.put("इसको", "isko");
        COMMON_HINDI_MAP.put("उसको", "usko");
        COMMON_HINDI_MAP.put("इस", "is");
        COMMON_HINDI_MAP.put("उस", "us");
        COMMON_HINDI_MAP.put("इसे", "ise");
        COMMON_HINDI_MAP.put("उसे", "use");
        COMMON_HINDI_MAP.put("इनको", "inko");
        COMMON_HINDI_MAP.put("उनको", "unko");
        COMMON_HINDI_MAP.put("करना", "karna");
        COMMON_HINDI_MAP.put("करने", "karne");
        COMMON_HINDI_MAP.put("करके", "karke");
        COMMON_HINDI_MAP.put("करो", "karo");
        COMMON_HINDI_MAP.put("कर", "kar");
        COMMON_HINDI_MAP.put("करेंगे", "karenge");
        COMMON_HINDI_MAP.put("करेगा", "karega");
        COMMON_HINDI_MAP.put("करेगी", "karegi");
        COMMON_HINDI_MAP.put("करूंगा", "karunga");
        COMMON_HINDI_MAP.put("करूँगा", "karunga");
        COMMON_HINDI_MAP.put("पड़ेगा", "padega");
        COMMON_HINDI_MAP.put("पडेगा", "padega");
        COMMON_HINDI_MAP.put("पड़ेगी", "padegi");
        COMMON_HINDI_MAP.put("पडेगी", "padegi");
        COMMON_HINDI_MAP.put("पड़ेंगे", "padenge");
        COMMON_HINDI_MAP.put("पडेंगे", "padenge");
        COMMON_HINDI_MAP.put("पड़ा", "pada");
        COMMON_HINDI_MAP.put("पड़ी", "padi");
        COMMON_HINDI_MAP.put("चाहिए", "chahiye");
        COMMON_HINDI_MAP.put("सकता", "sakta");
        COMMON_HINDI_MAP.put("सकते", "sakte");
        COMMON_HINDI_MAP.put("सकती", "sakti");
        COMMON_HINDI_MAP.put("सकेंगे", "sakenge");
        COMMON_HINDI_MAP.put("होगा", "hoga");
        COMMON_HINDI_MAP.put("होगी", "hogi");
        COMMON_HINDI_MAP.put("होंगे", "honge");
        COMMON_HINDI_MAP.put("होना", "hona");
        COMMON_HINDI_MAP.put("होने", "hone");
        COMMON_HINDI_MAP.put("रहे", "rahe");
        COMMON_HINDI_MAP.put("रहा", "raha");
        COMMON_HINDI_MAP.put("रही", "rahi");
        COMMON_HINDI_MAP.put("मेरे", "mere");
        COMMON_HINDI_MAP.put("मेरा", "mera");
        COMMON_HINDI_MAP.put("मेरी", "meri");
        COMMON_HINDI_MAP.put("तुम्हारा", "tumhara");
        COMMON_HINDI_MAP.put("तुम्हारी", "tumhari");
        COMMON_HINDI_MAP.put("तुम्हारे", "tumhare");
        COMMON_HINDI_MAP.put("तेरे", "tere");
        COMMON_HINDI_MAP.put("तेरा", "tera");
        COMMON_HINDI_MAP.put("तेरी", "teri");
        COMMON_HINDI_MAP.put("हम", "hum");
        COMMON_HINDI_MAP.put("तुम", "tum");
        COMMON_HINDI_MAP.put("आप", "aap");
        COMMON_HINDI_MAP.put("कहा", "kahan");
        COMMON_HINDI_MAP.put("कहां", "kahan");
        COMMON_HINDI_MAP.put("कहाँ", "kahan");
        COMMON_HINDI_MAP.put("यहा", "yahan");
        COMMON_HINDI_MAP.put("यहां", "yahan");
        COMMON_HINDI_MAP.put("यहाँ", "yahan");
        COMMON_HINDI_MAP.put("वहा", "wahan");
        COMMON_HINDI_MAP.put("वहां", "wahan");
        COMMON_HINDI_MAP.put("वहाँ", "wahan");
        COMMON_HINDI_MAP.put("क्या", "kya");
        COMMON_HINDI_MAP.put("क्यों", "kyun");
        COMMON_HINDI_MAP.put("क्यो", "kyun");
        COMMON_HINDI_MAP.put("कब", "kab");
        COMMON_HINDI_MAP.put("कैसे", "kaise");
        COMMON_HINDI_MAP.put("कौन", "kaun");
        COMMON_HINDI_MAP.put("अच्छा", "achha");
        COMMON_HINDI_MAP.put("नहीं", "nahi");
        COMMON_HINDI_MAP.put("नही", "nahi");
        COMMON_HINDI_MAP.put("हां", "haan");
        COMMON_HINDI_MAP.put("हाँ", "haan");
        COMMON_HINDI_MAP.put("आज", "aaj");
        COMMON_HINDI_MAP.put("कल", "kal");
        COMMON_HINDI_MAP.put("खेलेंगे", "khelenge");
        COMMON_HINDI_MAP.put("खेलेगा", "khelega");
        COMMON_HINDI_MAP.put("खेलो", "khelo");
        COMMON_HINDI_MAP.put("खेल", "khel");
        COMMON_HINDI_MAP.put("चलो", "chalo");
        COMMON_HINDI_MAP.put("चल", "chal");
        COMMON_HINDI_MAP.put("आ", "aa");
        COMMON_HINDI_MAP.put("जा", "ja");
        COMMON_HINDI_MAP.put("आओ", "aao");
        COMMON_HINDI_MAP.put("जाओ", "jao");
        COMMON_HINDI_MAP.put("आया", "aaya");
        COMMON_HINDI_MAP.put("आयी", "aayi");
        COMMON_HINDI_MAP.put("आए", "aaye");
        COMMON_HINDI_MAP.put("गया", "gaya");
        COMMON_HINDI_MAP.put("गई", "gayi");
        COMMON_HINDI_MAP.put("गए", "gaye");
        COMMON_HINDI_MAP.put("रुक", "ruk");
        COMMON_HINDI_MAP.put("रुको", "ruko");
        COMMON_HINDI_MAP.put("देखो", "dekho");
        COMMON_HINDI_MAP.put("देख", "dekh");
        COMMON_HINDI_MAP.put("बोलो", "bolo");
        COMMON_HINDI_MAP.put("बोल", "bol");
        COMMON_HINDI_MAP.put("बताओ", "batao");
        COMMON_HINDI_MAP.put("बता", "bata");
        COMMON_HINDI_MAP.put("सुनो", "suno");
        COMMON_HINDI_MAP.put("सुनों", "suno");
        COMMON_HINDI_MAP.put("दोस्त", "dost");
        COMMON_HINDI_MAP.put("घर", "ghar");
        COMMON_HINDI_MAP.put("पैसा", "paise");
        COMMON_HINDI_MAP.put("पैसे", "paise");
        COMMON_HINDI_MAP.put("पानी", "pani");
        COMMON_HINDI_MAP.put("खाना", "khana");
        COMMON_HINDI_MAP.put("हाल", "haal");
        COMMON_HINDI_MAP.put("बात", "baat");
        COMMON_HINDI_MAP.put("काम", "kaam");
        COMMON_HINDI_MAP.put("नाम", "naam");
        COMMON_HINDI_MAP.put("दिन", "din");
        COMMON_HINDI_MAP.put("रात", "raat");
        COMMON_HINDI_MAP.put("पास", "paas");
        COMMON_HINDI_MAP.put("दूर", "door");
        COMMON_HINDI_MAP.put("ऊपर", "upar");
        COMMON_HINDI_MAP.put("नीचे", "neeche");
        COMMON_HINDI_MAP.put("अंदर", "andar");
        COMMON_HINDI_MAP.put("बाहर", "bahar");
        COMMON_HINDI_MAP.put("जल्दी", "jaldi");
        COMMON_HINDI_MAP.put("सब", "sab");
        COMMON_HINDI_MAP.put("कुछ", "kuch");
        COMMON_HINDI_MAP.put("थोड़ा", "thoda");
        COMMON_HINDI_MAP.put("थोडा", "thoda");
        COMMON_HINDI_MAP.put("ज्यादा", "zyada");
        COMMON_HINDI_MAP.put("ज़्यादा", "zyada");
        COMMON_HINDI_MAP.put("बहुत", "bahut");
        COMMON_HINDI_MAP.put("कम", "kam");
        COMMON_HINDI_MAP.put("अरे", "arey");
        COMMON_HINDI_MAP.put("ओए", "oye");
        COMMON_HINDI_MAP.put("ले", "le");
        COMMON_HINDI_MAP.put("लो", "lo");
        COMMON_HINDI_MAP.put("दे", "de");
        COMMON_HINDI_MAP.put("दो", "do");
        COMMON_HINDI_MAP.put("साथ", "saath");
        COMMON_HINDI_MAP.put("अभी", "abhi");
        COMMON_HINDI_MAP.put("बाद", "baad");
        COMMON_HINDI_MAP.put("पहला", "pehla");
        COMMON_HINDI_MAP.put("पेड़", "ped");
        COMMON_HINDI_MAP.put("बड़ा", "bada");
        COMMON_HINDI_MAP.put("छोटा", "chhota");
        COMMON_HINDI_MAP.put("तेज़", "tez");
        COMMON_HINDI_MAP.put("तेज", "tez");
        COMMON_HINDI_MAP.put("खत्म", "khatam");
        COMMON_HINDI_MAP.put("चालू", "chalu");
        COMMON_HINDI_MAP.put("बंद", "band");
        COMMON_HINDI_MAP.put("खोल", "khol");
        COMMON_HINDI_MAP.put("खोलो", "kholo");
        COMMON_HINDI_MAP.put("मैं", "main");
        COMMON_HINDI_MAP.put("मै", "main");
        COMMON_HINDI_MAP.put("उसका", "uska");
        COMMON_HINDI_MAP.put("उसकी", "uski");
        COMMON_HINDI_MAP.put("उसके", "uske");
        COMMON_HINDI_MAP.put("इसका", "iska");
        COMMON_HINDI_MAP.put("इसकी", "iski");
        COMMON_HINDI_MAP.put("इसके", "iske");
        COMMON_HINDI_MAP.put("हमारा", "hamara");
        COMMON_HINDI_MAP.put("हमारी", "hamari");
        COMMON_HINDI_MAP.put("हमारे", "hamare");
        COMMON_HINDI_MAP.put("खेलना", "khelna");
        COMMON_HINDI_MAP.put("खेले", "khele");
        COMMON_HINDI_MAP.put("खेला", "khela");
        COMMON_HINDI_MAP.put("खेलूंगा", "khelunga");
        COMMON_HINDI_MAP.put("खेलूँगा", "khelunga");
        COMMON_HINDI_MAP.put("जाना", "jana");
        COMMON_HINDI_MAP.put("जाएगा", "jayega");
        COMMON_HINDI_MAP.put("जाएगी", "jayegi");
        COMMON_HINDI_MAP.put("जाएंगे", "jayenge");
        COMMON_HINDI_MAP.put("आना", "aana");
        COMMON_HINDI_MAP.put("आएगा", "aayega");
        COMMON_HINDI_MAP.put("आएगी", "aayegi");
        COMMON_HINDI_MAP.put("आएंगे", "aayenge");
        COMMON_HINDI_MAP.put("बैठो", "baitho");
        COMMON_HINDI_MAP.put("उठो", "utho");
        COMMON_HINDI_MAP.put("भागो", "bhago");
        COMMON_HINDI_MAP.put("मारो", "maro");
        COMMON_HINDI_MAP.put("बचाओ", "bachao");
        COMMON_HINDI_MAP.put("बनाओ", "banao");
        COMMON_HINDI_MAP.put("तोड़ो", "todo");
        COMMON_HINDI_MAP.put("तोड़ो", "todo");
        COMMON_HINDI_MAP.put("रखो", "rakho");
        COMMON_HINDI_MAP.put("पकड़ो", "pakdo");
        COMMON_HINDI_MAP.put("पकड़ो", "pakdo");
        COMMON_HINDI_MAP.put("छोड़ो", "chhodo");
        COMMON_HINDI_MAP.put("छोड़ो", "chhodo");
        COMMON_HINDI_MAP.put("ठीक", "theek");
        COMMON_HINDI_MAP.put("सही", "sahi");
        COMMON_HINDI_MAP.put("गलत", "galat");
        COMMON_HINDI_MAP.put("या", "ya");
        COMMON_HINDI_MAP.put("और", "aur");
        COMMON_HINDI_MAP.put("लेकिन", "lekin");
        COMMON_HINDI_MAP.put("मगर", "magar");
        COMMON_HINDI_MAP.put("क्योंकि", "kyunki");
        COMMON_HINDI_MAP.put("क्योकि", "kyunki");
        COMMON_HINDI_MAP.put("इसलिए", "isliye");
        COMMON_HINDI_MAP.put("इसलिये", "isliye");
        COMMON_HINDI_MAP.put("तो", "to");
        COMMON_HINDI_MAP.put("भी", "bhi");
        COMMON_HINDI_MAP.put("ही", "hi");
        COMMON_HINDI_MAP.put("मत", "mat");
        COMMON_HINDI_MAP.put("ना", "na");
        COMMON_HINDI_MAP.put("बस", "bas");
        COMMON_HINDI_MAP.put("सिर्फ", "sirf");
        COMMON_HINDI_MAP.put("फिर", "phir");
        COMMON_HINDI_MAP.put("तब", "tab");
        COMMON_HINDI_MAP.put("जब", "jab");
        COMMON_HINDI_MAP.put("अब", "ab");
        COMMON_HINDI_MAP.put("कोई", "koi");
        COMMON_HINDI_MAP.put("जहाँ", "jahan");
        COMMON_HINDI_MAP.put("जहां", "jahan");
        COMMON_HINDI_MAP.put("इधर", "idhar");
        COMMON_HINDI_MAP.put("उधर", "udhar");
        COMMON_HINDI_MAP.put("किधर", "kidhar");
    }

    /**
     * Transliterates a spoken sentence (potentially containing Devanagari, English words,
     * numbers, commands, and punctuation) into Romanized Hinglish.
     *
     * @param input raw input string
     * @return Romanized Hinglish output
     */
    public static String transliterate(String input) {
        if (input == null) {
            return null;
        }
        if (input.trim().isEmpty()) {
            return input;
        }

        // Detect and repair any Windows-1252 / ISO-8859-1 misdecoded UTF-8 mojibake first
        input = repairMojibake(input);

        // If the string contains no Devanagari characters, return as-is
        if (!DEVANAGARI_BLOCK.matcher(input).find()) {
            return input;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result.append(input, lastEnd, matcher.start());
            }

            String token = matcher.group(1);
            if (isDevanagariWord(token)) {
                result.append(transliterateWord(token));
            } else {
                result.append(token);
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < input.length()) {
            result.append(input.substring(lastEnd));
        }

        return result.toString();
    }

    public static boolean containsDevanagari(String text) {
        if (text == null) return false;
        return DEVANAGARI_BLOCK.matcher(text).find() || containsDevanagariMojibake(text);
    }

    /**
     * Checks if a string contains Windows-1252 or Latin-1 representations of UTF-8 Devanagari bytes.
     */
    public static boolean containsDevanagariMojibake(String text) {
        if (text == null || text.isEmpty()) return false;
        return text.contains("\u00E0\u00A4") || text.contains("\u00E0\u00A5");
    }

    /**
     * Detects and repairs Windows-1252 / ISO-8859-1 misdecoded UTF-8 sequences (mojibake).
     * For example, Devanagari text encoded as UTF-8 bytes but decoded through Windows-1252
     * results in characters like 'à¤•à¥ˆà¤¸à¥‡'.
     *
     * @param input potentially corrupted input string
     * @return corrected string with valid UTF-8 Unicode characters if mojibake was detected
     */
    public static String repairMojibake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Fast path: if it already contains valid Devanagari and no lead-byte mojibake, return directly
        boolean hasDevanagari = DEVANAGARI_BLOCK.matcher(input).find();
        if (hasDevanagari && !containsDevanagariMojibake(input)) {
            return input;
        }

        if (containsDevanagariMojibake(input) || input.contains("\u00E0") || input.contains("\u00C3")) {
            try {
                byte[] cp1252Bytes = input.getBytes(Charset.forName("Windows-1252"));
                String candidate = new String(cp1252Bytes, StandardCharsets.UTF_8);
                if (DEVANAGARI_BLOCK.matcher(candidate).find()) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }

            try {
                byte[] isoBytes = input.getBytes(StandardCharsets.ISO_8859_1);
                String candidate = new String(isoBytes, StandardCharsets.UTF_8);
                if (DEVANAGARI_BLOCK.matcher(candidate).find()) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }

        return input;
    }

    private static boolean isDevanagariWord(String word) {
        if (word == null || word.isEmpty()) return false;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transliterates a single Devanagari word using loanword matching,
     * common Hindi vocabulary matching, or phonetic rule-based transliteration.
     */
    public static String transliterateWord(String word) {
        if (word == null || word.isEmpty()) return "";

        // Purna Viram and Deergh Viram
        if ("।".equals(word) || "॥".equals(word)) {
            return ".";
        }

        // Normalize Unicode composed forms (NFC) so decomposed nuktas match dictionary
        String normalized = Normalizer.normalize(word, Normalizer.Form.NFC);

        // 1. Direct loanword match
        String loan = LOANWORDS_MAP.get(normalized);
        if (loan != null) {
            return loan;
        }

        // 2. Direct common Hindi vocabulary match
        String common = COMMON_HINDI_MAP.get(normalized);
        if (common != null) {
            return common;
        }

        // 3. Fallback: Rule-based phonetic transliteration with schwa deletion
        return phoneticTransliterate(normalized);
    }

    /**
     * Rule-based phonetic transliteration of Devanagari into standard Roman Hindi
     * with Hindi schwa deletion and candra vowel support.
     */
    private static String phoneticTransliterate(String devanagari) {
        StringBuilder sb = new StringBuilder();
        int len = devanagari.length();

        for (int i = 0; i < len; i++) {
            char c = devanagari.charAt(i);

            // Purna Viram (danda) and Deergh Viram (double danda) -> period
            if (c == 0x0964 || c == 0x0965) {
                sb.append('.');
                continue;
            }

            // Handle independent vowels
            String indVowel = getIndependentVowel(c);
            if (indVowel != null) {
                sb.append(indVowel);
                continue;
            }

            // Check if current char is a consonant (accounting for decomposed nukta)
            boolean hasNukta = false;
            if (i + 1 < len && devanagari.charAt(i + 1) == 0x093C) {
                hasNukta = true;
            }

            String consonant = hasNukta ? getNuktaConsonant(c) : getConsonant(c);
            if (consonant != null) {
                sb.append(consonant);
                if (hasNukta) {
                    i++; // Consume nukta
                }

                // Look ahead for matra, virama (halant), anusvara, chandrabindu, or next consonant
                if (i + 1 < len) {
                    char next = devanagari.charAt(i + 1);
                    if (isMatra(next)) {
                        String matraStr = getMatra(next);
                        i++; // Consume matra

                        // Check if matra is followed by anusvara (0x0902) or chandrabindu (0x0901)
                        if (i + 1 < len && (devanagari.charAt(i + 1) == 0x0902 || devanagari.charAt(i + 1) == 0x0901)) {
                            i++; // Consume anusvara
                            if ("e".equals(matraStr)) {
                                sb.append("ein"); // e.g. में -> mein
                            } else if ("ai".equals(matraStr)) {
                                sb.append("ain"); // e.g. हैं -> hain
                            } else if ("o".equals(matraStr)) {
                                sb.append("on");  // e.g. लोगों -> logon
                            } else if ("a".equals(matraStr)) {
                                sb.append("an");  // e.g. कहाँ -> kahan
                            } else {
                                sb.append(matraStr).append("n");
                            }
                        } else {
                            sb.append(matraStr);
                        }
                    } else if (next == 0x094D) { // Virama / Halant ्
                        i++; // Consume virama (suppresses inherent vowel, e.g. blk -> block)
                    } else if (next == 0x0902 || next == 0x0901) { // Anusvara ं / Chandrabindu ँ
                        sb.append("an");
                        i++;
                    } else if (isConsonant(next)) {
                        // Inherent 'a' between consonants unless medial schwa deletion applies
                        if (!shouldDeleteMedialSchwa(devanagari, i)) {
                            sb.append('a');
                        }
                    }
                }
                // Word-final consonant: Hindi schwa deletion applies (no trailing 'a')
                continue;
            }

            // Standalone matras or diacritics
            if (c == 0x0902 || c == 0x0901) {
                sb.append('n');
            } else if (c == 0x0903) {
                sb.append('h');
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Checks if Hindi medial schwa deletion rule applies:
     * In a word with 3 or more syllables/consonants, an unstressed schwa in VC_CV
     * environment is deleted before an inflected consonant ending with a vowel.
     * E.g. करना (k-a-r-n-a -> karna), चलना (chalna), पड़ेगा (padega).
     */
    private static boolean shouldDeleteMedialSchwa(String s, int currentIndex) {
        if (currentIndex < 1 || currentIndex + 1 >= s.length()) {
            return false;
        }

        // Must be preceded by a consonant or vowel
        char prev = s.charAt(currentIndex - 1);
        boolean hasPrecedingSyllable = isConsonant(prev) || isMatra(prev) || getIndependentVowel(prev) != null;
        if (!hasPrecedingSyllable) {
            return false;
        }

        // Next character is consonant
        char next = s.charAt(currentIndex + 1);
        if (!isConsonant(next)) {
            return false;
        }

        // Character after next is matra (vowel suffix)
        if (currentIndex + 2 < s.length()) {
            char afterNext = s.charAt(currentIndex + 2);
            return isMatra(afterNext);
        }

        return false;
    }

    private static String getIndependentVowel(char c) {
        switch (c) {
            case 0x0905: return "a";
            case 0x0906: return "aa";
            case 0x0907: return "i";
            case 0x0908: return "ee";
            case 0x0909: return "u";
            case 0x090A: return "oo";
            case 0x090B: return "ri";
            case 0x090D: return "e";  // ऍ (candra E)
            case 0x090E: return "e";  // ऎ (short E)
            case 0x090F: return "e";  // ए
            case 0x0910: return "ai"; // ऐ
            case 0x0911: return "o";  // ऑ (candra O, e.g. online, offline)
            case 0x0912: return "o";  // ऒ (short O)
            case 0x0913: return "o";  // ओ
            case 0x0914: return "au"; // औ
            default: return null;
        }
    }

    private static boolean isConsonant(char c) {
        return (c >= 0x0915 && c <= 0x0939) || (c >= 0x0958 && c <= 0x095F);
    }

    private static String getConsonant(char c) {
        switch (c) {
            case 0x0915: return "k";
            case 0x0916: return "kh";
            case 0x0917: return "g";
            case 0x0918: return "gh";
            case 0x0919: return "ng";
            case 0x091A: return "ch";
            case 0x091B: return "chh";
            case 0x091C: return "j";
            case 0x091D: return "jh";
            case 0x091E: return "ny";
            case 0x091F: return "t";
            case 0x0920: return "th";
            case 0x0921: return "d";
            case 0x0922: return "dh";
            case 0x0923: return "n";
            case 0x0924: return "t";
            case 0x0925: return "th";
            case 0x0926: return "d";
            case 0x0927: return "dh";
            case 0x0928: return "n";
            case 0x092A: return "p";
            case 0x092B: return "ph";
            case 0x092C: return "b";
            case 0x092D: return "bh";
            case 0x092E: return "m";
            case 0x092F: return "y";
            case 0x0930: return "r";
            case 0x0932: return "l";
            case 0x0933: return "l";
            case 0x0935: return "v";
            case 0x0936: return "sh";
            case 0x0937: return "sh";
            case 0x0938: return "s";
            case 0x0939: return "h";
            // Precomposed Nukta consonants
            case 0x0958: return "q";
            case 0x0959: return "kh";
            case 0x095A: return "gh";
            case 0x095B: return "z";
            case 0x095C: return "d";
            case 0x095D: return "dh";
            case 0x095E: return "f";
            case 0x095F: return "y";
            default: return null;
        }
    }

    private static String getNuktaConsonant(char base) {
        switch (base) {
            case 0x0915: return "q";   // क़
            case 0x0916: return "kh";  // ख़
            case 0x0917: return "gh";  // ग़
            case 0x091C: return "z";   // ज़
            case 0x0921: return "d";   // ड़
            case 0x0922: return "dh";  // ढ़
            case 0x092A:
            case 0x092B: return "f";   // फ़
            default: return getConsonant(base);
        }
    }

    private static boolean isMatra(char c) {
        return (c >= 0x093E && c <= 0x094C) || c == 0x0962 || c == 0x0963;
    }

    private static String getMatra(char c) {
        switch (c) {
            case 0x093E: return "a";   // ा
            case 0x093F: return "i";   // ि
            case 0x0940: return "i";   // ी
            case 0x0941: return "u";   // ु
            case 0x0942: return "oo";  // ू
            case 0x0943: return "ri";  // ृ
            case 0x0945: return "e";   // ॅ (candra E)
            case 0x0946: return "e";   // ॆ (short E)
            case 0x0947: return "e";   // े
            case 0x0948: return "ai";  // ै
            case 0x0949: return "o";   // ॉ (candra O, e.g. login, block, join, config, voice)
            case 0x094A: return "o";   // ॊ (short O)
            case 0x094B: return "o";   // ो
            case 0x094C: return "au";  // ौ
            default: return "";
        }
    }
}
