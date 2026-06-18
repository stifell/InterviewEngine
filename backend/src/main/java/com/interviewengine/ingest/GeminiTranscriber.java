package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/**
 * Мультимодальный транскрайбер на Gemini: одним вызовом получает аудио, делает ASR,
 * диаризацию и возвращает структурированный список сегментов.
 *
 * <p>Это «быстрый старт» по §5.1 CLAUDE.md: разворачивается без отдельного
 * Python-сайдкара, бесплатный тариф AI Studio даёт мультимодальные вызовы. Минусы —
 * у Gemini нет жёстких гарантий точности таймкодов и стабильности кластеров спикеров
 * при длинных записях; для прода используется {@code SidecarTranscriber}.
 *
 * <p>Реализация не загрязняет домен: всё, что вернёт LLM, складывается в record-структуру
 * {@link Result}, которая внутренне используется для маппинга на {@link RawSpeakerSegment}.
 */
@Service
public class GeminiTranscriber implements Transcriber {

    private final ChatClient chatClient;
    private final Resource promptTemplate;

    public GeminiTranscriber(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/transcribe-audio.st") Resource promptTemplate
    ) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = promptTemplate;
    }

    @Override
    public List<RawSpeakerSegment> transcribe(byte[] media, String contentType) {
        MimeType mimeType = parseMimeType(contentType);
        Resource mediaResource = new ByteArrayResource(media) {
            @Override
            public String getFilename() {
                return "media." + mimeType.getSubtype();
            }
        };

        Result result = chatClient.prompt()
                .user(u -> u.text(promptTemplate).media(mimeType, mediaResource))
                .call()
                .entity(Result.class);

        return result == null || result.segments == null ? List.of() : List.copyOf(result.segments);
    }

    private static MimeType parseMimeType(String contentType) {
        try {
            return MimeTypeUtils.parseMimeType(contentType);
        } catch (Exception e) {
            // безопасный fallback — Gemini принимает audio/mpeg для большинства mp3-like
            return MimeTypeUtils.parseMimeType("audio/mpeg");
        }
    }

    /**
     * Структура ответа LLM. Spring AI разворачивает её через нативный
     * {@code .entity()}, поэтому поля должны соответствовать промпту.
     */
    record Result(List<RawSpeakerSegment> segments) {
    }
}
