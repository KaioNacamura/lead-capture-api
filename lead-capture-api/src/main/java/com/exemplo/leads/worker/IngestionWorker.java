package com.exemplo.leads.worker;

import java.util.List;

import com.exemplo.leads.queue.IngestionEvent;
import com.exemplo.leads.queue.IngestionQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionQueue queue;
    private final LeadWriter writer;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;

    public IngestionWorker(IngestionQueue queue, LeadWriter writer,
                           @Value("${app.worker.enabled:true}") boolean enabled,
                           @Value("${app.worker.batch-size:20}") int batchSize,
                           @Value("${app.worker.max-attempts:5}") int maxAttempts) {
        this.queue = queue;
        this.writer = writer;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.worker.delay-ms:1000}")
    public void tick() {
        if (enabled) {
            processOnce();
        }
    }

    /** Processa um lote e devolve quantos eventos pegou. Público para os testes chamarem direto. */
    public int processOnce() {
        List<IngestionEvent> batch = queue.claimBatch(batchSize);
        for (IngestionEvent event : batch) {
            try {
                writer.write(event);
            } catch (Exception e) {
                log.warn("Falha no evento {} (tentativa {}): {}", event.id(), event.attempts(), e.getMessage());
                queue.markFailed(event.id(), event.attempts(), e.getMessage(), maxAttempts);
            }
        }
        return batch.size();
    }
}
