package com.interviewengine.linguistics;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Доля ожидаемых доменных терминов индикатора, которые встретились в ответе.
 * <p>Многословные термины (например, «гонка данных») ищутся как подстрока с
 * проверкой границ-слов; однословные — по множеству токенов.
 * Результат в {@code [0,1]}; если ожидаемых терминов нет — возвращается 1.0.
 */
public final class TermCoverage {

    private TermCoverage() {
    }

    public static double compute(String text, List<String> expectedTerms) {
        if (expectedTerms == null || expectedTerms.isEmpty()) {
            return 1.0;
        }
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        Set<String> tokens = new HashSet<>(RussianTokenizer.tokenize(text));
        String padded = padForPhraseSearch(text);

        int matched = 0;
        for (String term : expectedTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String norm = term.toLowerCase(Locale.ROOT).trim();
            boolean found = norm.contains(" ")
                    ? padded.contains(" " + norm + " ")
                    : tokens.contains(norm);
            if (found) {
                matched++;
            }
        }
        return (double) matched / expectedTerms.size();
    }

    private static String padForPhraseSearch(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length() + 2);
        sb.append(' ');
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-') {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        sb.append(' ');
        return sb.toString().replaceAll("\\s+", " ");
    }
}
