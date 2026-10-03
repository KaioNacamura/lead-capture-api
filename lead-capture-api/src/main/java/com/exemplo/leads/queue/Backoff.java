package com.exemplo.leads.queue;

/** Quanto esperar antes de tentar de novo: 10s, 20s, 40s... até no máximo 1 hora. */
public final class Backoff {

    private static final long BASE_SECONDS = 5;
    private static final long MAX_SECONDS = 3600;

    private Backoff() {
    }

    public static long delaySeconds(int attempts) {
        if (attempts < 1) {
            return 0;
        }
        int exponent = Math.min(attempts, 20);
        return Math.min(BASE_SECONDS * (1L << exponent), MAX_SECONDS);
    }
}
