package com.exemplo.leads.capture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.exemplo.leads.queue.IngestionQueue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaptureController {

    private final ApiKeyAuthenticator authenticator;
    private final IngestionQueue queue;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public CaptureController(ApiKeyAuthenticator authenticator, IngestionQueue queue,
                             Validator validator, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.authenticator = authenticator;
        this.queue = queue;
        this.validator = validator;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    /**
     * Recebe o lead e só coloca na fila. Por isso a resposta é 202 "queued"
     * e não "lead criado": quem grava o lead é o worker.
     */
    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> capture(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody CaptureRequest request) throws JsonProcessingException {

        // Primeiro a chave, depois o conteúdo: quem não tem chave não descobre nada sobre a validação.
        Optional<UUID> organization = authenticator.organizationFor(apiKey);
        if (organization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "chave de API inválida"));
        }

        Set<ConstraintViolation<CaptureRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<String> fields = violations.stream()
                    .map(v -> v.getPropertyPath().toString())
                    .sorted()
                    .toList();
            return ResponseEntity.badRequest().body(Map.of("error", "dados inválidos", "fields", fields));
        }

        boolean created = queue.enqueue(organization.get(), request.eventId(), mapper.writeValueAsString(request));
        return ResponseEntity.accepted().body(Map.of("status", "queued", "duplicate", !created));
    }

    /** Lista os últimos leads da empresa dona da chave. Cada empresa só vê os seus. */
    @GetMapping("/leads")
    public ResponseEntity<Object> leads(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        Optional<UUID> organization = authenticator.organizationFor(apiKey);
        if (organization.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "chave de API inválida"));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT email, name, source, created_at
                FROM leads
                WHERE organization_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """, organization.get());
        return ResponseEntity.ok(rows);
    }
}
