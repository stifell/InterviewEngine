package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Production-grade транскрайбер: HTTP-клиент к Python-сайдкару, который внутри
 * использует GigaAM v3 / Whisper large-v3-turbo + pyannote.audio для русской ASR
 * и диаризации (§5.1 CLAUDE.md).
 *
 * <p>Активируется свойством {@code transcriber.sidecar.url} — если задан URL,
 * этот бин становится {@code @Primary} вместо {@link GeminiTranscriber}. Иначе
 * не подключается, и в системе остаётся только Gemini-реализация.
 *
 * <p>Контракт сайдкара (см. {@code sidecar/README.md}):
 * <ul>
 *   <li>{@code POST /transcribe} — multipart {@code file=<audio>}</li>
 *   <li>Ответ: JSON {@code {"segments":[{"rawSpeakerId":..., "startMs":..., "endMs":..., "text":...}, ...]}}</li>
 * </ul>
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "transcriber.sidecar", name = "url")
public class SidecarTranscriber implements Transcriber {

    private final RestClient restClient;

    public SidecarTranscriber(@Value("${transcriber.sidecar.url}") String baseUrl) {
        // Таймаут 15 минут: faster-whisper на CPU может транскрибировать долго
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(900_000); // 15 мин
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Возвращает имя файла с расширением, исходя из content-type. */
    private static String guessFilename(String contentType) {
        if (contentType == null) return "audio.bin";
        return switch (contentType.toLowerCase()) {
            case "audio/mpeg", "audio/mp3"          -> "audio.mp3";
            case "audio/mp4", "audio/m4a",
                 "video/mp4"                        -> "audio.m4a";
            case "audio/wav", "audio/wave"          -> "audio.wav";
            case "audio/ogg"                        -> "audio.ogg";
            case "audio/webm", "video/webm"         -> "audio.webm";
            case "audio/flac"                       -> "audio.flac";
            default                                 -> "audio.bin";
        };
    }

    @Override
    public List<RawSpeakerSegment> transcribe(byte[] media, String contentType) {
        // Явно оборачиваем в HttpEntity с Content-Type, чтобы FastAPI распознал
        // часть как UploadFile, а не как строковое поле (иначе 422 "field required").
        String filename = guessFilename(contentType);
        ByteArrayResource resource = new ByteArrayResource(media) {
            @Override public String getFilename() { return filename; }
        };
        org.springframework.http.HttpHeaders partHeaders = new org.springframework.http.HttpHeaders();
        partHeaders.setContentType(contentType != null && !contentType.isBlank()
                ? org.springframework.http.MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM);
        org.springframework.http.HttpEntity<ByteArrayResource> filePart =
                new org.springframework.http.HttpEntity<>(resource, partHeaders);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", filePart);

        Response response = restClient.post()
                .uri("/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Response.class);

        return response == null || response.segments == null ? List.of() : List.copyOf(response.segments);
    }

    record Response(List<RawSpeakerSegment> segments) {
    }
}
