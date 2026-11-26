package com.example.fintech;

public final class PaymentFormDecisionTest {
    public static void main(String[] args) {
        var service = new PaymentFormService("test-key", "test-bucket");
        var highRisk = new PaymentFormService.PaymentEvent("pay-9", "cust-7", 9900, "USD", "HIGH");
        if (service.decide(highRisk) != PaymentFormService.Decision.REVIEW_REQUIRED) {
            throw new AssertionError("high-risk payment must require review");
        }
        var normal = new PaymentFormService.PaymentEvent("pay-10", "cust-7", 9900, "USD", "MEDIUM");
        if (service.decide(normal) != PaymentFormService.Decision.COMPLETED) {
            throw new AssertionError("medium-risk payment should complete");
        }
        System.out.println("payment decision test passed");
    }
}
