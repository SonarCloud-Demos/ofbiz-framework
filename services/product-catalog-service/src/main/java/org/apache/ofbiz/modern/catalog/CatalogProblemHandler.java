package org.apache.ofbiz.modern.catalog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
final class CatalogProblemHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatus(ResponseStatusException error, HttpServletRequest request) {
        return problem(HttpStatus.valueOf(error.getStatusCode().value()), error.getReason(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalid(ConstraintViolationException error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        problem.setType(URI.create("urn:ofbiz:catalog:" + status.value()));
        problem.setTitle(status.getReasonPhrase());
        String supplied = request.getHeader("X-Correlation-ID");
        String correlationId = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,128}")
                ? supplied : UUID.randomUUID().toString();
        problem.setProperties(Map.of("correlationId", correlationId));
        return problem;
    }
}
