# BizPlay Classifier API

**Version:** 1.1  
**Date:** 2026-04-27  
**Base URL:** `http://localhost:8080`

## Overview

BizPlay Classifier is a corp-based expense-classification API.

Current direction:
- no login endpoint in the active classifier flow
- no JWT requirement for the main classifier endpoints
- `corpNo` is the primary scope key
- normalized field names are used in API and schema:
  - `corpNo`
  - `merchantIndustryName`
  - `merchantIndustryCode`
  - `isUsed` in JSON
  - `is_used` in SQL

Active controller base paths:
- `/api/v1/corps`
- `/api/v1/categories`
- `/api/v1/rules`
- `/api/v1/bot-configs`
- `/api/v1/data`
- `/api/v1/transactions`
- `/api/v1/storage`

## Response Envelope

All non-download endpoints return a common JSON envelope:

```json
{
  "payload": "<object | array | null>",
  "message": "Human-readable result message",
  "code": 200,
  "status": "OK",
  "token": null,
  "fileUrl": null
}
```

Typical status codes:

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Resource created |
| 400 | Validation or request format error |
| 404 | Corp, rule, category, or file not found |
| 500 | Internal server error |

## Data Model

Current classifier schema is corp-centered.

Primary tables:
- `corp_group`
- `corp`
- `classifier_bot_config`
- `classifier_categories`
- `classifier_rules`
- `classifier_file_upload_history`
- `rule_category_map`
- `classifier_file_classify_summary`

Important naming:
- API uses `corpNo`
- SQL tables use `corp_no`
- API uses `isUsed`
- SQL uses `is_used`
- API uses `merchantIndustryName` / `merchantIndustryCode`
- SQL uses `merchant_industry_name` / `merchant_industry_code`

## Endpoints

### 1. Corp Management

Base path: `/api/v1/corps`

#### POST `/api/v1/corps/corp-groups`

Create a corp group.

Request:

```json
{
  "corpGroupCode": "BIZPLAY_GROUP"
}
```

#### GET `/api/v1/corps/corp-groups`

Get all corp groups.

#### GET `/api/v1/corps/corp-groups/{corpGroupId}`

Get a corp group by ID.

#### POST `/api/v1/corps`

Create a corp.

Request:

```json
{
  "corpName": "BizPlay",
  "corpNo": "1234567890",
  "corpGroupId": 1
}
```

Response payload shape:

```json
{
  "corpId": 1,
  "corpGroupId": 1,
  "corpNo": "1234567890",
  "corpName": "BizPlay",
  "corpGroupCode": "BIZPLAY_GROUP",
  "ruleDTOList": []
}
```

#### GET `/api/v1/corps`

Get all corps.

#### GET `/api/v1/corps/{corpNo}`

Get one corp by `corpNo`.

#### DELETE `/api/v1/corps/{corpNo}`

Delete one corp by `corpNo`.

### 2. Category Management

Base path: `/api/v1/categories`

#### POST `/api/v1/categories/create`

Create one category under a corp.

Request:

```json
{
  "corpNo": "1234567890",
  "code": "MEAL1",
  "category": "Meal Expense"
}
```

Response payload shape:

```json
{
  "categoryId": "550e8400-e29b-41d4-a716-446655440002",
  "corpNo": "1234567890",
  "code": "MEAL1",
  "category": "Meal Expense",
  "isUsed": false
}
```

#### GET `/api/v1/categories/{corpNo}`

Get all categories for a corp.

#### POST `/api/v1/categories/upload`

Bulk-create categories from Excel.

Form fields:
- `file`: required
- `corpNo`: required
- `sheetName`: optional

### 3. Rule Management

Base path: `/api/v1/rules`

Rules are corp-scoped and keyed by merchant industry metadata.

#### POST `/api/v1/rules/create`

Canonical request body:

```json
{
  "corpNo": "1234567890",
  "categoryCodes": ["MEAL1", "TRIP1"],
  "merchantIndustryName": "Restaurant",
  "merchantIndustryCode": "PFX0Q",
  "minAmount": 0,
  "maxAmount": 50000,
  "description": "Restaurant-related expense rule"
}
```

Backward-compatible input aliases still accepted by the request DTO:
- `categoryIds` -> `categoryCodes`
- `가맹점업종명` -> `merchantIndustryName`
- `가맹점업종코드` -> `merchantIndustryCode`

