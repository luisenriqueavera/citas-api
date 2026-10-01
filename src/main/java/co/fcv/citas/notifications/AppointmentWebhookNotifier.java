package co.fcv.citas.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outbound notification stub for S5/S6 (n8n). Disabled unless APPOINTMENT_WEBHOOK_URL is set;
 * never tested against a real n8n instance (none available) and never allowed to fail the
 * caller's transaction.
 */
@Component
public class AppointmentWebhookNotifier {
    private static final Logger log = LoggerFactory.getLogger(AppointmentWebhookNotifier.class);
    private final String url;
    private final RestClient restClient = RestClient.create();

    public AppointmentWebhookNotifier(@Value("${app.webhooks.appointment-status-url:}") String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return url != null && !url.isBlank();
    }

    public void notifyStatusChange(AppointmentStatusEvent event) {
        if (url == null || url.isBlank()) {
            log.debug("Appointment webhook disabled; skipping appointmentId={}", event.appointmentId());
            return;
        }
        try {
            restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(event).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Appointment webhook notification failed for appointmentId={}: {}", event.appointmentId(), ex.getMessage());
        }
    }

    public record AppointmentStatusEvent(Long appointmentId, String previousStatus, String newStatus,
                                          String changeSource, String reason, String occurredAt,
                                          String patientEmail, String patientName, String professionalName,
                                          String specialtyName, String scheduledStartAt) { }
}
