package com.exemplo.leads.worker;

import com.exemplo.leads.queue.IngestionEvent;
import com.exemplo.leads.queue.IngestionQueue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LeadWriter {

    private final JdbcTemplate jdbc;
    private final IngestionQueue queue;
    private final ObjectMapper mapper;

    public LeadWriter(JdbcTemplate jdbc, IngestionQueue queue, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.queue = queue;
        this.mapper = mapper;
    }

    /** Grava o lead e marca o evento como feito na mesma transação: ou acontecem os dois, ou nenhum. */
    @Transactional(rollbackFor = Exception.class)
    public void write(IngestionEvent event) throws JsonProcessingException {
        JsonNode payload = mapper.readTree(event.payload());
        String email = text(payload, "email");
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("evento sem e-mail válido");
        }

        jdbc.update("""
                INSERT INTO leads (organization_id, ingestion_event_id, email, name, source)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (ingestion_event_id) DO NOTHING
                """,
                event.organizationId(), event.id(), email.toLowerCase(), text(payload, "name"), text(payload, "source"));
        queue.markDone(event.id());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
