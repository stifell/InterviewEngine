package com.interviewengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Хранит текущее имя Gemini-модели, выбранной для LLM-оценки.
 * Изменяется в рантайме через {@code PUT /api/settings/model} без перезапуска.
 *
 * <p>Дефолтное значение берётся из {@code spring.ai.google.genai.chat.options.model}
 * (то, что прописано в application.yaml). Если нужно переключиться на модель с
 * большим лимитом (например, gemini-2.0-flash-lite с 1500 RPD вместо 20) —
 * просто выбрать в UI или вызвать API.
 */
@Component
public class ModelSelector {

    private static final Logger log = LoggerFactory.getLogger(ModelSelector.class);

    private volatile String currentModel;

    public ModelSelector(
            @Value("${spring.ai.google.genai.chat.options.model:gemini-2.5-flash}") String defaultModel
    ) {
        this.currentModel = defaultModel;
        log.info("LLM-модель по умолчанию: {}", defaultModel);
    }

    public String getModel() {
        return currentModel;
    }

    public void setModel(String model) {
        log.info("LLM-модель изменена: {} → {}", currentModel, model);
        this.currentModel = model;
    }
}
