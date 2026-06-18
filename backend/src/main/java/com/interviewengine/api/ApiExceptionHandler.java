package com.interviewengine.api;

import com.interviewengine.application.InterviewNotFoundException;
import com.interviewengine.rubric.RubricLoadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InterviewNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(InterviewNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RubricLoadException.class)
    public ResponseEntity<Map<String, String>> rubricMissing(RubricLoadException e) {
        // Невалидный/несуществующий рубрикатор — это клиентская ошибка
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
