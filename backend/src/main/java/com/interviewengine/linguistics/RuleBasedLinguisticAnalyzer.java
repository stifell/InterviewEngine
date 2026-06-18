package com.interviewengine.linguistics;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based анализатор русского ответа: словари + regex.
 * Достаточен для v1; v2 может прислать Python-сайдкар с морфологией.
 */
@Service
public class RuleBasedLinguisticAnalyzer implements LinguisticAnalyzer {

    private static final Set<String> FIRST_PERSON_SINGULAR = Set.of(
            "я", "меня", "мне", "мной", "мною",
            "мой", "моя", "моё", "мое", "мои",
            "моего", "моей", "моему", "моим", "моих", "моими"
    );

    private static final Set<String> FIRST_PERSON_PLURAL = Set.of(
            "мы", "нас", "нам", "нами",
            "наш", "наша", "наше", "наши",
            "нашего", "нашей", "нашему", "нашим", "наших", "нашими"
    );

    private static final List<String> FILLERS = List.of(
            "ну", "вот", "типа", "короче", "значит",
            "это самое", "как бы", "в общем", "то есть", "по сути",
            "так сказать", "как сказать", "э-э", "м-м", "ээ", "мм"
    );

    private static final List<String> HEDGES = List.of(
            "наверное", "наверно", "вроде", "кажется", "возможно",
            "может быть", "по-моему", "скорее всего",
            "как-то", "как-то так", "мне кажется",
            "не уверен", "не уверена", "вроде бы"
    );

    /**
     * Любое число с единицей измерения, процентом или конструкция X → Y / X -> Y / с X до Y.
     */
    private static final Pattern METRIC = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:%|мс|сек|секунд[аы]?|минут[аы]?|час[аов]*|" +
                    "rps|qps|тыс|млн|млрд|мб|гб|кб|raw|раз|x|×|штук|шт)" +
                    "|\\d+\\s*(?:->|→|–>|—>)\\s*\\d+" +
                    "|с\\s+\\d+(?:[.,]\\d+)?\\s+до\\s+\\d+",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public LinguisticFeatures analyze(String answer) {
        List<String> tokens = RussianTokenizer.tokenize(answer);
        int wordCount = tokens.size();

        if (wordCount == 0) {
            return new LinguisticFeatures(0, 0, 0, false, 0.0, 0.0, 0.0, false, 0.0);
        }

        int firstSg = countAny(tokens, FIRST_PERSON_SINGULAR);
        int firstPl = countAny(tokens, FIRST_PERSON_PLURAL);
        boolean hasFirstPerson = (firstSg + firstPl) > 0;
        double ownershipRatio = hasFirstPerson
                ? (double) firstSg / (firstSg + firstPl)
                : 0.0;

        String normalized = padForPhraseSearch(answer);
        int fillers = countPhrases(normalized, FILLERS);
        int hedges = countPhrases(normalized, HEDGES);

        double fillerDensity = density(fillers, wordCount);
        double hedgingDensity = density(hedges, wordCount);

        boolean hasMetrics = METRIC.matcher(answer).find();

        Set<String> unique = new HashSet<>(tokens);
        double lexicalDiversity = (double) unique.size() / wordCount;

        return new LinguisticFeatures(
                wordCount,
                firstSg,
                firstPl,
                hasFirstPerson,
                ownershipRatio,
                fillerDensity,
                hedgingDensity,
                hasMetrics,
                lexicalDiversity
        );
    }

    private static int countAny(List<String> tokens, Set<String> dictionary) {
        int total = 0;
        for (String t : tokens) {
            if (dictionary.contains(t)) {
                total++;
            }
        }
        return total;
    }

    /**
     * Приводит текст к нижнему регистру, заменяет не-буквенные/не-цифровые символы (кроме
     * пробела и дефиса) на пробелы и оборачивает результат пробелами — так многословные
     * фильтры/хеджи можно искать обычным {@code indexOf} с явными разделителями-словами.
     */
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
        // схлопнуть подряд идущие пробелы
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static int countPhrases(String paddedNormalized, List<String> phrases) {
        int total = 0;
        for (String phrase : phrases) {
            String key = " " + phrase.toLowerCase(Locale.ROOT) + " ";
            int idx = 0;
            while ((idx = paddedNormalized.indexOf(key, idx)) != -1) {
                total++;
                idx += key.length() - 1;
            }
        }
        return total;
    }

    private static double density(int count, int wordCount) {
        return wordCount == 0 ? 0.0 : (count * 100.0) / wordCount;
    }
}
