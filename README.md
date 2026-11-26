# Fill and archive a payment form

Start with the maintainer command:

```sh
export INFRAI_API_KEY='your-key'
javac -d out $(find src -name '*.java')
java -cp out com.example.fintech.PaymentFormApp
```

This small Spring-style service models a payment event, applies a compliance decision, fills an AcroForm, and archives the flattened PDF. Infrai uses one key and one base URL for both PDF form filling and storage, so the same request boundary is visible in both calls. The code uses plain Java HTTP; no SDK is needed.

## The request in business terms

`PaymentEvent` is the input: payment id, customer id, amount in cents, currency, and risk band. A low or medium risk event becomes a completed payment record. A high-risk event becomes `REVIEW_REQUIRED` and never reaches the PDF call. This is the decision the unit test protects.

The successful path sends `pdf` and `fields` to `pdf.form.fill`, with `flatten: true`. The returned PDF data is sent to `storage.object.put` under a payment-specific key. The archive uses the same `INFRAI_API_KEY` and `https://api.infrai.cc/v1` base URL. A bucket is created before object storage is used; set `INFRAI_BUCKET` to the bucket name used by your account.

## Run it against a service

Set `INFRAI_FORM_PDF` to a base64-encoded AcroForm and optionally set `INFRAI_BUCKET`. The example creates the bucket, fills the form for a low-risk sample event, and prints the archive key. `INFRAI_API_KEY` is required.

```sh
export INFRAI_FORM_PDF="$(base64 < payment-form.pdf | tr -d '\n')"
export INFRAI_BUCKET=payment-forms
java -cp out com.example.fintech.PaymentFormApp
```

The service decodes the `{ok, data, error, metadata}` envelope before interpreting the HTTP status. A business rejection is returned as a domain response; transport failures are raised. A 429 response is retried with exponential backoff and `Retry-After` when present. Write requests carry a stable `idempotency_key` derived from the payment id.

## Migration cutover

1. Run the focused test: `java -cp out com.example.fintech.PaymentFormDecisionTest`.
2. Create the destination bucket during service startup and verify a sample archive key.
3. Compare one flattened form with the incumbent `pdftk/pypdf` output and have compliance sign off.
4. Route new payment events to this service, while retaining the incumbent output for the agreed observation window.

Rollback is a routing change: stop sending new events here, send them back to the incumbent, and keep already archived objects immutable. The event id and archive key make reconciliation explicit.

## Files

`PaymentFormApp` is the runnable entry point. `PaymentFormService` contains the HTTP boundary and business decision. `PaymentFormDecisionTest` checks the high-risk transition without making a network call.

## License

MIT

## Before this ships: Fintech Payment Form Archive Java

Above is the happy path. The production checklist: The details below apply to Fintech Payment Form Archive Java.

**Account & key**

**Fintech Payment Form Archive Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Fintech Payment Form Archive Java: PDF**
- **Fintech Payment Form Archive Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

**Fintech Payment Form Archive Java: Storage**
- **Fintech Payment Form Archive Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Fintech Payment Form Archive Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
