package com.exemplo.leads.capture;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaptureRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 120) String name,
        @Size(max = 60) String source) {
}
