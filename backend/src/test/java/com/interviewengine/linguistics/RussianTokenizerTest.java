package com.interviewengine.linguistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RussianTokenizerTest {

    @Test
    void пустойТекстДаётПустойСписок() {
        assertTrue(RussianTokenizer.tokenize(null).isEmpty());
        assertTrue(RussianTokenizer.tokenize("").isEmpty());
        assertTrue(RussianTokenizer.tokenize("   ").isEmpty());
    }

    @Test
    void приводитКНижнемуРегиструИИгнорируетПунктуацию() {
        List<String> tokens = RussianTokenizer.tokenize("Я переписал сервис, и всё.");
        assertEquals(List.of("я", "переписал", "сервис", "и", "всё"), tokens);
    }

    @Test
    void сохраняетДефисВнутриСлова() {
        List<String> tokens = RussianTokenizer.tokenize("По-моему, это какое-то э-э решение");
        assertTrue(tokens.contains("по-моему"));
        assertTrue(tokens.contains("какое-то"));
        assertTrue(tokens.contains("э-э"));
    }

    @Test
    void числаОстаютсяТокенами() {
        List<String> tokens = RussianTokenizer.tokenize("Снизил с 200 мс до 50 мс");
        assertTrue(tokens.contains("200"));
        assertTrue(tokens.contains("50"));
        assertTrue(tokens.contains("мс"));
    }
}
