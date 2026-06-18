package com.interviewengine.api;

import com.interviewengine.api.dto.CreateFromAudioResponse;
import com.interviewengine.api.dto.CreateInterviewRequest;
import com.interviewengine.api.dto.EvaluationStartedResponse;
import com.interviewengine.api.dto.InterviewView;
import com.interviewengine.application.InterviewService;
import com.interviewengine.domain.Transcript;
import com.interviewengine.persistence.InterviewEntity;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * REST API интервью (§9 CLAUDE.md).
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    public ResponseEntity<InterviewView> create(@Valid @RequestBody CreateInterviewRequest request) {
        UUID id = interviewService.create(request.position(), new Transcript(request.segments()));
        InterviewView view = toView(interviewService.findById(id));
        URI location = UriComponentsBuilder.fromPath("/api/interviews/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(view);
    }

    @GetMapping("/{id}")
    public InterviewView get(@PathVariable UUID id) {
        return toView(interviewService.findById(id));
    }

    /**
     * Создание интервью из аудио/видео-файла: запускает асинхронную цепочку
     * транскрипция → оценка. Клиент потом поллит {@link #get} и {@link #getResult}.
     */
    @PostMapping(value = "/from-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateFromAudioResponse> createFromAudio(
            @RequestParam("position") String position,
            @RequestParam("media") MultipartFile media
    ) throws IOException {
        if (media.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String contentType = media.getContentType() != null ? media.getContentType() : "audio/mpeg";
        UUID id = interviewService.createFromMedia(position, media.getBytes(), contentType);
        InterviewEntity entity = interviewService.findById(id);
        URI location = UriComponentsBuilder.fromPath("/api/interviews/{id}").buildAndExpand(id).toUri();
        return ResponseEntity
                .created(location)
                .body(new CreateFromAudioResponse(id, entity.getStatus()));
    }

    @PostMapping("/{id}/evaluate")
    public EvaluationStartedResponse evaluate(@PathVariable UUID id) {
        InterviewEntity entity = interviewService.startEvaluation(id);
        return new EvaluationStartedResponse(id, id, entity.getStatus());
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<?> getResult(@PathVariable UUID id) {
        InterviewEntity entity = interviewService.findById(id);
        return interviewService.getResult(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    // Map.of не принимает null значения — собираем явно
                    java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
                    body.put("status", entity.getStatus().name());
                    body.put("message", "Результат ещё не готов. Текущий статус: " + entity.getStatus());
                    if (entity.getErrorMessage() != null) {
                        body.put("errorMessage", entity.getErrorMessage());
                    }
                    return ResponseEntity.status(409).body(body);
                });
    }

    private InterviewView toView(InterviewEntity entity) {
        Transcript transcript = interviewService.getTranscript(entity.getId());
        return new InterviewView(
                entity.getId(),
                entity.getPosition(),
                entity.getStatus(),
                transcript.segments(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
