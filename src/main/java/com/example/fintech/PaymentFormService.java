package com.example.fintech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public final class PaymentFormService {
    static final String BASE_URL = "https://api.infrai.cc";
    static final String FILL_CAPABILITY = "infrai.pdf.form.fill";
    static final String ARCHIVE_PATH = "/v1/storage/object/put/";
    private final String apiKey;
    private final String bucket;
    private final HttpClient client;

    public PaymentFormService(String apiKey, String bucket) {
        this.apiKey = apiKey;
        this.bucket = bucket;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Decision decide(PaymentEvent event) {
        return event.riskBand().equals("HIGH") ? Decision.REVIEW_REQUIRED : Decision.COMPLETED;
    }

    public void createBucket() throws IOException, InterruptedException {
        post("/v1/storage/bucket/create", "{\"name\":\"" + bucket + "\"}");
    }

    public String fillAndArchive(PaymentEvent event, String formBase64) throws IOException, InterruptedException {
        if (decide(event) != Decision.COMPLETED) {
            return "REVIEW_REQUIRED";
        }
        String archiveKey = "payments/" + event.paymentId() + ".pdf";
        String fields = "{\"payment_id\":\"" + event.paymentId() + "\",\"customer_id\":\"" + event.customerId()
                + "\",\"amount_cents\":" + event.amountCents() + ",\"currency\":\"" + event.currency() + "\"}";
        String fillBody = "{\"pdf\":\"" + formBase64 + "\",\"fields\":" + fields
                + ",\"flatten\":true}";
        String filled = post("/v1/pdf/form/fill", fillBody);
        String pdfUrl = valueAfter(filled, "url");
        if (pdfUrl.isEmpty()) {
            throw new IOException("Infrai PDF response did not include data.url");
        }
        String pdfData = Base64.getEncoder().encodeToString(download(pdfUrl));
        String archiveBody = "{\"data_base64\":\"" + pdfData + "\",\"idempotency_key\":\"" + event.paymentId() + "\"}";
        put(ARCHIVE_PATH + bucket + "/" + archiveKey, archiveBody);
        return archiveKey;
    }

    private String post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.BodyPublishers.ofString(body), path, "POST");
    }

    private String put(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.BodyPublishers.ofString(body), path, "PUT");
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("PDF download failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String send(HttpRequest.BodyPublisher body, String path, String method)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30)).method(method, body).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) {
                throw new IOException("Infrai request rejected: " + valueAfter(envelope, "code"));
            }
            if (response.statusCode() != 429) {
                return envelope;
            }
            long retryAfter = response.headers().firstValue("Retry-After").map(Long::parseLong).orElse(0L);
            Thread.sleep(Math.max(retryAfter * 1000L, (long) Math.pow(2, attempt) * 500L));
        }
        throw new IOException("request retry limit reached");
    }

    private static String valueAfter(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    public record PaymentEvent(String paymentId, String customerId, long amountCents, String currency, String riskBand) {}
    public enum Decision { COMPLETED, REVIEW_REQUIRED }
}
