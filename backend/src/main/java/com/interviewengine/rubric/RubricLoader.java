package com.interviewengine.rubric;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.interviewengine.domain.Rubric;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class RubricLoader {

    private static final String CLASSPATH_PREFIX = "/rubrics/";
    private static final String LIST_PATTERN = "classpath:/rubrics/*.yaml";

    private final ObjectMapper mapper;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public RubricLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.findAndRegisterModules();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * Возвращает id позиций (имена YAML-файлов без расширения), доступных в classpath.
     * Используется для эндпоинта {@code GET /api/rubrics}.
     */
    public List<String> listPositions() {
        try {
            Resource[] resources = resourceResolver.getResources(LIST_PATTERN);
            List<String> positions = new ArrayList<>();
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name != null && name.endsWith(".yaml")) {
                    positions.add(name.substring(0, name.length() - ".yaml".length()));
                }
            }
            return positions.stream().sorted().toList();
        } catch (IOException e) {
            throw new RubricLoadException("Не удалось перечислить рубрикаторы в classpath", e);
        }
    }

    public Rubric loadByPosition(String position) {
        String path = CLASSPATH_PREFIX + position + ".yaml";
        try (InputStream is = RubricLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new RubricLoadException("Рубрикатор не найден в classpath: " + path);
            }
            return loadFromStream(is, path);
        } catch (IOException e) {
            throw new RubricLoadException("Ошибка чтения рубрикатора " + path, e);
        }
    }

    public Rubric loadFromStream(InputStream is, String sourceHint) {
        try {
            Rubric rubric = mapper.readValue(is, Rubric.class);
            new RubricValidator(rubric).validate();
            return rubric;
        } catch (IOException e) {
            throw new RubricLoadException("Не удалось распарсить YAML рубрикатора: " + sourceHint, e);
        }
    }
}
