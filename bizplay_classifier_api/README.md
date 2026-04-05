# BizPlay API Specification

**Version:** 1.0  
**Date:** 2026-04-05  
**Base URL:** `http://localhost:8080`  
**Auth:** JWT Bearer Token (pass in `Authorization: Bearer <token>` header for all protected endpoints)

---

## Table of Contents

1. [Bizplay Classifier API](#1-bizplay-classifier-api)
    - [1.1 Authentication](#11-authentication)
    - [1.2 Company Management](#12-company-management)
    - [1.3 Category Management](#13-category-management)
    - [1.4 Rule Management](#14-rule-management)
    - [1.5 Bot Configuration](#15-bot-configuration)
    - [1.6 Data Training](#16-data-training)
    - [1.7 Transaction Processing](#17-transaction-processing)
    - [1.8 File Storage](#18-file-storage)
2. [Security-Enhanced Internal Regulation Chatbot (Secure RAG)](#2-security-enhanced-internal-regulation-chatbot-secure-rag)
3. [AI-Based Compliance Enhancement](#3-ai-based-compliance-enhancement)
4. [Automation of Conversational Settlement Statements and Vouchers](#4-automation-of-conversational-settlement-statements-and-vouchers)

---

## Response Envelope

All endpoints (except file downloads) return a standard JSON envelope:

```json
{
  "payload": "<object | array | null>",
  "message": "Human-readable result message",
  "code": 200,
  "status": "OK",
  "token": "<JWT — only on login>",
  "fileUrl": "<URL — only on file upload>"
}
```

### Common HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK — successful GET / PUT / DELETE |
| 201 | Created — successful POST that creates a resource |
| 400 | Bad Request — validation failure |
| 401 | Unauthorized — missing or invalid JWT |
| 404 | Not Found — resource does not exist |
| 500 | Internal Server Error |

---

---

# 1. Bizplay Classifier API

Intelligent Account Classification — automatically maps corporate transaction (receipt) data to accounting categories using a hybrid rule-based + AI classifier.

---

## 1.1 Authentication

Base path: `/api/v1/auths`  
Authentication: **Not required**

---

### POST `/api/v1/auths/register`

Register a new user account.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `username` | string | Yes | Not blank |
| `firstname` | string | Yes | Not blank; no digits allowed |
| `lastname` | string | Yes | Not blank; no digits allowed |
| `gender` | string | Yes | `"M"` or `"F"` |
| `dob` | string (date) | Yes | ISO 8601 format `YYYY-MM-DD` |
| `email` | string | Yes | Valid email format |
| `password` | string | Yes | Min 8 chars; must contain letters and digits |
| `confirmPassword` | string | No | Should match `password` |

**Example Request**

```json
{
  "username": "Sokheang",
  "firstname": "Sokheang",
  "lastname": "Chan",
  "gender": "M",
  "dob": "2001-08-08",
  "email": "sokheang@example.com",
  "password": "String12345",
  "confirmPassword": "String12345"
}
```

**Response** `201 Created`

```json
{
  "payload": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "Sokheang",
    "firstname": "Sokheang",
    "lastname": "Chan",
    "email": "sokheang@example.com",
    "gender": "M",
    "dob": "2001-08-08",
    "isVerified": false
  },
  "message": "User registered successfully.",
  "code": 201,
  "status": "CREATED"
}
```

---

### POST `/api/v1/auths/login`

Authenticate and obtain a JWT token.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `email` | string | Yes | Valid email format |
| `password` | string | Yes | — |

**Example Request**

```json
{
  "email": "sokheang@example.com",
  "password": "String12345"
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "Sokheang",
    "firstname": "Sokheang",
    "lastname": "Chan",
    "email": "sokheang@example.com",
    "gender": "M",
    "dob": "2001-08-08",
    "isVerified": true
  },
  "message": "Login successfully.",
  "code": 200,
  "status": "OK",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 1.2 Company Management

Base path: `/api/v1/companies`  
Authentication: **Required**

---

### POST `/api/v1/companies/create`

Create a new company for the currently authenticated user.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `companyName` | string | Yes | Not blank |
| `businessNumber` | string | No | — |

**Example Request**

```json
{
  "companyName": "Acme Corporation",
  "businessNumber": "1234567890"
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "companyName": "Acme Corporation",
    "businessNumber": "1234567890",
    "createdDate": "2026-04-05T10:30:00",
    "ruleDTOList": []
  },
  "message": "Company was created successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### GET `/api/v1/companies/allCompanies`

Retrieve all companies belonging to the currently authenticated user.

**Request Body:** None  
**Query Parameters:** None

**Response** `200 OK`

```json
{
  "payload": [
    {
      "companyId": "550e8400-e29b-41d4-a716-446655440001",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "companyName": "Acme Corporation",
      "businessNumber": "1234567890",
      "createdDate": "2026-04-05T10:30:00",
      "ruleDTOList": []
    }
  ],
  "message": "Get all companies successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### GET `/api/v1/companies/{companyId}`

Retrieve a single company by its ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `companyId` | UUID | The unique identifier of the company |

**Response** `200 OK`

```json
{
  "payload": {
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "companyName": "Acme Corporation",
    "businessNumber": "1234567890",
    "createdDate": "2026-04-05T10:30:00",
    "ruleDTOList": []
  },
  "message": "Get company successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### DELETE `/api/v1/companies/{companyId}`

Delete a company by its ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `companyId` | UUID | The unique identifier of the company |

**Response** `200 OK`

```json
{
  "message": "Company was deleted successfully.",
  "code": 200,
  "status": "OK"
}
```

---

## 1.3 Category Management

Base path: `/api/v1/categories`  
Authentication: **Required**

Categories are the accounting account labels that transactions get classified into. Each category has a short 5-character alphanumeric code and a human-readable name.

---

### POST `/api/v1/categories/create`

Create a single category for a company. If an identical category already exists, the existing one is returned.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `companyId` | UUID | Yes | Not null |
| `code` | string | Yes | Exactly 5 alphanumeric characters (`[A-Za-z0-9]{5}`) |
| `category` | string | Yes | Not blank |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "code": "RTL01",
  "category": "Retail"
}
```

**Response** `201 Created`

```json
{
  "payload": {
    "categoryId": "550e8400-e29b-41d4-a716-446655440002",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "code": "RTL01",
    "category": "Retail",
    "isUsed": false
  },
  "message": "Category processed successfully. Existing category is returned if duplicate.",
  "code": 201,
  "status": "CREATED"
}
```

---

### GET `/api/v1/categories/{companyId}`

Retrieve all categories belonging to a company.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `companyId` | UUID | The unique identifier of the company |

**Response** `200 OK`

```json
{
  "payload": [
    {
      "categoryId": "550e8400-e29b-41d4-a716-446655440002",
      "companyId": "550e8400-e29b-41d4-a716-446655440001",
      "code": "RTL01",
      "category": "Retail",
      "isUsed": true
    }
  ],
  "message": "Categories were retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### POST `/api/v1/categories/upload`

Bulk-import categories from an Excel file.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | file | Yes | Excel file (`.xlsx`) containing category data |
| `companyId` | UUID | Yes | Target company |
| `sheetName` | string | No | Sheet name to read; defaults to the first sheet |

**Response** `201 Created`

```json
{
  "payload": {
    "totalRows": 100,
    "insertedRows": 95,
    "skippedRows": 5,
    "alreadyExistedRows": 3
  },
  "message": "Categories were created successfully from Excel.",
  "code": 201,
  "status": "CREATED"
}
```

---

## 1.4 Rule Management

Base path: `/api/v1/rules`  
Authentication: **Required**

Rules define the matching logic used by the classifier. Each rule maps a merchant pattern (and optional amount range / industry type) to one or more categories.

---

### POST `/api/v1/rules/create`

Create a new classification rule.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `companyId` | UUID | Yes | Not null |
| `categoryIds` | UUID[] | Yes | Not empty; list of category UUIDs to assign |
| `ruleName` | string | Yes | Not blank |
| `merchantName` | string | Yes | Not blank. Also accepted as JSON key `"가맹점명"` |
| `merchantIndustryName` | string | No | Also accepted as `"가맹점업종명"`, `"businessType"`, `"business_type"` |
| `minAmount` | integer | No | Minimum transaction amount (inclusive) |
| `maxAmount` | integer | No | Maximum transaction amount (inclusive) |
| `description` | string | No | Free-text notes |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "categoryIds": [
    "550e8400-e29b-41d4-a716-446655440002"
  ],
  "ruleName": "Convenience Store Rule",
  "merchantName": "GS25",
  "merchantIndustryName": "편의점",
  "minAmount": 1000,
  "maxAmount": 50000,
  "description": "Matches GS25 convenience store purchases"
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "ruleId": "550e8400-e29b-41d4-a716-446655440010",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "ruleName": "Convenience Store Rule",
    "merchantName": "GS25",
    "merchantIndustryName": "편의점",
    "usageStatus": "Y",
    "minAmount": 1000,
    "maxAmount": 50000,
    "description": "Matches GS25 convenience store purchases",
    "createdDate": "2026-04-05T10:30:00",
    "categoryDTOList": [
      {
        "categoryId": "550e8400-e29b-41d4-a716-446655440002",
        "companyId": "550e8400-e29b-41d4-a716-446655440001",
        "code": "RTL01",
        "category": "Retail",
        "isUsed": true
      }
    ]
  },
  "message": "Rule was created successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### PUT `/api/v1/rules/update/{ruleId}`

Update an existing rule.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `ruleId` | UUID | The unique identifier of the rule to update |

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `categoryIds` | UUID[] | Yes | Not empty |
| `ruleName` | string | Yes | Not blank |
| `merchantName` | string | Yes | Not blank. Also accepted as `"가맹점명"` |
| `merchantIndustryName` | string | No | Also accepted as `"가맹점업종명"`, `"businessType"`, `"business_type"` |
| `usageStatus` | string | No | `"Y"` (active) or `"N"` (inactive) |
| `minAmount` | integer | No | — |
| `maxAmount` | integer | No | — |
| `description` | string | No | — |

**Example Request**

```json
{
  "categoryIds": [
    "550e8400-e29b-41d4-a716-446655440002"
  ],
  "ruleName": "Convenience Store Rule (Updated)",
  "merchantName": "GS25",
  "merchantIndustryName": "편의점",
  "usageStatus": "Y",
  "minAmount": 500,
  "maxAmount": 100000,
  "description": "Updated rule for GS25 purchases"
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "ruleId": "550e8400-e29b-41d4-a716-446655440010",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "ruleName": "Convenience Store Rule (Updated)",
    "merchantName": "GS25",
    "merchantIndustryName": "편의점",
    "usageStatus": "Y",
    "minAmount": 500,
    "maxAmount": 100000,
    "description": "Updated rule for GS25 purchases",
    "createdDate": "2026-04-05T10:30:00",
    "categoryDTOList": [
      {
        "categoryId": "550e8400-e29b-41d4-a716-446655440002",
        "companyId": "550e8400-e29b-41d4-a716-446655440001",
        "code": "RTL01",
        "category": "Retail",
        "isUsed": true
      }
    ]
  },
  "message": "Rule was updated successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### GET `/api/v1/rules/{companyId}`

Retrieve all rules for a company.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `companyId` | UUID | The unique identifier of the company |

**Response** `200 OK`

```json
{
  "payload": [
    {
      "ruleId": "550e8400-e29b-41d4-a716-446655440010",
      "companyId": "550e8400-e29b-41d4-a716-446655440001",
      "ruleName": "Convenience Store Rule",
      "merchantName": "GS25",
      "merchantIndustryName": "편의점",
      "usageStatus": "Y",
      "minAmount": 1000,
      "maxAmount": 50000,
      "description": "Matches GS25 convenience store purchases",
      "createdDate": "2026-04-05T10:30:00",
      "categoryDTOList": [
        {
          "categoryId": "550e8400-e29b-41d4-a716-446655440002",
          "companyId": "550e8400-e29b-41d4-a716-446655440001",
          "code": "RTL01",
          "category": "Retail",
          "isUsed": true
        }
      ]
    }
  ],
  "message": "Rules was retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

## 1.5 Bot Configuration

Base path: `/api/v1/bot-configs`  
Authentication: **Required**

Configures the AI model used during the fallback AI classification stage (when no rule matches a transaction).

---

### POST `/api/v1/bot-configs/create`

Create or update the AI bot configuration for a company.

**Request Body** (`application/json`)

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `companyId` | UUID | Yes | Not null |
| `config` | object | Yes | Not null |
| `config.modelName` | string | Yes | Not blank (e.g., `"gpt-4o"`, `"claude-sonnet-4-6"`) |
| `config.temperature` | number | Yes | `0.0` – `2.0` |
| `config.systemPrompt` | string | Yes | Not blank; instructs the model how to classify transactions |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "config": {
    "modelName": "claude-sonnet-4-6",
    "temperature": 0.2,
    "systemPrompt": "You are a corporate expense classifier. Given a merchant name and transaction amount, classify the transaction into one of the provided accounting categories."
  }
}
```

**Response** `201 Created`

```json
{
  "payload": {
    "botId": "550e8400-e29b-41d4-a716-446655440020",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "config": "{\"modelName\":\"claude-sonnet-4-6\",\"temperature\":0.2,\"systemPrompt\":\"...\"}",
    "createdDate": "2026-04-05T10:30:00"
  },
  "message": "Bot config was created successfully.",
  "code": 201,
  "status": "CREATED"
}
```

---

## 1.6 Data Training

Base path: `/api/v1/data`  
Authentication: **Required**

Bulk-trains classification rules from a pre-labeled Excel file. The system parses each row, creates the necessary categories, rules, and rule-category mappings automatically.

---

### POST `/api/v1/data/train`

Parse a labeled Excel file and generate rules and categories for a company.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | file | Yes | Labeled Excel file (`.xlsx`) with merchant and category data |
| `companyId` | UUID | Yes | Target company |
| `sheetName` | string | No | Sheet name to read; defaults to the first sheet |

**Response** `201 Created`

```json
{
  "payload": {
    "totalRows": 500,
    "trainedRows": 480,
    "skippedRows": 20,
    "createdRules": 25,
    "createdCategories": 10,
    "createdMappings": 150
  },
  "message": "Training completed successfully.",
  "code": 201,
  "status": "CREATED"
}
```

---

## 1.7 Transaction Processing

Base path: `/api/v1/transactions`  
Authentication: **Required**

Core classification endpoint. Accepts a raw corporate transaction Excel file, runs the hybrid classifier (rule-based first, AI fallback), and returns an enriched file with predicted account categories.

---

### POST `/api/v1/transactions/upload`

Upload a transaction Excel file for classification.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | file | Yes | Transaction Excel file (`.xlsx`) |
| `companyId` | UUID | No | Company to classify against; uses caller's default company if omitted |
| `sheetName` | string | No | Sheet name to read; defaults to the first sheet |

**Response** `201 Created`

```json
{
  "payload": {
    "fileId": "550e8400-e29b-41d4-a716-446655440030",
    "totalRows": 1000,
    "insertedRows": 980,
    "skippedRows": 20,
    "batchSize": 100,
    "enrichedFileUrl": "/api/v1/storage/files/by-name/enriched-txn-20260405-abc123.xlsx",
    "storedFileName": "enriched-txn-20260405-abc123.xlsx",
    "ruleMatchedRows": 750,
    "aiMatchedRows": 180,
    "ruleUnmatchedRows": 50,
    "unmatchedMerchantSamples": [
      "Unknown Vendor A",
      "Unknown Vendor B",
      "Unknown Vendor C"
    ]
  },
  "message": "Transactions were created successfully from Excel.",
  "code": 201,
  "status": "CREATED"
}
```

**Response Field Descriptions**

| Field | Description |
|-------|-------------|
| `fileId` | UUID of the stored enriched file record |
| `totalRows` | Total data rows parsed from the uploaded file |
| `insertedRows` | Rows successfully processed and stored |
| `skippedRows` | Rows skipped due to parse errors or missing required data |
| `batchSize` | Batch size used during DB insertion |
| `enrichedFileUrl` | Download URL of the output file with appended classification columns |
| `storedFileName` | Server-side filename of the enriched output file |
| `ruleMatchedRows` | Rows classified by a matching rule |
| `aiMatchedRows` | Rows classified by the AI fallback (no rule matched) |
| `ruleUnmatchedRows` | Rows where neither rule nor AI could assign a category |
| `unmatchedMerchantSamples` | Sample merchant names that could not be classified (up to 3) |

---

## 1.8 File Storage

Base path: `/api/v1/storage`  
Authentication: **Required**

General-purpose file storage for uploaded and enriched Excel files.

---

### POST `/api/v1/storage/upload`

Upload any file to the server and receive a storage URL.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `file` | file | Yes | File to upload |

**Response** `201 Created`

```json
{
  "payload": {
    "originalFileName": "transactions.xlsx",
    "storedFileName": "txn-20260405-abc123.xlsx",
    "fileUrl": "/api/v1/storage/files/by-name/txn-20260405-abc123.xlsx",
    "size": 102400,
    "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  },
  "message": "File uploaded successfully.",
  "code": 201,
  "status": "CREATED",
  "fileUrl": "/api/v1/storage/files/by-name/txn-20260405-abc123.xlsx"
}
```

---

### GET `/api/v1/storage/files/by-name/{storedFileName}`

Download a file using its server-side stored filename.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `storedFileName` | string | The stored filename returned at upload time |

**Response** `200 OK`

Binary file content with header:
```
Content-Disposition: attachment; filename="<storedFileName>"
Content-Type: application/octet-stream
```

---

### GET `/api/v1/storage/files/by-id/{fileId}`

Download a file using its database record ID. The response uses the original filename for the `Content-Disposition` header.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileId` | UUID | The file record ID |

**Response** `200 OK`

Binary file content with header:
```
Content-Disposition: attachment; filename="<originalFileName>"
Content-Type: application/octet-stream
```

---

### GET `/api/v1/storage/files/company/{companyId}`

List all file upload records for a company.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `companyId` | UUID | The unique identifier of the company |

**Response** `200 OK`

```json
{
  "payload": [
    {
      "fileId": "550e8400-e29b-41d4-a716-446655440030",
      "companyId": "550e8400-e29b-41d4-a716-446655440001",
      "originalFileName": "transactions_april.xlsx",
      "storedFileName": "enriched-txn-20260405-abc123.xlsx",
      "fileUrl": "/api/v1/storage/files/by-name/enriched-txn-20260405-abc123.xlsx",
      "sheetName": "Sheet1",
      "createdDate": "2026-04-05T10:30:00"
    }
  ],
  "message": "Files were retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### DELETE `/api/v1/storage/files/{fileId}`

Delete a file record and its physical file from the server.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `fileId` | UUID | The file record ID |

**Response** `200 OK`

```json
{
  "message": "File deleted successfully.",
  "code": 200,
  "status": "OK"
}
```

---

---

# 2. Security-Enhanced Internal Regulation Chatbot (Secure RAG)

> **Status: Planned**
>
> RAG-based chatbot service that aggregates solution manuals, corporate regulations, and Q&A to answer internal questions. Runs fully on-premise (closed environment). Target response accuracy: ≥ 90%, hallucination rate: ≤ 5%.

---

*API specification for this section is not yet defined. Endpoints will be documented here upon implementation.*

---

---

# 3. AI-Based Compliance Enhancement

> **Status: Planned**
>
> Audit and anomaly detection engine for corporate travel and expense payment data. Combines rule-based fraud detection with AI scoring across 10+ anomaly pattern types. Target F1-Score: ≥ 95%, False Positive rate: ≤ 15%.

---

*API specification for this section is not yet defined. Endpoints will be documented here upon implementation.*

---

---

# 4. Automation of Conversational Settlement Statements and Vouchers

> **Status: Planned**
>
> Multimodal (image + text) conversational interface for automating travel approval, reservation, settlement, expense resolution, and voucher creation workflows.

---

*API specification for this section is not yet defined. Endpoints will be documented here upon implementation.*

---
