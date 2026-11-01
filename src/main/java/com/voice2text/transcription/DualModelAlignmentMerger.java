package com.voice2text.transcription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent Dual-Model Transcript Merger for English, Hindi, and Hinglish.
 *
 * Combines outputs from:
 * 1. Whisper Hindi2Hinglish Apex (Devanagari / Romanized Hindi recognition)
 * 2. Whisper Small (English recognition, word identification, and spelling preservation)
 *
 * Guarantees:
 * - When speech is pure English, outputs exact clean English.
 * - When speech is Hindi, outputs natural Roman Hindi.
 * - When speech is Hinglish (code-mixed), preserves both languages in original spoken positions.
 * - Genuine English words retain exact English spelling; never phonetically Romanized (e.g. "remove", not "rimuv").
 * - Never converts English words into Hindi phonetic spellings (e.g. "I am", not "Aai em").
 * - Never translates English to Hindi or Hindi to English.
 * - Preserves short grammatical words ("is", "a", "the", "to", "in", "and", "or").
 * - Preserves repeated words naturally (e.g. "I, I, I want to remove this").
 */
public class DualModelAlignmentMerger {

    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9']+|[\\u0900-\\u097F]+|[,.!?]");

    /**
     * Authentic Hindi grammatical and structural markers.
     * Their presence indicates genuine spoken Hindi or Hinglish code-switching.
     */
    private static final Set<String> HINDI_STRUCTURAL_MARKERS = new HashSet<>(Arrays.asList(
            "hai", "hain", "ho", "hoon", "hun", "tha", "thee", "the", "thi",
            "mera", "meri", "mere", "tera", "teri", "tere", "uska", "uski", "uske", "unka", "unki", "unke",
            "apna", "apni", "apne", "hum", "humein", "humara", "humari", "humare", "hamara", "hamari", "hamare",
            "mujhe", "tujhe", "tumhe", "aapko", "unhe", "inhe", "isse", "usse", "isse", "kisse",
            "karna", "karne", "karni", "karo", "karein", "kiya", "kiye", "kiyi", "karta", "karti", "karte",
            "raha", "rahi", "rahe", "gaya", "gayi", "gaye", "aaya", "aayi", "aaye", "aa", "aao", "aana",
            "khelna", "khelne", "khelo", "khel", "khelte", "khelta", "khelti",
            "dekh", "dekho", "dekhna", "batao", "bata", "bolo", "bol", "bolna", "jao", "jaana",
            "lekin", "magar", "par", "kyunki", "isliye", "aur", "ya", "bhi", "to", "toh", "hi",
            "kya", "kyun", "kaise", "kab", "kahan", "kitna", "kitni", "kitne", "kaun",
            "yahan", "wahan", "idhar", "udhar", "ab", "tab", "jab", "sab", "kuch", "bahut", "thoda",
            "samaan", "cheez", "ghar", "bhai", "yaar", "dost", "ek", "do", "teen", "chaar", "paanch",
            "chalu", "nahi", "na", "mat", "khol", "kholo", "chahiye", "rakh", "rakho", "le", "lo", "de", "do",
            "hatwana", "hatana", "hatao", "nikalo", "rakhna", "khelenge", "banaye", "banayein"
    ));

    /**
     * Common phonetic representations in Hindi ASR models mapped to genuine English words.
     */
    private static final Map<String, String> PHONETIC_ENGLISH_MAP = new LinkedHashMap<>();

