package com.voice2text.transcription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 * - Integrates user-confirmed corrections via PersonalizedLearningStore.
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

        // "weesy" / "visi" -> "VC" in context of voice/discord/join
        addContextRule("weesy", "in", "VC");
        addContextRule("weesy", "mein", "VC");
        addContextRule("weesy", "में", "VC");
        addContextRule("weesy", "join", "VC");
        addContextRule("visi", "in", "VC");
        addContextRule("visi", "mein", "VC");
        addContextRule("visi", "में", "VC");
        addContextRule("visi", "join", "VC");
    }

    private static void addContextRule(String mistake, String neighborContext, String replacement) {
        String m = mistake.toLowerCase(Locale.ROOT);
        String c = neighborContext.toLowerCase(Locale.ROOT);
        CONTEXTUAL_RULES.computeIfAbsent(m, k -> new HashMap<>()).put(c, replacement);
    }

    private final PersonalizedLearningStore learningStore;

    public TranscriptionCorrectionService(PersonalizedLearningStore learningStore) {
        this.learningStore = learningStore;
    }

    public TranscriptionCorrectionService() {
        this(null);
    }

    public PersonalizedLearningStore getLearningStore() {
        return learningStore;
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

            String prevToken = (i > 0) ? tokens.get(i - 1).text : null;
            String nextToken = (i < tokens.size() - 1) ? tokens.get(i + 1).text : null;

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

        return corrected.toString();
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

        // 1. Personalized learned store lookup (highest priority)
        if (learningStore != null) {
            String learned = learningStore.lookupCorrection(rawToken, prevToken, nextToken);
            if (learned != null) {
                return matchCasing(rawToken, learned);
            }
        }

        // 2. Context-dependent correction
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

    private List<TokenSpan> tokenize(String input) {
        List<TokenSpan> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            tokens.add(new TokenSpan(matcher.group(), matcher.start(), matcher.end()));
        }
        return tokens;
    }
}

