package co.fcv.citas.notifications;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;

class AppointmentWebhookNotifierTest {

    @Test
    void doesNothingWhenUrlIsNotConfigured() {
        AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier("");
        assertThatCode(() -> notifier.notifyStatusChange(sampleEvent())).doesNotThrowAnyException();
    }

    @Test
    void neverThrowsWhenTheConfiguredUrlIsUnreachable() {
        AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier("http://127.0.0.1:1/unreachable");
        assertThatCode(() -> notifier.notifyStatusChange(sampleEvent())).doesNotThrowAnyException();
    }

    private AppointmentWebhookNotifier.AppointmentStatusEvent sampleEvent() {
        return new AppointmentWebhookNotifier.AppointmentStatusEvent(1L, "APPROVED", "CANCELLED", "USER", null, "2031-01-01T00:00:00Z");
    }
}