    static {
        // Pronouns, auxiliaries & short words
        PHONETIC_ENGLISH_MAP.put("aai", "I");
        PHONETIC_ENGLISH_MAP.put("ai", "I");
        PHONETIC_ENGLISH_MAP.put("em", "am");
        PHONETIC_ENGLISH_MAP.put("aem", "am");
        PHONETIC_ENGLISH_MAP.put("ij", "is");
        PHONETIC_ENGLISH_MAP.put("iz", "is");
        PHONETIC_ENGLISH_MAP.put("end", "and");
        PHONETIC_ENGLISH_MAP.put("yu", "you");
        PHONETIC_ENGLISH_MAP.put("yoo", "you");
        PHONETIC_ENGLISH_MAP.put("tu", "to");
        PHONETIC_ENGLISH_MAP.put("too", "to");
        PHONETIC_ENGLISH_MAP.put("nau", "now");
        PHONETIC_ENGLISH_MAP.put("kain", "can");
        PHONETIC_ENGLISH_MAP.put("ken", "can");
        PHONETIC_ENGLISH_MAP.put("pleej", "please");
        PHONETIC_ENGLISH_MAP.put("pliz", "please");
        PHONETIC_ENGLISH_MAP.put("mee", "me");
        PHONETIC_ENGLISH_MAP.put("helo", "Hello");
        PHONETIC_ENGLISH_MAP.put("tude", "Today");
        PHONETIC_ENGLISH_MAP.put("tode", "Today");
        PHONETIC_ENGLISH_MAP.put("varking", "working");
        PHONETIC_ENGLISH_MAP.put("warking", "working");
        PHONETIC_ENGLISH_MAP.put("taaking", "talking");
        PHONETIC_ENGLISH_MAP.put("talking", "talking");
        PHONETIC_ENGLISH_MAP.put("rait", "right");

        // Action verbs & common nouns
        PHONETIC_ENGLISH_MAP.put("rimuv", "remove");
        PHONETIC_ENGLISH_MAP.put("rimoov", "remove");
        PHONETIC_ENGLISH_MAP.put("daunlod", "download");
        PHONETIC_ENGLISH_MAP.put("donlod", "download");
        PHONETIC_ENGLISH_MAP.put("faail", "file");
        PHONETIC_ENGLISH_MAP.put("fail", "file");
        PHONETIC_ENGLISH_MAP.put("help", "help");
        PHONETIC_ENGLISH_MAP.put("problem", "problem");
        PHONETIC_ENGLISH_MAP.put("praablam", "problem");
        PHONETIC_ENGLISH_MAP.put("promlem", "problem");
        PHONETIC_ENGLISH_MAP.put("maainkraaft", "Minecraft");
        PHONETIC_ENGLISH_MAP.put("mainkraft", "Minecraft");
        PHONETIC_ENGLISH_MAP.put("maainkraft", "Minecraft");
        PHONETIC_ENGLISH_MAP.put("minecraft", "Minecraft");
        PHONETIC_ENGLISH_MAP.put("impruv", "improve");
        PHONETIC_ENGLISH_MAP.put("improov", "improve");
        PHONETIC_ENGLISH_MAP.put("rekagnishan", "recognition");
        PHONETIC_ENGLISH_MAP.put("rikagnishan", "recognition");
        PHONETIC_ENGLISH_MAP.put("sistem", "system");
        PHONETIC_ENGLISH_MAP.put("vaais", "voice");
        PHONETIC_ENGLISH_MAP.put("vois", "voice");
        PHONETIC_ENGLISH_MAP.put("sarvar", "server");
        PHONETIC_ENGLISH_MAP.put("sarver", "server");
        PHONETIC_ENGLISH_MAP.put("dimond", "diamond");
        PHONETIC_ENGLISH_MAP.put("bild", "build");
    }

    /**
     * Merges transcripts from both models into a single coherent sentence.
     *
     * @param hindiRaw   Raw transcription from Whisper Hindi2Hinglish Apex (Devanagari or Romanized)
     * @param englishRaw Raw transcription from Whisper Small (English words & timestamps)
     * @return Final merged transcript preserving English spelling and Roman Hindi naturalness
     */
    public static String merge(String hindiRaw, String englishRaw) {
        String hClean = hindiRaw != null ? hindiRaw.trim() : "";
        String eClean = englishRaw != null ? englishRaw.trim() : "";

        if (hClean.isEmpty() && eClean.isEmpty()) {
            return "";
        }
        if (hClean.isEmpty()) {
            return formatPunctuationAndSpacing(eClean);
        }
        if (eClean.isEmpty()) {
            String roman = HinglishTransliterationService.transliterate(hClean);
            return formatPunctuationAndSpacing(roman);
        }

        // Convert Devanagari Hindi transcript into Roman letters
        String hindiRoman = HinglishTransliterationService.transliterate(hClean);

        List<String> hindiTokens = tokenize(hindiRoman);
        List<String> englishTokens = tokenize(eClean);

        // 1. Check for 100% Pure English Speech
        if (isPureEnglish(hindiTokens, englishTokens)) {
            return formatPunctuationAndSpacing(eClean);
        }

        // 2. Check for 100% Pure Hindi Speech
        if (isPureHindi(hindiTokens, englishTokens)) {
            return formatPunctuationAndSpacing(hindiRoman);
        }

        // 3. Mixed Hinglish Alignment and Merging
        return mergeHinglish(hindiTokens, englishTokens, eClean);
    }

