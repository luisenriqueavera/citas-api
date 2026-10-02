package co.fcv.citas.notifications;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AppointmentWebhookNotifierTest {

    @Test
    void doesNothingWhenUrlIsNotConfigured() {
        AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier("", "");
        assertThatCode(() -> notifier.notifyStatusChange(sampleEvent())).doesNotThrowAnyException();
    }

    @Test
    void neverThrowsWhenTheConfiguredUrlIsUnreachable() {
        AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier("http://127.0.0.1:1/unreachable", "");
        assertThatCode(() -> notifier.notifyStatusChange(sampleEvent())).doesNotThrowAnyException();
    }

    @Test
    void sendsTheSharedSecretHeaderWhenConfigured() throws Exception {
        CompletableFuture<String> receivedHeader = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            receivedHeader.complete(exchange.getRequestHeaders().getFirst("X-Webhook-Secret"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier(url, "s3cr3t");
            notifier.notifyStatusChange(sampleEvent());
            assertThat(receivedHeader.get(5, TimeUnit.SECONDS)).isEqualTo("s3cr3t");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void omitsTheSecretHeaderWhenNotConfigured() throws Exception {
        CompletableFuture<Boolean> hadHeader = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hadHeader.complete(exchange.getRequestHeaders().containsKey("X-Webhook-Secret"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            AppointmentWebhookNotifier notifier = new AppointmentWebhookNotifier(url, "");
            notifier.notifyStatusChange(sampleEvent());
            assertThat(hadHeader.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private AppointmentWebhookNotifier.AppointmentStatusEvent sampleEvent() {
        return new AppointmentWebhookNotifier.AppointmentStatusEvent(1L, "APPROVED", "CANCELLED", "USER", null, "2031-01-01T00:00:00Z",
                "patient@example.com", "Jane Doe", "Dr. John Smith", "Cardiología", "2031-01-02T09:00:00");
    }
}
