package com.interviewengine.api;

import com.interviewengine.config.GeminiModelDiscovery;
import com.interviewengine.config.ModelSelector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Настройки приложения в рантайме.
 *
 * <p>Список доступных моделей загружается через {@code GET /v1beta/models} Gemini API —
 * никакого хардкода. Лимиты (RPM/RPD/TPM) API не возвращает: они видны только
 * в AI Studio → Rate Limits. Их нет смысла показывать — они не меняются в рантайме
 * и у каждого аккаунта свои.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final ModelSelector modelSelector;
    private final GeminiModelDiscovery modelDiscovery;

    public SettingsController(ModelSelector modelSelector, GeminiModelDiscovery modelDiscovery) {
        this.modelSelector = modelSelector;
        this.modelDiscovery = modelDiscovery;
    }

    @GetMapping
    public SettingsView get() {
        List<ModelInfo> models = modelDiscovery.listTextModels().stream()
                .map(e -> new ModelInfo(e.id(), e.displayName(), e.inputTokenLimit()))
                .toList();
        return new SettingsView(modelSelector.getModel(), models);
    }

    @PutMapping("/model")
    public ResponseEntity<SettingsView> setModel(@RequestBody Map<String, String> body) {
        String model = body.get("model");
        if (model == null || model.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        modelSelector.setModel(model.trim());
        return ResponseEntity.ok(get());
    }

    /**
     * Инвалидирует кэш списка моделей. Полезно если в аккаунте появились новые
     * модели — после вызова следующий GET /api/settings перезапросит Gemini API.
     */
    @PostMapping("/models/refresh")
    public ResponseEntity<SettingsView> refreshModels() {
        modelDiscovery.invalidateCache();
        return ResponseEntity.ok(get());
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    /**
     * @param id             API-идентификатор модели (то, что передаётся в запросе)
     * @param displayName    Человекочитаемое название из Gemini API
     * @param inputTokenLimit Максимальный контекст в токенах (из API)
     */
    public record ModelInfo(
            String id,
            String displayName,
            int inputTokenLimit
    ) {}

    public record SettingsView(
            String currentModel,
            List<ModelInfo> availableModels
    ) {}
}
