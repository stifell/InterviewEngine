package com.interviewengine.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Получает список доступных Gemini-моделей через {@code GET /v1beta/models}.
 *
 * <p>Остатки квот (RPD used / remaining) Google не отдаёт через этот API —
 * они доступны только в AI Studio / Cloud Console UI. Отдаём лишь то, что
 * реально возвращает API: id, displayName, inputTokenLimit.
 *
 * <p>Результат кэшируется на время жизни процесса; модели меняются редко,
 * перезапуск приложения обновит список.
 */
@Service
public class GeminiModelDiscovery {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelDiscovery.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String GENERATE_CONTENT = "generateContent";

    private final String apiKey;
    private final RestClient restClient;

    /** Кэш: null = ещё не загружали, пустой список = загрузили, но ничего не нашли. */
    private final AtomicReference<List<ModelEntry>> cache = new AtomicReference<>(null);

    public GeminiModelDiscovery(
            @Value("${spring.ai.google.genai.api-key:}") String apiKey
    ) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    /**
     * Возвращает список text-generation моделей (поддерживают {@code generateContent}).
     * Результат кэшируется до перезапуска процесса. При ошибке возвращает пустой список
     * — SettingsController упадёт на hardcoded-список как запасной вариант.
     */
    public List<ModelEntry> listTextModels() {
        List<ModelEntry> cached = cache.get();
        if (cached != null) {
            return cached;
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("dummy")) {
            log.warn("GeminiModelDiscovery: API-ключ не задан — список моделей недоступен");
            cache.set(List.of());
            return List.of();
        }

        try {
            List<ModelEntry> result = fetchAllPages();
            log.info("GeminiModelDiscovery: найдено {} text-generation моделей", result.size());
            cache.set(result);
            return result;
        } catch (Exception e) {
            log.warn("GeminiModelDiscovery: не удалось получить список моделей — {}", e.getMessage());
            cache.set(List.of());
            return List.of();
        }
    }

    /** Инвалидирует кэш — следующий вызов {@link #listTextModels()} перезапросит API. */
    public void invalidateCache() {
        cache.set(null);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<ModelEntry> fetchAllPages() {
        List<ModelEntry> result = new ArrayList<>();
        String pageToken = null;

        do {
            String uri = pageToken == null
                    ? "/v1beta/models?key=" + apiKey + "&pageSize=50"
                    : "/v1beta/models?key=" + apiKey + "&pageSize=50&pageToken=" + pageToken;

            ModelsPage page = restClient.get().uri(uri).retrieve().body(ModelsPage.class);
            if (page == null || page.models() == null) break;

            for (RawModel m : page.models()) {
                if (m.supportedGenerationMethods() != null
                        && m.supportedGenerationMethods().contains(GENERATE_CONTENT)
                        && m.name() != null) {
                    String id = m.name().startsWith("models/")
                            ? m.name().substring("models/".length())
                            : m.name();
                    String display = m.displayName() != null ? m.displayName() : id;
                    result.add(new ModelEntry(id, display, m.inputTokenLimit()));
                }
            }
            pageToken = page.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        result.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return result;
    }

    // ── DTO для десериализации ответа API ─────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelsPage(List<RawModel> models, String nextPageToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawModel(
            String name,
            String displayName,
            List<String> supportedGenerationMethods,
            int inputTokenLimit
    ) {}

    /** Выходная модель — то, что реально знает API. */
    public record ModelEntry(String id, String displayName, int inputTokenLimit) {}
}
