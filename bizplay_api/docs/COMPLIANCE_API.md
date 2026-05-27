# Compliance API

Base path: `/compliance/api/rule-engine`

## Technology stack

- Backend: Spring Boot
- HTTP client: Spring `RestClient`
- File upload/storage:
  - multipart upload via Spring Web
  - local file registry with `fileId` mapping

### LLM and AI usage

- Model used for receipt-based compliance checks:
  - `gemma-4-E4B-8b-instruct`
- Endpoint:
  - `http://gpu-local.sovanreach.com:9020/api/v1/gemma-4-E4B-8b-instruct/chat/completions`
- Used by:
  - `R05` receipt number extraction and comparison
  - `R06` card number pattern extraction and comparison
  - `run-all` overall compliance assessment

### External APIs

- Business registration (`R07`)
  - Provider: NTS Businessman Status API via ODcloud
  - Base URL: `https://api.odcloud.kr/api/nts-businessman/v1`

- Location geocoding (`R08`)
  - Provider: Naver Map Geocode API
  - Base URL: `https://maps.apigw.ntruss.com`

- Holiday lookup (`R09`)
  - Provider: Data.go.kr Special Date / Holiday API
  - Base URL: `http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService`

## Response format

Most endpoints return this envelope:

```json
{
  "message": "string",
  "status": "OK",
  "code": 200,
  "payload": {}
}
```

Rule endpoints that use `RuleCheckResponse` return:

```json
{
  "ruleId": "R01",
  "ruleName": "Split Payment Detection",
  "status": "Pass",
  "detail": "No issue detected."
}
```

Rule status meaning:
- `Pass`: no issue flagged by the rule
- `Failed`: issue flagged or validation could not be trusted

## Receipt upload

### `POST /receipt/upload`
Uploads a receipt image and returns a `fileId` to pass to `POST /run-all` as the optional `fileId` query parameter. This lets `run-all` reuse the uploaded receipt for `R05` and `R06` without uploading the image again.

- Content-Type: `multipart/form-data`
- Part:
  - `file`

Example response:

```json
{
  "message": "Receipt uploaded successfully.",
  "status": "CREATED",
  "code": 201,
  "payload": {
    "fileId": "6e669a86-da5c-477d-8d81-18aef3caf597",
    "originalFileName": "receipt.jpg",
    "storedFileName": "string",
    "fileUrl": "string",
    "contentType": "image/jpeg"
  }
}
```

## Single-rule endpoints

### `POST /r01`
Dummy split-payment test. Fails when `amount > 50000`.

Request body:

```json
{
  "amount": 49000
}
```

### `POST /r02`
Checks whether the transaction time falls in the restricted nighttime window.

Request body:

```json
{
  "transactionDate": "2026-05-12T23:30:00"
}
```

### `POST /r03`
Checks whether amount exceeds the dummy category limit.

- Query params:
  - `amount` required
  - `category` optional

Example:

`/compliance/api/rule-engine/r03?amount=120000&category=MEAL`

### `POST /r04`
Checks whether `mccCode` is in the blocked MCC list.

- Query params:
  - `mccCode` required
  - `blockedMccCodes` optional, defaults to dummy data

Example:

`/compliance/api/rule-engine/r04?mccCode=5812&blockedMccCodes=5812&blockedMccCodes=7995`

### `POST /r05`
Uploads a receipt image, extracts one receipt number with the LLM, and compares it with the provided `receiptNumber`.

- Content-Type: `multipart/form-data`
- Params:
  - `receiptNumber`
- Part:
  - `file`

### `POST /r06`
Uploads a receipt image, extracts the visible masked card number pattern with the LLM, and compares it with the provided `cardNumber`.

- Content-Type: `multipart/form-data`
- Params:
  - `cardNumber`
- Part:
  - `file`

### `POST /r07`
Looks up business registration status from the NTS API.

Request body:

```json
{
  "businessNumber": "3158300467"
}
```

### `POST /r08`
Geocodes one address with the Naver geocode API.

Request body:

```json
{
  "address": "충북 청주시 흥덕구 복대동 1657"
}
```

### `POST /r09`
Checks whether the date is a weekend or public holiday.

Request body:

```json
{
  "transactionDate": "2026-05-01"
}
```

### `POST /r10`
Dummy requisition mismatch check based on in-memory requisition and approval data.

Request body:

```json
{
  "requisitionId": "REQ-2026-0512-001",
  "transactionReference": "03712301"
}
```

## Run all rules

### `POST /run-all`
Runs the full compliance pipeline for one transaction.

- Query param:
  - `fileId` optional, used by `R05` and `R06`

Request body:

```json
{
  "id": "tx-2026-0512-001",
  "employeeId": "EMP-1001",
  "merchantId": "3150140032",
  "transactionDate": "2026-05-12T12:46:52",
  "amount": 70000,
  "category": "MEAL",
  "mccCode": "5812",
  "receiptNumber": "03712301",
  "cardNumber": "5327501212342536",
  "receiptCardNumber": "5327-50**-****-2536",
  "businessNumber": "3158300467",
  "address": "충북 청주시 흥덕구 복대동 1657",
  "requisitionId": "REQ-2026-0512-001",
  "transactionReference": "03712301"
}
```

Response payload:

```json
{
  "complianceStatus": "NORMAL",
  "confidenceLevel": "LOW",
  "rules": [
    {
      "ruleId": "R01",
      "ruleName": "Split Payment Detection",
      "status": "Pass",
      "info": "No issue detected."
    }
  ]
}
```

Overall fields:
- `complianceStatus`: `NORMAL` or `SUSPICION`
- `confidenceLevel`: `HIGH`, `MEDIUM`, or `LOW`
- `rules`: all `R01` to `R10`, each with `Pass` or `Failed`

## Notes

- `R03`, `R04`, and `R10` currently use dummy test data.
- `R05` and `R06` depend on LLM-based image extraction, so image quality affects accuracy.
- `run-all` overall `complianceStatus` and `confidenceLevel` are also assessed with the Gemma model.
- `fileId` is currently backed by in-memory mapping; uploaded receipt references are lost after application restart.
