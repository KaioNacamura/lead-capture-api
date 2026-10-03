package com.exemplo.leads;

import static org.assertj.core.api.Assertions.assertThat;

import com.exemplo.leads.queue.Backoff;
import org.junit.jupiter.api.Test;

class BackoffTest {

    @Test
    void esperaDobraACadaTentativa() {
        assertThat(Backoff.delaySeconds(1)).isEqualTo(10);
        assertThat(Backoff.delaySeconds(2)).isEqualTo(20);
        assertThat(Backoff.delaySeconds(3)).isEqualTo(40);
    }

    @Test
    void esperaTemTeto() {
        assertThat(Backoff.delaySeconds(30)).isEqualTo(3600);
    }

    @Test
    void semTentativaNaoEspera() {
        assertThat(Backoff.delaySeconds(0)).isZero();
    }
}
