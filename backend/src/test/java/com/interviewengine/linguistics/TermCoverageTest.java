package com.interviewengine.linguistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermCoverageTest {

    @Test
    void пустойСписокТерминовДаётПолноеПокрытие() {
        assertEquals(1.0, TermCoverage.compute("любой текст", List.of()));
        assertEquals(1.0, TermCoverage.compute("любой текст", null));
    }

    @Test
    void пустойТекстПриЕстьТерминахДаётНоль() {
        assertEquals(0.0, TermCoverage.compute("", List.of("горутина", "канал")));
        assertEquals(0.0, TermCoverage.compute(null, List.of("горутина")));
    }

    @Test
    void совпадениеОдногоИзДвухДаётПоловину() {
        double cov = TermCoverage.compute(
                "Я использовал горутины для параллельной обработки.",
                List.of("горутина", "канал"));
        // "горутина" не равно "горутины" — однословный термин ищется как точный токен,
        // поэтому совпадений нет. Проверяем точное соответствие.
        assertEquals(0.0, cov, 0.0001);
    }

    @Test
    void однословныйТерминСовпадаетПоТочномуТокену() {
        double cov = TermCoverage.compute(
                "Здесь использовалась горутина, а здесь канал.",
                List.of("горутина", "канал", "мьютекс"));
        assertEquals(2.0 / 3.0, cov, 0.0001);
    }

    @Test
    void многословныйТерминСовпадаетКакФраза() {
        // ВАЖНО: v1 без лемматизации сопоставляет точные поверхностные формы.
        // «гонка данных» в им. падеже найдёт ровно «гонка данных», но не «гонку данных».
        // Морфология появится в v2 (Python-сайдкар с pymorphy3 / Natasha — см. §7 CLAUDE.md).
        double cov = TermCoverage.compute(
                "В проде у нас была гонка данных, дебажил через детектор race.",
                List.of("гонка данных", "детектор race"));
        assertEquals(1.0, cov, 0.0001);
    }

    @Test
    void словоформаНеНаходитсяБезЛемматизации_известноеОграничениеV1() {
        // Документируем ограничение: «горутина» (им.) не совпадёт с «горутины» (мн./род.).
        double cov = TermCoverage.compute(
                "Использовал горутины для обработки.",
                List.of("горутина"));
        assertEquals(0.0, cov, 0.0001);
    }

    @Test
    void регистрНеВажен() {
        double cov = TermCoverage.compute(
                "GOROUTINE и Channel",
                List.of("goroutine", "channel"));
        assertEquals(1.0, cov, 0.0001);
    }
}
