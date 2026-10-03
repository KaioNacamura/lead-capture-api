package com.exemplo.leads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.exemplo.leads.worker.IngestionWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Sobe um PostgreSQL de verdade num container e testa o caminho completo do lead. */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.worker.enabled=false", "app.worker.max-attempts=2"})
class CaptureFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String KEY_A = "demo-key-org-a";
    private static final String KEY_B = "demo-key-org-b";

    @Autowired
    TestRestTemplate http;

    @Autowired
    IngestionWorker worker;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limparBanco() {
        jdbc.execute("TRUNCATE leads, ingestion_events RESTART IDENTITY");
    }

    @Test
    void leadEntraNaFilaEOWorkerGrava() {
        ResponseEntity<Map> resposta = capturar(KEY_A, "evt-1", "ana@exemplo.com");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resposta.getBody()).containsEntry("status", "queued").containsEntry("duplicate", false);

        assertThat(contar("leads")).isZero();
        worker.processOnce();
        assertThat(contar("leads")).isEqualTo(1);
    }

    @Test
    void mesmoEventIdNaoDuplica() {
        capturar(KEY_A, "evt-1", "ana@exemplo.com");
        ResponseEntity<Map> segunda = capturar(KEY_A, "evt-1", "ana@exemplo.com");

        assertThat(segunda.getBody()).containsEntry("duplicate", true);
        worker.processOnce();
        assertThat(contar("leads")).isEqualTo(1);
    }

    @Test
    void chaveErradaRecebe401() {
        ResponseEntity<Map> resposta = capturar("chave-que-nao-existe", "evt-1", "ana@exemplo.com");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(contar("ingestion_events")).isZero();
    }

    @Test
    void emailInvalidoRecebe400() {
        ResponseEntity<Map> resposta = capturar(KEY_A, "evt-1", "isso-nao-e-email");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("fields")).isEqualTo(List.of("email"));
    }

    @Test
    void cadaEmpresaSoVeOsSeusLeads() {
        capturar(KEY_A, "evt-a", "cliente-a@exemplo.com");
        capturar(KEY_B, "evt-b", "cliente-b@exemplo.com");
        worker.processOnce();

        ResponseEntity<List> leadsDaA = http.exchange("/leads", HttpMethod.GET,
                new HttpEntity<>(cabecalhos(KEY_A)), List.class);

        assertThat(leadsDaA.getBody()).hasSize(1);
        assertThat(leadsDaA.getBody().get(0).toString()).contains("cliente-a@exemplo.com");
    }

    @Test
    void eventoQueFalhaTentaDeNovoEDepoisVaiParaDead() {
        // Evento quebrado colocado direto na fila, sem passar pela validação da API.
        jdbc.update("""
                INSERT INTO ingestion_events (organization_id, event_id, payload)
                VALUES ('11111111-1111-1111-1111-111111111111', 'evt-quebrado', '{"name": "Sem email"}')
                """);

        worker.processOnce();
        assertThat(status("evt-quebrado")).isEqualTo("pending");

        // Libera a próxima tentativa sem esperar o backoff.
        jdbc.update("UPDATE ingestion_events SET available_at = now()");
        worker.processOnce();
        assertThat(status("evt-quebrado")).isEqualTo("dead");
        assertThat(contar("leads")).isZero();
    }

    private ResponseEntity<Map> capturar(String chave, String eventId, String email) {
        Map<String, String> corpo = Map.of("eventId", eventId, "email", email, "name", "Teste", "source", "site");
        return http.postForEntity("/capture", new HttpEntity<>(corpo, cabecalhos(chave)), Map.class);
    }

    private HttpHeaders cabecalhos(String chave) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", chave);
        return headers;
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class);
    }

    private String status(String eventId) {
        return jdbc.queryForObject("SELECT status FROM ingestion_events WHERE event_id = ?", String.class, eventId);
    }
}
