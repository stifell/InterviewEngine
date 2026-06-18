package com.interviewengine.api;

import com.interviewengine.domain.Rubric;
import com.interviewengine.rubric.RubricLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rubrics")
public class RubricController {

    private final RubricLoader rubricLoader;

    public RubricController(RubricLoader rubricLoader) {
        this.rubricLoader = rubricLoader;
    }

    @GetMapping
    public List<String> list() {
        return rubricLoader.listPositions();
    }

    @GetMapping("/{position}")
    public Rubric get(@PathVariable String position) {
        return rubricLoader.loadByPosition(position);
    }
}
