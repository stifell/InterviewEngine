package com.interviewengine.linguistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Юникод-токенизатор русского текста.
 * <p>Сохраняет дефис внутри слова (например, «по-моему», «э-э»), всё приводит
 * к нижнему регистру. Знаки препинания и прочие неалфавитные символы — разделители.
 */
public final class RussianTokenizer {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{Nd}](?:[\\p{L}\\p{Nd}-]*[\\p{L}\\p{Nd}])?");

    private RussianTokenizer() {
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher m = TOKEN.matcher(lower);
        List<String> tokens = new ArrayList<>();
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }
}