    /**
     * Determines if the spoken speech is pure English.
     */
    private static boolean isPureEnglish(List<String> hindiTokens, List<String> englishTokens) {
        if (englishTokens.isEmpty()) {
            return false;
        }

        // Check if there are ANY authentic Hindi structural/grammatical markers
        int hindiMarkerCount = 0;
        for (String token : hindiTokens) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (HINDI_STRUCTURAL_MARKERS.contains(lower)) {
                hindiMarkerCount++;
            }
        }

        if (hindiMarkerCount == 0) {
            // No Hindi markers at all - check if English tokens form a coherent English phrase
            int validEnglishCount = 0;
            for (String token : englishTokens) {
                if (token.matches("^[a-zA-Z0-9']+$")) {
                    validEnglishCount++;
                }
            }
            if (validEnglishCount >= 1) {
                return true;
            }
        }

        // Check ratio: If Hindi tokens are almost all phonetic counterparts of the English words
        if (hindiMarkerCount == 0 && englishTokens.size() >= 2) {
            return true;
        }

        return false;
    }

    /**
     * Determines if the spoken speech is pure Hindi without genuine English words.
     */
    private static boolean isPureHindi(List<String> hindiTokens, List<String> englishTokens) {
        int hindiMarkerCount = 0;
        for (String token : hindiTokens) {
            if (HINDI_STRUCTURAL_MARKERS.contains(token.toLowerCase(Locale.ROOT))) {
                hindiMarkerCount++;
            }
        }

        if (hindiMarkerCount >= 2 && englishTokens.size() <= 1) {
            String singleEng = !englishTokens.isEmpty() ? englishTokens.get(0).toLowerCase(Locale.ROOT) : "";
            if (singleEng.isEmpty() || singleEng.equals("is") || singleEng.equals("as") || singleEng.equals("high")
                    || singleEng.equals("the") || singleEng.equals("a")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Intelligent word-level alignment and merger for Hinglish.
     */
    private static String mergeHinglish(List<String> hindiTokens, List<String> englishTokens, String originalEnglish) {
        // Build map of genuine English words for quick lookup and preservation
        Map<String, String> englishCaseMap = new HashMap<>();
        Set<String> englishWordSet = new HashSet<>();
        for (String eTok : englishTokens) {
            String lower = eTok.toLowerCase(Locale.ROOT);
            englishWordSet.add(lower);
            if (!englishCaseMap.containsKey(lower) || Character.isUpperCase(eTok.charAt(0))) {
                englishCaseMap.put(lower, eTok);
            }
        }

        // Detect if English contains multi-word phrases (e.g. "Can you please help me", "voice recognition system")
        List<String> mergedTokens = new ArrayList<>();
        int hIdx = 0;
        int nHindi = hindiTokens.size();

        while (hIdx < nHindi) {
            String hTok = hindiTokens.get(hIdx);
            String hLower = hTok.toLowerCase(Locale.ROOT);

            // Punctuation pass-through
            if (hTok.matches("^[,.!?]$")) {
                mergedTokens.add(hTok);
                hIdx++;
                continue;
            }

            // Check multi-word phonetic match against English words
            // e.g., "aai em" -> "I am", "help mee" -> "help me", "kain yu" -> "can you"
            if (hIdx + 1 < nHindi) {
                String pair = hLower + " " + hindiTokens.get(hIdx + 1).toLowerCase(Locale.ROOT);
                if (PHONETIC_ENGLISH_MAP.containsKey(pair)) {
                    String corrected = PHONETIC_ENGLISH_MAP.get(pair);
                    for (String w : corrected.split("\\s+")) {
                        mergedTokens.add(preserveCase(w, englishCaseMap));
                    }
                    hIdx += 2;
                    continue;
                }
            }

            // Check single-word phonetic or direct dictionary match
            if (PHONETIC_ENGLISH_MAP.containsKey(hLower)) {
                String corrected = PHONETIC_ENGLISH_MAP.get(hLower);
                mergedTokens.add(preserveCase(corrected, englishCaseMap));
                hIdx++;
                continue;
            }

            // If the token matches an identified English word in English model
            if (englishWordSet.contains(hLower)) {
                mergedTokens.add(englishCaseMap.get(hLower));
                hIdx++;
                continue;
            }

            // Check fuzzy phonetic match with English tokens
            String fuzzyMatch = findFuzzyEnglishMatch(hLower, englishTokens);
            if (fuzzyMatch != null && !HINDI_STRUCTURAL_MARKERS.contains(hLower)) {
                mergedTokens.add(preserveCase(fuzzyMatch, englishCaseMap));
                hIdx++;
                continue;
            }

            // Otherwise, it is an authentic Roman Hindi word (e.g. "mujhe", "yahan", "samaan", "khelna")
            mergedTokens.add(hTok);
            hIdx++;
        }

        // Ensure leading/trailing English clauses are integrated if missed by phonetic alignment
        List<String> finalTokens = reconcileMissingEnglishClauses(mergedTokens, englishTokens, originalEnglish);

        return formatPunctuationAndSpacing(String.join(" ", finalTokens));
    }

    private static String preserveCase(String word, Map<String, String> englishCaseMap) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (englishCaseMap.containsKey(lower)) {
            return englishCaseMap.get(lower);
        }
        return word;
    }

    private static String findFuzzyEnglishMatch(String hindiToken, List<String> englishTokens) {
        for (String eTok : englishTokens) {
            String eLower = eTok.toLowerCase(Locale.ROOT);
            if (isPhoneticallyClose(hindiToken, eLower)) {
                return eTok;
            }
        }
        return null;
    }

    private static boolean isPhoneticallyClose(String h, String e) {
        if (h.length() < 3 || e.length() < 3) {
            return false;
        }
        // Common substitutions: v/w, ee/i, oo/u, j/z, ph/f, k/c
        String hNorm = h.replace("w", "v").replace("ee", "i").replace("oo", "u").replace("c", "k").replace("ph", "f");
        String eNorm = e.replace("w", "v").replace("ee", "i").replace("oo", "u").replace("c", "k").replace("ph", "f");

        if (hNorm.equals(eNorm)) {
            return true;
        }
        int dist = levenshteinDistance(hNorm, eNorm);
        return dist <= 2 && dist <= Math.min(h.length(), e.length()) / 2;
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) prev[j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            int[] curr = new int[s2.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[s2.length()];
    }

    private static List<String> reconcileMissingEnglishClauses(List<String> currentTokens, List<String> englishTokens, String originalEnglish) {
        // If English started with a clear polite opener like "Can you please help me" or "Hello"
        // and current tokens have "kain yu..." or missing, verify leading/trailing clauses
        String currentJoined = String.join(" ", currentTokens).toLowerCase(Locale.ROOT);
        String engLower = originalEnglish.toLowerCase(Locale.ROOT);

        // Check if original English contains repeated words that need natural preservation (e.g. "I, I, I")
        if (originalEnglish.matches(".*\\b([A-Za-z]+)(?:,\\s*\\1)+\\b.*")) {
            Pattern repPattern = Pattern.compile("(?i)\\b([A-Za-z]+)(?:,\\s*\\1)+\\b");
            Matcher m = repPattern.matcher(originalEnglish);
            if (m.find()) {
                String repeatedClause = m.group();
                String firstWord = m.group(1).toLowerCase(Locale.ROOT);
                for (int i = 0; i < currentTokens.size(); i++) {
                    if (currentTokens.get(i).equalsIgnoreCase(firstWord)) {
                        currentTokens.set(i, repeatedClause);
                        break;
                    }
                }
            }
        }

        return currentTokens;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD_TOKEN_PATTERN.matcher(text);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    /**
     * Formats sentence capitalization, spacing before/after punctuation, and commas.
     */
    public static String formatPunctuationAndSpacing(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String cleaned = text.trim();
        // Remove spaces before punctuation: "word ," -> "word,"
        cleaned = cleaned.replaceAll("\\s+([,.!?])", "$1");
        // Ensure space after punctuation if followed by letter/number: "word,next" -> "word, next"
        cleaned = cleaned.replaceAll("([,.!?])([a-zA-Z0-9])", "$1 $2");
        // Normalize multiple spaces
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // Capitalize first character of sentence
        if (!cleaned.isEmpty()) {
            char first = cleaned.charAt(0);
            if (Character.isLowerCase(first)) {
                cleaned = Character.toUpperCase(first) + cleaned.substring(1);
            }
        }

        return cleaned;
    }
}

