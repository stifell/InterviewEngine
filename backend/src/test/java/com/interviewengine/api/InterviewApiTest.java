package com.interviewengine.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewengine.application.InterviewService;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.InterviewStatus;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.evaluation.LlmJudge;
import com.interviewengine.linguistics.LinguisticFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной API-тест: POST /interviews → POST /evaluate → ждём DONE → GET /result.
 * LLM подменён на детерминированный фейк через {@link TestConfig}.
 */
@SpringBootTest
@Import(InterviewApiTest.TestConfig.class)
class InterviewApiTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        LlmJudge fakeLlmJudge() {
            return (Indicator indicator, String answer, LinguisticFeatures features, ProsodicFeatures prosody) -> {
                boolean strong = answer != null && answer.toLowerCase().contains("я");
                int score = strong ? 5 : 2;
                return new IndicatorEvaluation(
                        indicator.id(),
                        score,
                        score >= indicator.acceptableFrom(),
                        strong ? "цитата с «я»" : "слабый ответ",
                        strong ? "сильный ответ" : "недостаточно личного вклада"
                );
            };
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InterviewService interviewService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void полныйСценарий_создание_оценка_получениеРезультата() throws Exception {
        MockMvc mvc = mvc();

        // 1. POST /api/interviews — создаём интервью с сильным транскриптом
        String requestJson = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("position", "senior-go-developer");
            put("segments", strongCandidateSegments());
        }});

        MvcResult createResult = mvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.position").value("senior-go-developer"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());

        // 2. GET /api/interviews/{id} — статус и транскрипт доступны
        mvc.perform(get("/api/interviews/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.segments.length()").value(strongCandidateSegments().size()));

        // 3. GET /result до оценки → 409
        mvc.perform(get("/api/interviews/{id}/result", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 4. POST /evaluate — запускаем оценку
        mvc.perform(post("/api/interviews/{id}/evaluate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(id.toString()))
                .andExpect(jsonPath("$.interviewId").value(id.toString()));

        // 5. Ждём DONE (async-исполнитель в проде, но в тестах быстро доходит)
        awaitStatus(id, InterviewStatus.DONE, 5_000);

        // 6. GET /result — Scorecard + InterviewerEvaluation
        mvc.perform(get("/api/interviews/{id}/result", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("senior-go-developer"))
                .andExpect(jsonPath("$.scorecard.recommendation").value("STRONG_HIRE"))
                .andExpect(jsonPath("$.scorecard.overallScore").value(5.0))
                .andExpect(jsonPath("$.scorecard.competencies.length()").value(4))
                .andExpect(jsonPath("$.interviewerEvaluation.probeCoverage").value(1.0));
    }

    @Test
    void createПриНевалидномЗапросеДаёт400() throws Exception {
        MockMvc mvc = mvc();
        // пустые segments → @NotEmpty
        String body = """
                { "position": "senior-go-developer", "segments": [] }
                """;
        mvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getНесуществующегоИнтервьюДаёт404() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(get("/api/interviews/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rubricsList_возвращаетSeniorGo() throws Exception {
        mvc().perform(get("/api/rubrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // список может содержать несколько рубрикаторов (порядок алфавитный),
                // проверяем только наличие обоих нужных позиций
                .andExpect(jsonPath("$[?(@ == 'senior-go-developer')]").exists())
                .andExpect(jsonPath("$[?(@ == 'senior-go-architect')]").exists());
    }

    @Test
    void rubricDetail_возвращаетПолныйРубрикатор() throws Exception {
        mvc().perform(get("/api/rubrics/{position}", "senior-go-developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("Senior Go Developer"))
                .andExpect(jsonPath("$.blocks.length()").value(4))
                // 4 исходных компетенции + новый просодический домен «Уверенность и беглость речи»
                .andExpect(jsonPath("$.competencies.length()").value(5));
    }

    @Test
    void несуществующийРубрикатор_даёт400() throws Exception {
        mvc().perform(get("/api/rubrics/{position}", "несуществующий"))
                .andExpect(status().isBadRequest());
    }

    private void awaitStatus(UUID id, InterviewStatus expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            InterviewStatus current = interviewService.findById(id).getStatus();
            if (current == expected) return;
            if (current == InterviewStatus.FAILED) {
                throw new AssertionError("Оценка интервью " + id + " упала с FAILED: "
                        + interviewService.findById(id).getErrorMessage());
            }
            Thread.sleep(50);
        }
        InterviewStatus current = interviewService.findById(id).getStatus();
        throw new AssertionError("Не дождались статуса " + expected + " за " + timeoutMs + " мс, текущий = " + current);
    }

    private static List<TranscriptSegment> strongCandidateSegments() {
        return List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block1", "Расскажите коротко."),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "Я работаю на Go 6 лет, я писал биллинг."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block1", "Ваша роль?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "Я был техлидом."),

                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2", "Сложная задача?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "Я переписал обработчик заказов."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2", "Ситуация?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "Я столкнулся с p99 850 мс."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2", "Что делали вы?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "Я профилировал, я переписал на пайплайн."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2", "Метрики?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "Я снизил p99 с 850 до 120 мс."),

                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block3", "Каналы?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block3", "Я часто использую буферизованные."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block3", "Небуферизованный?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block3", "Я объясню: писатель заблокируется."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block3", "Select?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block3", "Я применяю default для polling."),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block3", "Гонки?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block3", "Я ловил гонку через -race."),

                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block4", "Вопросы?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block4", "Я хотел бы узнать про команду.")
        );
    }
}
