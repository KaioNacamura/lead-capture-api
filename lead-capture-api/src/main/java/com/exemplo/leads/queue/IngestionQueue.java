package com.exemplo.leads.queue;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Fila durável guardada numa tabela do PostgreSQL. */
@Repository
public class IngestionQueue {

    private final JdbcTemplate jdbc;

    public IngestionQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Retorna false quando o mesmo eventId já tinha chegado antes para essa empresa. */
    public boolean enqueue(UUID organizationId, String eventId, String payloadJson) {
        int inserted = jdbc.update("""
                INSERT INTO ingestion_events (organization_id, event_id, payload)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (organization_id, event_id) DO NOTHING
                """, organizationId, eventId, payloadJson);
        return inserted == 1;
    }

    /**
     * Pega um lote de eventos para processar.
     * FOR UPDATE SKIP LOCKED deixa dois workers rodarem juntos sem pegar o mesmo evento.
     * Evento preso em "processing" há mais de 5 minutos (worker caiu no meio) volta para o lote.
     */
    public List<IngestionEvent> claimBatch(int limit) {
        return jdbc.query("""
                UPDATE ingestion_events
                SET status = 'processing', locked_at = now(), attempts = attempts + 1
                WHERE id IN (
                    SELECT id FROM ingestion_events
                    WHERE (status = 'pending' AND available_at <= now())
                       OR (status = 'processing' AND locked_at < now() - interval '5 minutes')
                    ORDER BY id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                RETURNING id, organization_id, event_id, payload::text AS payload, attempts
                """,
                (rs, i) -> new IngestionEvent(
                        rs.getLong("id"),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("event_id"),
                        rs.getString("payload"),
                        rs.getInt("attempts")),
                limit);
    }

    public void markDone(long id) {
        jdbc.update("""
                UPDATE ingestion_events
                SET status = 'done', processed_at = now(), locked_at = NULL, last_error = NULL
                WHERE id = ?
                """, id);
    }

    /** Volta para a fila com espera crescente, ou vai para "dead" depois do limite de tentativas. */
    public void markFailed(long id, int attempts, String error, int maxAttempts) {
        String message = error == null ? "erro sem mensagem" : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            jdbc.update("""
                    UPDATE ingestion_events
                    SET status = 'dead', locked_at = NULL, last_error = ?
                    WHERE id = ?
                    """, message, id);
            return;
        }
        jdbc.update("""
                UPDATE ingestion_events
                SET status = 'pending', locked_at = NULL, last_error = ?,
                    available_at = now() + (? * interval '1 second')
                WHERE id = ?
                """, message, Backoff.delaySeconds(attempts), id);
    }
}
