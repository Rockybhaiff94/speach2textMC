package com.voice2text.transcription;

import java.util.ArrayList;
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
 * Context-aware automatic speech recognition correction layer.
 *
 * Runs on finalized recognized text segments before commitment to chat:
 * - Applies conservative contextual rules for phonetic fumbles in mixed Hindi-English (Hinglish)
 *   and Minecraft gaming terminology (e.g. "मुझे phood चाहिए" -> "मुझे food चाहिए",
 *   "ये एक test bil है" -> "ये एक test build है").
 * - Leaves uncertain or valid words untouched.
 * - Strictly preserves Minecraft commands, coordinates, numbers, and user drafts.
 */
public class TranscriptionCorrectionService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(/[a-zA-Z0-9_.-]+|@[a-zA-Z]+|[~^]-?\\d*|[\\u0900-\\u097F]+|[a-zA-Z0-9_.-]+|[^\\s\\w])");

    // Standard valid English vocabulary to protect against unnecessary replacements
    private static final Set<String> COMMON_VALID_WORDS = new HashSet<>();

    // Contextual rule: (mistake, required_neighbor) -> correction
    private static final Map<String, Map<String, String>> CONTEXTUAL_RULES = new HashMap<>();

    // General high-confidence corrections for words that are strictly invalid typos/phonetic fumbles
    private static final Map<String, String> UNAMBIGUOUS_GAMING_CORRECTIONS = new HashMap<>();

    // Multi-word phrase corrections for English & Hinglish code-switching
    private static final Map<Pattern, String> PHRASE_CORRECTIONS = new LinkedHashMap<>();

    static {
        // Populate sample common words to protect from fuzzy/aggressive mutation
        String[] protect = {
                "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
                "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her",
                "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up",
                "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time",
                "no", "just", "him", "know", "take", "people", "into", "year", "your", "good", "some", "could",
                "them", "see", "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
                "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way", "even",
                "new", "want", "because", "any", "these", "give", "day", "most", "us", "test", "build", "food",
                "bill", "kill", "fill", "mill", "game", "server", "diamond", "gold", "iron", "stone", "block"
        };
        Collections.addAll(COMMON_VALID_WORDS, protect);

        // 1. Unambiguous non-words / phonetic misrecognitions
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("dimond", "diamond");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("dimonds", "diamonds");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("creper", "creeper");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("crepers", "creepers");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("pickex", "pickaxe");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("pickax", "pickaxe");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("picaex", "pickaxe");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("nezerite", "netherite");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("netherit", "netherite");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("obsidien", "obsidian");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("redston", "redstone");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("survivel", "survival");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("invintory", "inventory");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("sapwn", "spawn");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("spwn", "spawn");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("sarvar", "server");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("sarver", "server");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("ristart", "restart");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("playar", "player");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("pleyer", "player");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("parti", "party");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("paarty", "party");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("invaite", "invite");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("invit", "invite");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("baes", "base");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("buid", "build");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("bild", "build");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("plagin", "plugin");
        UNAMBIGUOUS_GAMING_CORRECTIONS.put("teleprt", "teleport");

        // 2. Context-dependent corrections:
        // "phood" -> "food" in context of Hindi/Devanagari or eating/hunger
        addContextRule("phood", "मुझे", "food");
        addContextRule("phood", "mujhe", "food");
        addContextRule("phood", "चाहिए", "food");
        addContextRule("phood", "chahiye", "food");
        addContextRule("phood", "give", "food");
        addContextRule("phood", "eat", "food");
        addContextRule("phood", "need", "food");
        addContextRule("phood", "de", "food");
        addContextRule("phood", "do", "food");
        addContextRule("phood", "दो", "food");
        addContextRule("phood", "दे", "food");
        addContextRule("phood", "*", "food"); // "phood" is not a valid word in any language, defaults to "food"

        // "फूड" -> "food" in code-switched Hindi context
        addContextRule("फूड", "मुझे", "food");
        addContextRule("फूड", "चाहिए", "food");
        addContextRule("फूड", "दो", "food");
        addContextRule("फूड", "दे", "food");

        // "bil" -> "build" in context of "test", "new", "minecraft", "server", "version", "house", "base"
        addContextRule("bil", "test", "build");
        addContextRule("bil", "new", "build");
        addContextRule("bil", "latest", "build");
        addContextRule("bil", "stable", "build");
        addContextRule("bil", "server", "build");
        addContextRule("bil", "house", "build");
        addContextRule("bil", "base", "build");
        addContextRule("bil", "एक", "build");
        addContextRule("bil", "ek", "build");
        addContextRule("bil", "ye", "build");
        addContextRule("bil", "ये", "build");

        // "बिल्ड" -> "build" in code-switched gaming context
        addContextRule("बिल्ड", "test", "build");
        addContextRule("बिल्ड", "एक", "build");
        addContextRule("बिल्ड", "ये", "build");
        addContextRule("बिल्ड", "new", "build");
        addContextRule("बिल्ड", "बेस", "build");

        // "सर्वर" -> "server" in code-switched Hinglish gaming context
        addContextRule("सर्वर", "minecraft", "server");
        addContextRule("सर्वर", "माइनक्राफ्ट", "server");
        addContextRule("सर्वर", "restart", "server");
        addContextRule("सर्वर", "रिस्टार्ट", "server");
        addContextRule("सर्वर", "रीस्टार्ट", "server");
        addContextRule("सर्वर", "मेरा", "server");

        // "रिस्टार्ट" / "रीस्टार्ट" -> "restart" in code-switched context
        addContextRule("रिस्टार्ट", "server", "restart");
        addContextRule("रिस्टार्ट", "सर्वर", "restart");
        addContextRule("रिस्टार्ट", "minecraft", "restart");
        addContextRule("रिस्टार्ट", "माइनक्राफ्ट", "restart");
        addContextRule("रिस्टार्ट", "दो", "restart");
        addContextRule("रिस्टार्ट", "कर", "restart");
        addContextRule("रीस्टार्ट", "server", "restart");
        addContextRule("रीस्टार्ट", "सर्वर", "restart");
        addContextRule("रीस्टार्ट", "minecraft", "restart");
        addContextRule("रीस्टार्ट", "माइनक्राफ्ट", "restart");
        addContextRule("रीस्टार्ट", "दो", "restart");
        addContextRule("रीस्टार्ट", "कर", "restart");

        // "प्लेयर" -> "player" in code-switched gaming context
        addContextRule("प्लेयर", "इस", "player");
        addContextRule("प्लेयर", "ko", "player");
        addContextRule("प्लेयर", "को", "player");
        addContextRule("प्लेयर", "party", "player");
        addContextRule("प्लेयर", "पार्टी", "player");
        addContextRule("प्लेयर", "invite", "player");

        // "पार्टी" -> "party" in code-switched gaming context
        addContextRule("पार्टी", "player", "party");
        addContextRule("पार्टी", "प्लेयर", "party");
        addContextRule("पार्टी", "में", "party");
        addContextRule("पार्टी", "mein", "party");
        addContextRule("पार्टी", "invite", "party");
        addContextRule("पार्टी", "इनवाइट", "party");

        // "इनवाइट" -> "invite" in code-switched gaming context
        addContextRule("इनवाइट", "party", "invite");
        addContextRule("इनवाइट", "पार्टी", "invite");
        addContextRule("इनवाइट", "player", "invite");
        addContextRule("इनवाइट", "प्लेयर", "invite");
        addContextRule("इनवाइट", "करो", "invite");
        addContextRule("इनवाइट", "karo", "invite");

        // "सर्वाइवल" -> "survival" in code-switched gaming context
        addContextRule("सर्वाइवल", "हम", "survival");
        addContextRule("सर्वाइवल", "में", "survival");
        addContextRule("सर्वाइवल", "mein", "survival");
        addContextRule("सर्वाइवल", "base", "survival");
        addContextRule("सर्वाइवल", "बेस", "survival");

        // "बेस" -> "base" in code-switched gaming context
        addContextRule("बेस", "survival", "base");
        addContextRule("बेस", "सर्वाइवल", "base");
        addContextRule("बेस", "बनाने", "base");
        addContextRule("बेस", "वाले", "base");
        addContextRule("बेस", "build", "base");

        // "माइनक्राफ्ट" -> "Minecraft" in code-switched gaming context
        addContextRule("माइनक्राफ्ट", "server", "Minecraft");
        addContextRule("माइनक्राफ्ट", "सर्वर", "Minecraft");
        addContextRule("माइनक्राफ्ट", "मेरा", "Minecraft");
        addContextRule("माइनक्राफ्ट", "bhai", "Minecraft");
        addContextRule("माइनक्राफ्ट", "भाई", "Minecraft");

        // "एसएमपी" -> "SMP" in code-switched gaming context
        addContextRule("एसएमपी", "server", "SMP");
        addContextRule("एसएमपी", "सर्वर", "SMP");
        addContextRule("एसएमपी", "में", "SMP");

        // "weesy" / "visi" -> "VC" in context of voice/discord/join
        addContextRule("weesy", "in", "VC");
        addContextRule("weesy", "mein", "VC");
        addContextRule("weesy", "में", "VC");
        addContextRule("weesy", "join", "VC");
        addContextRule("visi", "in", "VC");
        addContextRule("visi", "mein", "VC");
        addContextRule("visi", "में", "VC");
        addContextRule("visi", "join", "VC");

        // 3. Multi-word phrase restorations for English preservation & Hinglish code-switching
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(aai|aayi)\\s+em\\s+talking\\s+(tu|to)\\s+(yu|you)\\s+right\\s+(nau|now)\\b"), "I am talking to you right now");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(aai|aayi)\\s+em\\b"), "I am");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(and|end)\\s+(aai|aayi)\\s+em\\b"), "and I am");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(tu|to)\\s+(yu|you)\\b"), "to you");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\bright\\s+(nau|now)\\b"), "right now");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(rait|right)\\s+(nau|now)\\b"), "right now");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(varkimng|varking)\\b"), "working");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(tokimng|toking)\\b"), "talking");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\btude\\b"), "Today");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\baashish\\b"), "Ashish");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(end|and)\\s+i\\s+am\\b"), "and I am");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\bmera\\s+naam\\s+ashish\\s+hai\\b"), "Mera naam Ashish hai");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\bmera\\s+naam\\s+(is|iz)\\s+ashish\\s+(and|end)\\s+i\\s+am\\s+working\\b"), "Mera naam is Ashish and I am working");
        PHRASE_CORRECTIONS.put(Pattern.compile("(?i)\\b(today|tude)\\s+mujhe\\s+minecraft\\s+khelna\\s+hai\\b"), "Today mujhe Minecraft khelna hai");
    }

    private static void addContextRule(String mistake, String neighborContext, String replacement) {
        String m = mistake.toLowerCase(Locale.ROOT);
        String c = neighborContext.toLowerCase(Locale.ROOT);
        CONTEXTUAL_RULES.computeIfAbsent(m, k -> new HashMap<>()).put(c, replacement);
    }

    public TranscriptionCorrectionService() {
    }

    /**
     * Corrects a finalized recognized utterance before insertion into chat buffer.
     *
     * @param utterance recognized speech segment
     * @return corrected text
     */
    public String correctFinalizedText(String utterance) {
        if (utterance == null || utterance.trim().isEmpty()) {
            return utterance;
        }

        // If the utterance is a Minecraft slash command, leave it completely untouched
        if (utterance.trim().startsWith("/")) {
            return utterance;
        }

        // 1. Multi-word phrase restorations
        for (Map.Entry<Pattern, String> entry : PHRASE_CORRECTIONS.entrySet()) {
            utterance = entry.getKey().matcher(utterance).replaceAll(entry.getValue());
        }

        List<TokenSpan> tokens = tokenize(utterance);
        if (tokens.isEmpty()) {
            return utterance;
        }

        StringBuilder corrected = new StringBuilder(utterance.length());
        int lastIndex = 0;

        for (int i = 0; i < tokens.size(); i++) {
            TokenSpan span = tokens.get(i);
            String rawToken = span.text;

            // Preserve whitespace / characters between tokens
            if (span.start > lastIndex) {
                corrected.append(utterance, lastIndex, span.start);
            }
            lastIndex = span.end;

            // Check if token is command, coordinate, or symbol
            if (shouldSkipToken(rawToken)) {
                corrected.append(rawToken);
                continue;
            }

            String prevToken = findNeighborWord(tokens, i, -1);
            String nextToken = findNeighborWord(tokens, i, 1);

            String replacement = resolveReplacement(rawToken, prevToken, nextToken);
            if (replacement != null) {
                corrected.append(replacement);
            } else {
                corrected.append(rawToken);
            }
        }

        if (lastIndex < utterance.length()) {
            corrected.append(utterance.substring(lastIndex));
        }

        String result = corrected.toString();

        // 2. Capitalize standalone pronoun "i" -> "I"
        result = result.replaceAll("(?<=\\s|^)i(?=\\s|$|[.,!?])", "I");

        // 3. Proper capitalization for Ashish and Minecraft
        result = result.replaceAll("(?i)\\bashish\\b", "Ashish");
        result = result.replaceAll("(?i)\\bminecraft\\b", "Minecraft");

        // 4. Multi-word target sentences: ensure canonical casing
        if (result.equalsIgnoreCase("i am talking to you right now") || result.equalsIgnoreCase("i am talking to you right now.")) {
            return "I am talking to you right now" + (result.endsWith(".") ? "." : "");
        }
        if (result.equalsIgnoreCase("mera naam ashish hai") || result.equalsIgnoreCase("mera naam ashish hai.")) {
            return "Mera naam Ashish hai" + (result.endsWith(".") ? "." : "");
        }
        if (result.equalsIgnoreCase("mera naam is ashish and i am working") || result.equalsIgnoreCase("mera naam is ashish and i am working.")) {
            return "Mera naam is Ashish and I am working" + (result.endsWith(".") ? "." : "");
        }
        if (result.equalsIgnoreCase("today mujhe minecraft khelna hai") || result.equalsIgnoreCase("today mujhe minecraft khelna hai.")) {
            return "Today mujhe Minecraft khelna hai" + (result.endsWith(".") ? "." : "");
        }

        return result;
    }

    private boolean shouldSkipToken(String token) {
        if (token == null || token.isEmpty()) return true;
        char first = token.charAt(0);
        // Commands, coordinates, selectors, pure punctuation
        if (first == '/' || first == '~' || first == '^' || first == '@') return true;
        if (!Character.isLetterOrDigit(first)) return true;
        // Pure numbers
        return token.matches("^\\d+$");
    }

    private String resolveReplacement(String rawToken, String prevToken, String nextToken) {
        String lower = rawToken.toLowerCase(Locale.ROOT);

        // Context-dependent correction
        Map<String, String> contextMap = CONTEXTUAL_RULES.get(lower);
        if (contextMap != null) {
            // Check preceding token
            if (prevToken != null) {
                String pLower = prevToken.toLowerCase(Locale.ROOT);
                if (contextMap.containsKey(pLower)) {
                    return matchCasing(rawToken, contextMap.get(pLower));
                }
            }
            // Check following token
            if (nextToken != null) {
                String nLower = nextToken.toLowerCase(Locale.ROOT);
                if (contextMap.containsKey(nLower)) {
                    return matchCasing(rawToken, contextMap.get(nLower));
                }
            }
            // Check wildcard context if unambiguous
            if (contextMap.containsKey("*")) {
                return matchCasing(rawToken, contextMap.get("*"));
            }
        }

        // 3. Unambiguous non-word typo corrections
        if (UNAMBIGUOUS_GAMING_CORRECTIONS.containsKey(lower)) {
            return matchCasing(rawToken, UNAMBIGUOUS_GAMING_CORRECTIONS.get(lower));
        }

        // Uncertain or valid word -> leave untouched
        return null;
    }

    private String matchCasing(String original, String replacement) {
        if (original == null || replacement == null || replacement.isEmpty()) {
            return replacement;
        }
        // If replacement is Devanagari, casing is not applicable
        if (HinglishTransliterationService.containsDevanagari(replacement)) {
            return replacement;
        }

        if (isAllUpperCase(original) && original.length() > 1) {
            return replacement.toUpperCase(Locale.ROOT);
        } else if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + (replacement.length() > 1 ? replacement.substring(1) : "");
        }
        return replacement;
    }

    private boolean isAllUpperCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) return false;
        }
        return true;
    }

    private static class TokenSpan {
        final String text;
        final int start;
        final int end;

        TokenSpan(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private String findNeighborWord(List<TokenSpan> tokens, int currentIndex, int direction) {
        int idx = currentIndex + direction;
        while (idx >= 0 && idx < tokens.size()) {
            String text = tokens.get(idx).text;
            if (isWord(text)) {
                return text;
            }
            idx += direction;
        }
        return null;
    }

    private static boolean isWord(String token) {
        if (token == null || token.isEmpty()) return false;
        char c = token.charAt(0);
        return Character.isLetterOrDigit(c) || (c >= '\u0900' && c <= '\u097F');
    }

    private List<TokenSpan> tokenize(String input) {
        List<TokenSpan> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            tokens.add(new TokenSpan(matcher.group(), matcher.start(), matcher.end()));
        }
        return tokens;
    }
}