Canonical field names are:
- `corpNo`
- `categoryCodes`
- `merchantIndustryName`
- `merchantIndustryCode`
- `minAmount`
- `maxAmount`
- `description`

Response payload shape:

```json
{
  "ruleId": "550e8400-e29b-41d4-a716-446655440010",
  "corpNo": "1234567890",
  "merchantIndustryName": "Restaurant",
  "merchantIndustryCode": "PFX0Q",
  "usageStatus": "Y",
  "minAmount": 0,
  "maxAmount": 50000,
  "description": "Restaurant-related expense rule",
  "createdDate": "2026-04-27T12:00:00",
  "categoryDTOList": [
    {
      "categoryId": "550e8400-e29b-41d4-a716-446655440002",
      "corpNo": "1234567890",
      "code": "MEAL1",
      "category": "Meal Expense",
      "isUsed": true
    }
  ]
}
```

#### PUT `/api/v1/rules/update/{ruleId}`

Update one rule.

Canonical request body:

```json
{
  "categoryCodes": ["MEAL1"],
  "merchantIndustryName": "Restaurant",
  "merchantIndustryCode": "PFX0Q",
  "usageStatus": "Y",
  "minAmount": 1000,
  "maxAmount": 100000,
  "description": "Updated restaurant rule"
}
```

#### DELETE `/api/v1/rules/{ruleId}`

Delete one rule by `ruleId`.

#### GET `/api/v1/rules/{corpNo}`

Get all rules for a corp.

### 4. Bot Configuration

Base path: `/api/v1/bot-configs`

#### POST `/api/v1/bot-configs/create`

Create a bot config for a corp.

Request body:

```json
{
  "corpNo": "1234567890",
  "config": {
    "temperature": 0.0,
    "apiKey": "sk-...",
    "systemPrompt": "You are a corporate expense classifier."
  }
}
```

Request parameters on the endpoint select provider/model:
- `provider`
- `modelName`

#### GET `/api/v1/bot-configs/{corpNo}`

Get latest bot config for a corp.

#### PUT `/api/v1/bot-configs/{corpNo}`

Update latest bot config for a corp.

#### GET `/api/v1/bot-configs/prompt-enhancement`

Generate or preview prompt enhancement from the latest training file.

### 5. Data Training

Base path: `/api/v1/data`

#### POST `/api/v1/data/train`

Train rules and categories from Excel.

Form fields:
- `file`: required
- `corpNo`: required
- `sheetName`: optional
- `sampleRows`: optional

The training flow creates:
- categories
- rules
- rule-category mappings

Expected normalized spreadsheet/business columns:
- `merchant_industry_code`
- `merchant_industry_name`

### 6. Transaction Processing

Base path: `/api/v1/transactions`

#### POST `/api/v1/transactions/test-single-transaction/create`

Create one test transaction.

#### PUT `/api/v1/transactions/files/{fileId}/rows`

Patch classified rows for an uploaded file.

#### POST `/api/v1/transactions/upload`

Upload an Excel file for classification.

Form fields:
- `file`: required
- `corpNo`: optional depending on file contents
- `sheetName`: optional

#### GET `/api/v1/transactions/files/corp/{corpNo}/classify-summaries`

Get file classify summaries for a corp.

#### GET `/api/v1/transactions/files/{id}/transactions`

Get paged transactions for one file.

Query params:
- `page` default `1`
- `limit` default `100`

### 7. File Storage

Base path: `/api/v1/storage`

#### POST `/api/v1/storage/upload`

Store a generic file.

#### POST `/api/v1/storage/training-files/upload`

Store a training file for a corp.

Form fields:
- `file`: required
- `corpNo`: required

#### GET `/api/v1/storage/files/by-name/{storedFileName}`

Download by stored file name.

#### GET `/api/v1/storage/files/by-id/{fileId}`

Download by file record ID.

#### GET `/api/v1/storage/files/corp/{corpNo}`

Get all file records for a corp.

#### GET `/api/v1/storage/files/corp/{corpNo}/filter`

Get file records for a corp filtered by `fileType`.

#### DELETE `/api/v1/storage/files/{fileId}`

Delete a file and its file record.

## Notes

- `/api/v1/auths/login` has been removed from the active API surface.
- Older `companyId` wording should now be read as `corpNo` unless a legacy DTO explicitly keeps compatibility aliases.
- Older Korean-encoded DB column names in legacy documents have been replaced by normalized schema names in the active classifier flow.
- If Swagger shows stale schemas, restart the app so the OpenAPI document is regenerated.
