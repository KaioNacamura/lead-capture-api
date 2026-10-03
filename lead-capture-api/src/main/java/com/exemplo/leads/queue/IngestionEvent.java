package com.exemplo.leads.queue;

import java.util.UUID;

public record IngestionEvent(long id, UUID organizationId, String eventId, String payload, int attempts) {
}
