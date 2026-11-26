package com.example.fintech;

public final class PaymentFormApp {
    public static void main(String[] args) throws Exception {
        String key = required("INFRAI_API_KEY");
        String form = required("INFRAI_FORM_PDF");
        String bucket = System.getenv().getOrDefault("INFRAI_BUCKET", "payment-forms");
        PaymentFormService service = new PaymentFormService(key, bucket);
        service.createBucket();
        var event = new PaymentFormService.PaymentEvent("pay-1001", "cust-42", 12500, "USD", "LOW");
        System.out.println(service.fillAndArchive(event, form));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
