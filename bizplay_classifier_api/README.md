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

> **Status: In Design**
>
> RAG-based chatbot service that aggregates solution manuals, corporate regulations, and Q&A to answer internal questions. Runs fully on-premise (closed environment). Target response accuracy: ≥ 90%, hallucination rate: ≤ 5%.

---

## Architecture Overview

Built on **Spring AI** ([spring-ai RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)) with a local LLM backend.

```
[Client]
   │
   ├── POST /rag/documents/upload   ──► DocumentReader → Splitter → EmbeddingModel → VectorStore
   │
   └── POST /rag/chat               ──► QuestionAnswerAdvisor → VectorStore → LLM → Response
```

**Spring AI components used:**

| Component | Role |
|---|---|
| `TikaDocumentReader` / `PagePdfDocumentReader` | Load uploaded documents |
| `TokenTextSplitter` | Chunk documents into fixed-size overlapping segments |
| `EmbeddingModel` | Generate vector embeddings per chunk |
| `VectorStore` | Store and similarity-search embeddings (e.g., PGVector, Chroma) |
| `QuestionAnswerAdvisor` | Wraps retrieval + prompt augmentation into `ChatClient` |
| `RetrievalAugmentationAdvisor` | Advanced modular RAG — query rewriting, re-ranking |
| `CompressionQueryTransformer` | Compress multi-turn conversation history into a single query |

**Target LLMs for testing:**

| Model | Size | Notes |
|---|---|---|
| `LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-AWQ` | 7.8B | Korean-English bilingual; AWQ quantization fits on a single 16 GB GPU |
| `Qwen/Qwen2.5-72B-Instruct` | 72B | Strong multilingual reasoning; requires multi-GPU or high-VRAM setup |
| `SKT/A.X-4.0-72B` | 72B | Korean-specialized LLM by SKT; optimized for Korean enterprise document Q&A |

---

## 2.1 Document Management

Base path: `/api/v1/rag/documents`
Authentication: **Required**

---

### POST `/api/v1/rag/documents/upload`

Upload one or more internal documents (PDF, DOCX, TXT) into the vector store for a company. The server reads, chunks, embeds, and indexes each file automatically.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `files` | file[] | Yes | One or more documents (`.pdf`, `.docx`, `.txt`) |
| `companyId` | UUID | Yes | Scopes the indexed documents to this company |
| `source` | string | No | Label for this batch (e.g., `"expense-policy-2026"`) |
| `overwrite` | boolean | No | If `true`, re-indexes existing documents with the same source label. Default: `false` |

**Spring AI internals (per file):**
1. `TikaDocumentReader` / `PagePdfDocumentReader` — parse raw file
2. `TokenTextSplitter` — chunk into segments (default: 512 tokens, 64-token overlap)
3. `EmbeddingModel.embed()` — generate vector per chunk
4. `VectorStore.add()` — persist chunks with metadata `{companyId, source, filename, chunkIndex}`

**Response** `201 Created`

```json
{
  "payload": {
    "uploadId": "550e8400-e29b-41d4-a716-446655440050",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "source": "expense-policy-2026",
    "files": [
      {
        "originalFileName": "expense-policy.pdf",
        "totalChunks": 42,
        "status": "INDEXED"
      },
      {
        "originalFileName": "travel-regulations.docx",
        "totalChunks": 31,
        "status": "INDEXED"
      }
    ],
    "totalChunks": 73,
    "indexedAt": "2026-04-05T10:30:00"
  },
  "message": "Documents indexed successfully.",
  "code": 201,
  "status": "CREATED"
}
```

---

### GET `/api/v1/rag/documents/{companyId}`

List all indexed document sources for a company.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `companyId` | UUID | The company whose document index to query |

**Response** `200 OK`

```json
{
  "payload": [
    {
      "uploadId": "550e8400-e29b-41d4-a716-446655440050",
      "source": "expense-policy-2026",
      "fileCount": 2,
      "totalChunks": 73,
      "indexedAt": "2026-04-05T10:30:00"
    }
  ],
  "message": "Documents retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### DELETE `/api/v1/rag/documents/{uploadId}`

Remove an indexed document batch from the vector store.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `uploadId` | UUID | The upload batch ID to delete |

**Response** `200 OK`

```json
{
  "message": "Documents deleted from index successfully.",
  "code": 200,
  "status": "OK"
}
```

---

## 2.2 Chat Inference

Base path: `/api/v1/rag/chat`
Authentication: **Required**

---

### POST `/api/v1/rag/chat`

Submit a question. The server retrieves the top-K most relevant document chunks from the vector store and passes them as context to the LLM to generate a grounded answer.

**Spring AI internals:**
1. `CompressionQueryTransformer` — compresses prior conversation turns into a standalone query (if `sessionId` provided)
2. `VectorStoreDocumentRetriever` — similarity search with `companyId` filter, returns top-K chunks
3. `QuestionAnswerAdvisor` — injects retrieved chunks into the prompt template
4. `ChatModel.call()` → EXAONE-3.5-7.8B-Instruct-AWQ generates a grounded answer

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `companyId` | UUID | Yes | Scopes retrieval to this company's documents only |
| `question` | string | Yes | User's question |
| `sessionId` | string | No | Conversation session ID for multi-turn context. Omit for single-turn |
| `topK` | integer | No | Number of document chunks to retrieve. Default: `5` |
| `similarityThreshold` | number | No | Minimum similarity score `0.0`–`1.0`. Default: `0.7` |
| `source` | string | No | Restrict retrieval to a specific document source label |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "question": "해외 출장 시 숙박비 한도는 얼마인가요?",
  "sessionId": "session-abc123",
  "topK": 5,
  "similarityThreshold": 0.7
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "answer": "사내 규정 제12조에 따르면, 해외 출장 숙박비 한도는 북미·유럽 지역 USD 200/박, 아시아 지역 USD 150/박입니다.",
    "sessionId": "session-abc123",
    "sources": [
      {
        "source": "travel-regulations.docx",
        "chunkIndex": 7,
        "similarityScore": 0.923,
        "excerpt": "제12조(숙박비) 해외 출장의 경우 북미·유럽 USD 200..."
      },
      {
        "source": "travel-regulations.docx",
        "chunkIndex": 8,
        "similarityScore": 0.871,
        "excerpt": "아시아 지역 출장의 경우 USD 150을 초과할 수 없으며..."
      }
    ],
    "retrievedChunks": 2,
    "model": "SKT/A.X-4.0-72B"
  },
  "message": "Answer generated successfully.",
  "code": 200,
  "status": "OK"
}
```

**Response Field Descriptions**

| Field | Description |
|---|---|
| `answer` | LLM-generated answer grounded in retrieved document chunks |
| `sessionId` | Echo of the session ID for multi-turn tracking |
| `sources` | Document chunks used as context, with similarity scores and text excerpts |
| `retrievedChunks` | Number of chunks actually passed to the LLM |
| `model` | LLM model identifier used for this response |

---

## 2.3 Design Notes & Suggestions

### LLM Candidates Comparison

| Model | Size | Korean | Context Window | Hardware Requirement | Best For |
|---|---|---|---|---|---|
| `LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-AWQ` | 7.8B | Excellent | 32k | Single GPU (≥16 GB VRAM) | Low-resource on-prem deployment |
| `Qwen/Qwen2.5-72B-Instruct` | 72B | Good | 128k | Multi-GPU or 80 GB VRAM | Long-document retrieval, complex reasoning |
| `SKT/A.X-4.0-72B` | 72B | Best | 32k | Multi-GPU or 80 GB VRAM | Korean enterprise document Q&A |

### On EXAONE-3.5-7.8B-Instruct-AWQ

| Aspect | Recommendation |
|---|---|
| **Strength** | Strong Korean-English bilingual capability; lightest of the three — best fit for cost-constrained on-prem setup |
| **AWQ quantization** | Runs on a single 16 GB GPU (e.g., RTX 4080/3090) with acceptable throughput |
| **Temperature** | Set to `0.1`–`0.3` for factual Q&A; lower = less hallucination |
| **Context window** | Up to 32k tokens — keep total retrieved chunks well under 4k to leave generation headroom |
| **System prompt** | Instruct to answer only from provided context and say "모르겠습니다" if answer is not in the documents |

### On Qwen2.5-72B-Instruct

| Aspect | Recommendation |
|---|---|
| **Strength** | Best overall reasoning quality among the three; 128k context window allows passing more document chunks per call |
| **Korean** | Competent but not specialized — may occasionally produce unnatural phrasing in Korean-only documents |
| **Temperature** | `0.1`–`0.2` for RAG; the model is instruction-tuned so it respects system prompts well |
| **Context window** | 128k tokens — can retrieve larger `topK` (e.g., 10–20 chunks) without truncation risk |
| **Hardware** | Requires multi-GPU setup (e.g., 2× A100 80 GB) or quantized variant (GPTQ/AWQ) for single-GPU use |
| **Use case fit** | Best for complex multi-hop questions spanning several regulation documents |

### On SKT A.X 4.0 (72B)

| Aspect | Recommendation |
|---|---|
| **Strength** | Purpose-built for Korean enterprise use; highest Korean language accuracy among the three |
| **Temperature** | `0.1`–`0.2`; designed for instruction-following and factual grounding |
| **Context window** | 32k tokens — same RAG chunk budget as EXAONE |
| **Hardware** | Requires multi-GPU or 80 GB VRAM; no AWQ variant confirmed yet — plan for full-precision or GPTQ |
| **Use case fit** | Recommended primary candidate for production if on-prem Korean regulation Q&A is the core requirement |
| **Hallucination** | SKT A.X is trained with Korean compliance data in mind; expected lower hallucination rate on Korean corporate regulation text |

### On Spring AI RAG advisor choice

| Use case | Recommended advisor |
|---|---|
| Simple single-turn Q&A | `QuestionAnswerAdvisor` |
| Multi-turn conversation | `RetrievalAugmentationAdvisor` + `CompressionQueryTransformer` |
| Query is verbose or ambiguous | Add `RewriteQueryTransformer` in the pre-retrieval stage |
| Documents in Korean, embeddings model in English | Add `TranslationQueryTransformer` to translate query before embedding lookup |

### On Vector Store choice for on-premise deployment

| Option | Notes |
|---|---|
| **PGVector** (PostgreSQL) | Recommended — already common in Spring Boot stacks, easy Docker setup |
| **Chroma** | Lightweight Python-native store, good for prototyping |
| **Qdrant** | Best performance at scale, native Docker/Kubernetes support |

---

---

# 3. AI-Based Compliance Enhancement

> **Status: In Design**
>
> Audit and anomaly detection engine for corporate travel and expense payment data. Combines rule-based fraud detection (R01–R10) with AI scoring. Target F1-Score: ≥ 95%, False Positive rate: ≤ 15%.

---

## Architecture Overview

```
[Client]
   │
   ├── POST /compliance/audit          ──► RuleEngine (R01–R10) ──► AnomalyScorer ──► AuditResult
   ├── GET  /compliance/audit/history  ──► AuditResultStore
   ├── GET  /compliance/rules          ──► RuleConfigStore
   ├── POST /compliance/rules          ──► RuleConfigStore
   │
   └── POST /compliance/chat           ──► RAG Chatbot (PDF regulation Q&A)
            └── POST /compliance/chat/documents/upload
```

**External integrations per rule:**

| Rule | Integration | Source |
|---|---|---|
| R06 — Card mismatch | OCR (`dots.ocr`) for digital receipts; VLM for handwritten receipts | [rednote-hilab/dots.ocr](https://huggingface.co/rednote-hilab/dots.ocr) |
| R07 — Business registration | Public Open API — merchant registration number verification | [data.go.kr / 15081808](https://www.data.go.kr/data/15081808/openapi.do) |
| R08 — Location anomaly | Naver Maps Geocoding API — reverse-geocode merchant coordinates | [Naver Cloud Geocoding](https://api.ncloud-docs.com/docs/en/application-maps-geocoding) |
| R10 — Requisition mismatch | VLM — compare scanned requisition document against transaction (see §4) | See Section 4 |

---

## Compliance Rules Reference (R01 – R10)

| ID | Name | Status | Trigger Condition |
|---|---|---|---|
| R01 | Split payment detection | Active | Same employee + same merchant within 30-min window; amount in 49,000–49,999 range |
| R02 | Nighttime transaction | Active | Approval time between 22:00–06:00 |
| R03 | Limit exceed | Active | Transaction amount exceeds per-category limit defined in rule params |
| R04 | MCC prohibited | Active | Transaction `mcc_code` is in the blocked list defined in rule params |
| R05 | Duplicate receipt | Active | Same `receipt_hash` already exists on another transaction |
| R06 | Card mismatch | Placeholder | OCR/VLM not yet integrated — always returns `null` |
| R07 | Business registration check | Placeholder | External API not yet integrated — always returns `null` |
| R08 | Location anomaly | Placeholder | GPS/location data not yet available — always returns `null` |
| R09 | Holiday / weekend use | Active | Approval date falls on Saturday or Sunday |
| R10 | Requisition mismatch | Placeholder | Purchase-requisition data not yet integrated — always returns `null` |

---

## 3.1 Compliance Audit

Base path: `/api/v1/compliance`
Authentication: **Required**

---

### POST `/api/v1/compliance/audit`

Run all active compliance rules against one or more transactions. Returns a flag result per rule per transaction.

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `companyId` | UUID | Yes | Company scope for rule config lookup |
| `transactions` | object[] | Yes | List of transactions to audit |
| `transactions[].transactionId` | string | Yes | Unique ID of the transaction |
| `transactions[].employeeId` | string | Yes | Employee who made the transaction |
| `transactions[].merchantName` | string | Yes | Merchant name |
| `transactions[].mccCode` | string | No | MCC code (required for R04) |
| `transactions[].merchantRegNumber` | string | No | Merchant business registration number (required for R07) |
| `transactions[].amount` | integer | Yes | Supply amount in KRW |
| `transactions[].category` | string | No | Account category name (required for R03 limit check) |
| `transactions[].approvalDatetime` | string | Yes | ISO 8601 datetime e.g. `"2026-04-05T23:15:00"` |
| `transactions[].receiptHash` | string | No | Hash of the attached receipt file (required for R05) |
| `transactions[].receiptFileId` | UUID | No | File ID of the scanned receipt (required for R06 OCR) |
| `transactions[].requisitionFileId` | UUID | No | File ID of the purchase requisition document (required for R10) |
| `rules` | string[] | No | Subset of rule IDs to run e.g. `["R01","R03"]`. Omit to run all active rules |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "transactions": [
    {
      "transactionId": "TXN-20260405-001",
      "employeeId": "EMP-0042",
      "merchantName": "스타벅스 강남점",
      "mccCode": "5812",
      "merchantRegNumber": "123-45-67890",
      "amount": 49500,
      "category": "식대",
      "approvalDatetime": "2026-04-05T23:15:00",
      "receiptHash": "sha256-abc123...",
      "receiptFileId": "550e8400-e29b-41d4-a716-446655440060"
    }
  ]
}
```

**Response** `200 OK`

```json
{
  "payload": {
    "auditId": "550e8400-e29b-41d4-a716-446655440070",
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "auditedAt": "2026-04-05T23:20:00",
    "summary": {
      "total": 1,
      "flagged": 1,
      "clean": 0
    },
    "results": [
      {
        "transactionId": "TXN-20260405-001",
        "flagged": true,
        "ruleResults": [
          {
            "ruleId": "R01",
            "ruleName": "Split payment detection",
            "triggered": false,
            "detail": null
          },
          {
            "ruleId": "R02",
            "ruleName": "Nighttime transaction",
            "triggered": true,
            "detail": "Transaction approved at 23:15, outside allowed hours (06:00–22:00)"
          },
          {
            "ruleId": "R03",
            "ruleName": "Limit exceed",
            "triggered": false,
            "detail": null
          },
          {
            "ruleId": "R04",
            "ruleName": "MCC prohibited",
            "triggered": false,
            "detail": null
          },
          {
            "ruleId": "R05",
            "ruleName": "Duplicate receipt",
            "triggered": false,
            "detail": null
          },
          {
            "ruleId": "R06",
            "ruleName": "Card mismatch",
            "triggered": null,
            "detail": "Placeholder — OCR integration pending"
          },
          {
            "ruleId": "R07",
            "ruleName": "Business registration check",
            "triggered": null,
            "detail": "Placeholder — data.go.kr API integration pending"
          },
          {
            "ruleId": "R08",
            "ruleName": "Location anomaly",
            "triggered": null,
            "detail": "Placeholder — GPS data not available"
          },
          {
            "ruleId": "R09",
            "ruleName": "Holiday / weekend use",
            "triggered": false,
            "detail": null
          },
          {
            "ruleId": "R10",
            "ruleName": "Requisition mismatch",
            "triggered": null,
            "detail": "Placeholder — requisition data not available"
          }
        ]
      }
    ]
  },
  "message": "Audit completed successfully.",
  "code": 200,
  "status": "OK"
}
```

**`triggered` field values:**

| Value | Meaning |
|---|---|
| `false` | Rule ran and found no anomaly |
| `true` | Rule ran and flagged this transaction |
| `null` | Rule is a placeholder — integration not yet available |

---

### GET `/api/v1/compliance/audit/history/{companyId}`

Retrieve past audit records for a company.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `companyId` | UUID | Company to query audit history for |

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `from` | string (date) | No | Filter from date `YYYY-MM-DD` |
| `to` | string (date) | No | Filter to date `YYYY-MM-DD` |
| `flaggedOnly` | boolean | No | If `true`, return only audits with at least one triggered rule. Default: `false` |
| `page` | integer | No | Page number, 0-based. Default: `0` |
| `size` | integer | No | Page size. Default: `20` |

**Response** `200 OK`

```json
{
  "payload": {
    "content": [
      {
        "auditId": "550e8400-e29b-41d4-a716-446655440070",
        "auditedAt": "2026-04-05T23:20:00",
        "total": 50,
        "flagged": 3,
        "clean": 47
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1
  },
  "message": "Audit history retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

## 3.2 Rule Configuration

Base path: `/api/v1/compliance/rules`
Authentication: **Required**

Compliance rules that carry configurable parameters (R03 limits, R04 blocked MCCs) are stored per company. Rules without params (R01, R02, R05, R09) use fixed logic.

---

### GET `/api/v1/compliance/rules/{companyId}`

Retrieve the active rule configuration for a company.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `companyId` | UUID | The company to retrieve rules for |

**Response** `200 OK`

```json
{
  "payload": [
    {
      "ruleId": "R03",
      "ruleName": "Limit exceed",
      "enabled": true,
      "params": {
        "limits": {
          "식대": 100000,
          "교통비": 50000,
          "출장비": 500000
        },
        "default": 200000
      }
    },
    {
      "ruleId": "R04",
      "ruleName": "MCC prohibited",
      "enabled": true,
      "params": {
        "blocked": ["5813", "5912", "7995"]
      }
    }
  ],
  "message": "Compliance rules retrieved successfully.",
  "code": 200,
  "status": "OK"
}
```

---

### POST `/api/v1/compliance/rules`

Create or overwrite a rule configuration for a company.

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `companyId` | UUID | Yes | Target company |
| `ruleId` | string | Yes | Rule ID: `"R03"` or `"R04"` |
| `enabled` | boolean | Yes | Whether this rule is active |
| `params` | object | Yes | Rule-specific parameters (see schema below) |

**`params` schema by rule:**

**R03 — Limit exceed**
```json
{
  "limits": {
    "식대": 100000,
    "교통비": 50000
  },
  "default": 200000
}
```

**R04 — MCC prohibited**
```json
{
  "blocked": ["5813", "5912", "7995"]
}
```

**Response** `201 Created`

```json
{
  "payload": {
    "companyId": "550e8400-e29b-41d4-a716-446655440001",
    "ruleId": "R03",
    "enabled": true,
    "params": {
      "limits": { "식대": 100000, "교통비": 50000 },
      "default": 200000
    },
    "updatedAt": "2026-04-05T10:30:00"
  },
  "message": "Compliance rule saved successfully.",
  "code": 201,
  "status": "CREATED"
}
```

---

## 3.3 Compliance Chatbot (Regulation Q&A)

Base path: `/api/v1/compliance/chat`
Authentication: **Required**

RAG-powered chatbot for answering employee questions about expense regulations and flagged transaction explanations. Built on the same Spring AI RAG stack as Section 2 but scoped to compliance documents (expense policy PDFs, audit rule explanations).

---

### POST `/api/v1/compliance/chat/documents/upload`

Index compliance regulation documents (expense policy PDF, audit rule guides) into the vector store.

**Content-Type:** `multipart/form-data`

| Parameter | Type | Required | Description |
|---|---|---|---|
| `files` | file[] | Yes | PDF or DOCX regulation documents |
| `companyId` | UUID | Yes | Company scope |
| `source` | string | No | Label e.g. `"expense-policy-2026"`, `"audit-rule-guide"` |

**Response** `201 Created` — same structure as Section 2.1 document upload.

---

### POST `/api/v1/compliance/chat`

Ask a question about expense regulations or get an explanation of a flagged transaction.

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `companyId` | UUID | Yes | Company scope for document retrieval |
| `question` | string | Yes | Natural language question |
| `sessionId` | string | No | Session ID for multi-turn conversation |
| `auditId` | UUID | No | If provided, attaches the audit result as additional context so the model can explain specific flags |
| `topK` | integer | No | Number of document chunks to retrieve. Default: `5` |

**Example Request**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "question": "야간 거래가 왜 규정 위반인가요? 예외가 있나요?",
  "sessionId": "session-xyz789",
  "auditId": "550e8400-e29b-41d4-a716-446655440070"
}
```

**Response** `200 OK` — same structure as Section 2.2 chat response.

---

## 3.4 Placeholder Rule Integration Notes

### R06 — Card Mismatch (OCR + VLM)

When a `receiptFileId` is present, the pipeline selects the OCR method based on document type:

| Document type | Model | Role |
|---|---|---|
| Standard Korean receipt / tax invoice (printed) | **Naver CLOVA OCR** | Structured Korean receipt parsing — directly returns field-level output (merchant name, amount, card number) without post-processing |
| General printed / digital document | **PaddleOCR** / **Surya OCR** | Confirmed Korean support; fully on-premise; fast text extraction |
| Handwritten receipt or form | **Qwen2.5-VL** / **InternVL2** / **EXAONE Vision** | VLM interprets handwritten Korean card digits and field values |

**Processing steps:**
1. Detect document type (receipt vs. general document vs. handwritten)
2. Route to the appropriate model above
3. Extract the card number from the model output
4. Normalize both numbers to masked format (e.g. `****-****-****-1234`) before comparison
5. Compare against the transaction's registered card number
6. Mismatch → `triggered: true`

> **Note:** Step 4 (masked string comparison) is a post-OCR normalization step — not an LLM call. This keeps the comparison deterministic regardless of which OCR model was used.

---

#### OCR Models — Experiment List

**Korean-Specialized (Commercial)**

| Model | Type | Open Source | Korean | Notes |
|---|---|---|---|---|
| **Naver CLOVA OCR** | API service | No | Excellent | Best-in-class for Korean receipts and tax invoices; returns structured field-level output directly; on-premise version available |
| **Upstage Document Parse** | API service | No | Excellent | Korean AI company; structured key-value extraction; strong on Korean business documents and forms |

**General Multilingual — Korean Confirmed (Open Source)**

| Model | Type | Open Source | Korean | Notes |
|---|---|---|---|---|
| **PaddleOCR** | Pure OCR | Yes | Good | Confirmed Korean (Hangul) support; fast; fully on-premise; well-maintained |
| **EasyOCR** | Pure OCR | Yes | Good | 80+ confirmed languages including Korean; simplest API for quick prototyping |
| **GOT-OCR 2.0** (`stepfun-ai/GOT-OCR2_0`) | End-to-end OCR | Yes | Good | Handles plain text, tables, math, charts in one pass; confirmed multilingual Korean support |
| **Surya OCR** | Pure OCR | Yes | Good | 90+ confirmed languages including Korean; line-level OCR; designed for document-scale throughput |

---

#### VLM Models — Experiment List

| Model | Params | Open Source | Korean | Strength for This Use Case |
|---|---|---|---|---|
| **Qwen2.5-VL** (`Qwen/Qwen2.5-VL-72B-Instruct`) | 72B | Yes | Good | Best overall reasoning on document images; understands layout, tables, handwriting; pairs naturally with Qwen2.5-72B already in the stack |
| **Qwen2.5-VL** (`Qwen/Qwen2.5-VL-7B-Instruct`) | 7B | Yes | Good | Lighter variant; suitable for single-GPU deployment when 72B is too heavy |
| **InternVL2** (`OpenGVLab/InternVL2-8B`) | 8B | Yes | Good | Strong document understanding benchmark scores; efficient on-premise deployment |
| **InternVL2** (`OpenGVLab/InternVL2-40B`) | 40B | Yes | Good | Higher accuracy for complex handwritten or degraded documents |
| **EXAONE Vision** | — | Limited | Excellent | LG AI's Korean-specialized vision model; shares infra with EXAONE-3.5 already in the stack |

> **Recommendation for Korean handwritten forms (R06, R10):** Start with **Qwen2.5-VL-7B** (fastest to deploy, reuses existing Qwen infrastructure) and compare against **InternVL2-8B**. Escalate to the 72B / 40B variants only if smaller models fail on degraded or complex handwriting.

---

### R07 — Business Registration Check

Calls the [data.go.kr public Open API](https://www.data.go.kr/data/15081808/openapi.do) with the merchant's `merchantRegNumber` to verify:
- Registration is active (not closed or suspended)
- Registered business category matches the transaction's merchant industry

### R08 — Location Anomaly

Uses [Naver Cloud Geocoding API](https://api.ncloud-docs.com/docs/en/application-maps-geocoding) to:
- Reverse-geocode the merchant's registered address into coordinates
- Compare against the employee's last known location or prior transaction location
- Flag if geographically impossible travel time between two consecutive transactions

### R10 — Requisition Mismatch

See Section 4 for the full VLM-based document comparison pipeline. R10 is the compliance audit trigger; Section 4 provides the requisition creation and approval workflow that produces the documents R10 validates against.

---

---

# 4. Automation of Conversational Settlement Statements and Vouchers

> **Status: In Design**
>
> This section covers two features: **(1)** an AI assistant that helps employees fill out travel or expense approval forms — either by typing a description in plain language or by uploading a receipt or document image, and **(2)** a natural language query tool that lets managers ask questions about expense data without writing SQL.

---

## How Section 4 Relates to Section 3 (R10 — Requisition Mismatch)

These two sections work at different points in the same expense lifecycle:

| | Section 4 | Section 3 — R10 |
|---|---|---|
| **When** | Before the expense happens | After the transaction is recorded |
| **What it does** | Helps create and approve the travel/expense request | Checks whether the actual transaction matches the approved request |
| **Technology** | LLM + VLM to fill and generate forms | VLM to compare the approved form against the transaction |
| **Output** | Approved requisition PDF | `triggered: true` (mismatch found) or `false` (all clear) |

**Step-by-step flow:**

```
Step 1 — Employee fills out a request (Section 4, Endpoint 1)
         Types a description or uploads a receipt image
         LLM + VLM extracts the fields and fills the form
         Employee reviews and submits → status: PENDING_APPROVAL

Step 2 — Manager approves (Section 4, Endpoint 1)
         System generates the approved requisition as a PDF
         PDF is stored and linked to the request record

Step 3 — Employee spends the money (real-world transaction)
         Transaction is uploaded via Section 1 (/transactions/upload)

Step 4 — Compliance audit runs (Section 3, R10)
         VLM compares the approved requisition PDF against the transaction
         → triggered: true (amounts / merchant don't match) or false (all good)

Step 5 — Manager queries expense data (Section 4, Endpoint 2)
         Types a plain-language question
         MRS-Agent converts it to SQL and returns the result
```

---

## Technology Reused From Other Sections

| Technology | From | Used Here For |
|---|---|---|
| Spring AI `ChatClient` | Section 2 | Driving the multi-turn form-filling conversation |
| VLM + `dots.ocr` | Section 3 — R06 | Reading text and field values from uploaded receipt / document images |
| `FileStorageService` | Section 1.8 | Saving the approved requisition PDF and making it downloadable |
| JWT authentication | Section 1.1 | Securing all endpoints |
| LLM (Qwen2.5-72B / SKT A.X 4.0) | Section 2 | Powering both form filling and SQL generation |

---

## 4.1 Form Filling Assistant

Base path: `/api/v1/workflow/form`
Authentication: **Required**

The employee can describe their trip or expense in two ways — by typing a free-text message, or by uploading a photo or PDF of a receipt, itinerary, or any supporting document. Both inputs go through the same pipeline: document images are read by OCR/VLM first, then the LLM combines everything into a structured form. The process is conversational — the employee keeps adding information turn by turn until all required fields are complete.

```
Input option A: typed message  ──┐
                                  ├──► LLM fills form fields
Input option B: uploaded image ──┘
                └── OCR / VLM reads
                    text from image first
```

---

### POST `/api/v1/workflow/form/chat`

Send a message or upload documents to fill the form step by step. Each call returns the current state of the form and a list of fields still missing, so the client knows what to ask next.

**Content-Type:** `multipart/form-data`

**Form Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `message` | string | Yes | What the employee types, e.g. `"부산 출장 4월 10일~12일, KTX 왕복"` |
| `companyId` | UUID | Yes | Used to look up the company's account categories and rules |
| `sessionId` | string | No | The ongoing conversation ID. Leave empty to start a new session |
| `formType` | string | No | `"TRAVEL_REQUEST"` or `"SETTLEMENT"`. The LLM guesses this from the message if not provided |
| `files` | file[] | No | Receipt photos, itinerary PDFs, or scanned documents. Digital files go through `dots.ocr`; handwritten ones go through VLM |

**What happens internally:**
1. If files are attached — `dots.ocr` (printed/digital) or VLM (handwritten) reads each file and extracts raw text and field values
2. The extracted text is combined with the employee's typed message and the current form state
3. The LLM maps everything to the form's fields (destination, dates, cost, purpose, etc.)
4. Any required fields the LLM could not fill are returned in `missingFields` so the client can prompt the employee

**Example Request**

```
POST /api/v1/workflow/form/chat
Content-Type: multipart/form-data

message    = "4월 10일부터 12일까지 부산 출장입니다. KTX 왕복 타고 숙박은 해운대 호텔이에요."
companyId  = 550e8400-e29b-41d4-a716-446655440001
formType   = TRAVEL_REQUEST
```

**Response** `200 OK`

```json
{
  "payload": {
    "sessionId": "session-form-abc123",
    "formType": "TRAVEL_REQUEST",
    "status": "IN_PROGRESS",
    "form": {
      "destination": "부산",
      "departureDate": "2026-04-10",
      "returnDate": "2026-04-12",
      "nights": 2,
      "transportType": "KTX",
      "transportCost": null,
      "accommodationName": "해운대 호텔",
      "accommodationCost": null,
      "purpose": null,
      "estimatedTotal": null,
      "category": "출장비"
    },
    "missingFields": [
      { "field": "transportCost",     "prompt": "KTX 왕복 비용이 얼마인가요?" },
      { "field": "accommodationCost", "prompt": "숙박 비용을 알려주세요." },
      { "field": "purpose",           "prompt": "출장 목적을 간단히 설명해 주세요." }
    ],
    "extractedFromFiles": []
  },
  "message": "Form partially filled. Please provide missing fields.",
  "code": 200,
  "status": "OK"
}
```

> The client should keep calling this endpoint, passing the same `sessionId`, until `missingFields` is empty. Then call `/submit`.

---

### POST `/api/v1/workflow/form/submit`

Once all fields are complete, the employee submits the form. This saves the request as a record with status `PENDING_APPROVAL`. No PDF is generated yet — that happens only after a manager approves it.

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `sessionId` | string | Yes | The session ID from the `/chat` conversation |
| `companyId` | UUID | Yes | Company scope |
| `form` | object | Yes | The completed form (same fields as returned by `/chat`) |
| `employeeId` | string | Yes | ID of the employee submitting the request |

**Response** `201 Created`

```json
{
  "payload": {
    "requestId": "REQ-20260410-0042",
    "formType": "TRAVEL_REQUEST",
    "status": "PENDING_APPROVAL",
    "submittedAt": "2026-04-05T11:00:00"
  },
  "message": "Request submitted. Waiting for manager approval.",
  "code": 201,
  "status": "CREATED"
}
```

---

### PUT `/api/v1/workflow/form/{requestId}/approve`

A manager approves or rejects a submitted request. On approval, the system generates the requisition as a PDF (via `FileStorageService`) and stores it. This PDF becomes the baseline document that Section 3 R10 uses to check whether the actual transaction matches the approved request.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `requestId` | string | The request ID returned by `/submit` |

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `decision` | string | Yes | `"APPROVED"` or `"REJECTED"` |
| `managerId` | string | Yes | ID of the approving manager |
| `comment` | string | No | Optional note, required if `"REJECTED"` |

**Response** `200 OK`

```json
{
  "payload": {
    "requestId": "REQ-20260410-0042",
    "status": "APPROVED",
    "approvedBy": "MGR-0010",
    "approvedAt": "2026-04-06T09:15:00",
    "requisitionFileId": "550e8400-e29b-41d4-a716-446655440080",
    "requisitionFileUrl": "/api/v1/storage/files/by-id/550e8400-e29b-41d4-a716-446655440080"
  },
  "message": "Request approved. Requisition PDF generated.",
  "code": 200,
  "status": "OK"
}
```

> `requisitionFileId` is the PDF that Section 3 R10 retrieves when auditing the matching transaction.

---

## 4.2 Natural Language Expense Query — MRS-Agent

Base path: `/api/v1/workflow/query`
Authentication: **Required**

Managers and finance staff can ask questions about expense data in plain Korean or English. The system converts the question into SQL and runs it against the database, returning the result as structured data. No SQL knowledge is required.

This is powered by **MRS-Agent**, a framework built specifically for large enterprise databases where the schema has many tables and column names are ambiguous. It solves the problem of context overload that causes standard single-pass LLM approaches to generate wrong SQL.

---

### How MRS-Agent Works

> Based on the paper: *MRS-Agent: A MapReduce-Inspired Two-Phase Framework for Enterprise NL-SQL*
> Benchmark result on BIRD: **77.04% Execution Accuracy**, +9.70% over its base model, outperforming Agentar-Scale-SQL.

**The problem it solves:** BizPlay's database has tables for companies, transactions, rules, categories, files, audit results, and more. Feeding the entire schema to an LLM at once causes context overload and incorrect table/column selection. MRS-Agent avoids this by filtering the schema down to only what is relevant before generating SQL.

```
User's question (plain language)
         │
         ▼
┌──────────────────────────────────────────────────┐
│  PHASE 1 — Find the relevant tables and columns  │
│                                                  │
│  Map step:                                       │
│    Split the full schema into small chunks       │
│    Ask the LLM: "which tables here are useful    │
│    for this question?" — run in parallel         │
│                                                  │
│  Reduce step:                                    │
│    Combine the scores from all chunks            │
│    Keep only the relevant tables/columns         │
│    Discard everything else                       │
└──────────────────────────┬───────────────────────┘
                           │ Small, focused schema
                           ▼
┌──────────────────────────────────────────────────┐
│  PHASE 2 — Generate and pick the best SQL        │
│                                                  │
│  Generate:                                       │
│    Write N different SQL queries for the         │
│    question (each from a slightly different      │
│    angle to cover ambiguous interpretations)     │
│                                                  │
│  Execute:                                        │
│    Run all N queries against the database        │
│    Collect which ones actually return results    │
│                                                  │
│  Judge (LLM-as-a-Judge):                         │
│    The LLM reviews the queries and their         │
│    results and picks the most correct one        │
└──────────────────────────┬───────────────────────┘
                           │
                           ▼
                Final SQL + Result rows
```

---

### POST `/api/v1/workflow/query`

Ask a question in plain language. Returns the matching data rows and, optionally, the SQL that was generated.

**Request Body** (`application/json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `companyId` | UUID | Yes | Only data belonging to this company will be queried |
| `question` | string | Yes | The question in Korean or English |
| `sessionId` | string | No | Reuse the same session for follow-up questions — skips Phase 1 on repeated calls |
| `returnSql` | boolean | No | Set `true` to see the generated SQL in the response. Default: `false` |
| `dryRun` | boolean | No | Set `true` to get the SQL without actually running it. Default: `false` |
| `topK` | integer | No | Maximum number of rows to return. Default: `50` |

**Example Requests**

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "question": "이번 달 교통비가 5만원을 초과한 직원 목록을 보여줘",
  "returnSql": true,
  "topK": 20
}
```

```json
{
  "companyId": "550e8400-e29b-41d4-a716-446655440001",
  "question": "지난 분기 가장 많이 사용된 가맹점 상위 10개는?",
  "sessionId": "session-query-xyz"
}
```

**Response** `200 OK`

```json
{
   "payload": {
      "question": "이번 달 교통비가 5만원을 초과한 직원 목록을 보여줘",
      "sessionId": "session-query-xyz",
      "sql": "SELECT employee_id, SUM(amount) AS total FROM transactions WHERE company_id = ? AND category = '교통비' AND approval_date >= DATE_TRUNC('month', CURRENT_DATE) GROUP BY employee_id HAVING SUM(amount) > 50000 ORDER BY total DESC",
      "columns": ["employee_id", "total"],
      "rows": [
         { "employee_id": "EMP-0042", "total": 87500 },
         { "employee_id": "EMP-0017", "total": 63200 }
      ],
      "totalRows": 2,
      "truncated": false,
      "agent": {
         "tablesScanned": 12,
         "tablesSelected": 3,
         "sqlCandidatesGenerated": 4,
         "sqlCandidatesExecuted": 4,
         "winnerCandidateIndex": 2,
         "model": "Qwen/Qwen2.5-72B-Instruct"
      }
   },
   "message": "Query executed successfully.",
   "code": 200,
   "status": "OK"
}
```

**Response Field Descriptions**

| Field | Description |
|---|---|
| `sql` | The generated SQL query. Only included when `returnSql: true` |
| `columns` | Column names in the result |
| `rows` | The actual data rows |
| `truncated` | `true` if there were more rows than `topK` allowed |
| `agent.tablesScanned` | How many tables were evaluated in Phase 1 Map step |
| `agent.tablesSelected` | How many tables remained after Phase 1 Reduce step |
| `agent.sqlCandidatesGenerated` | How many SQL queries were written in Phase 2 |
| `agent.sqlCandidatesExecuted` | How many of those ran successfully |
| `agent.winnerCandidateIndex` | Which candidate (1-based) the LLM Judge selected |
| `agent.model` | The LLM that ran both phases |

---

## 4.3 Design Notes

### Form Filling (Endpoint 1)

| Topic | Guidance |
|---|---|
| **Storing session state** | Save the in-progress form on the server (Redis or DB), keyed by `sessionId`. Never store it only on the client — the employee may switch devices mid-conversation |
| **Image input** | Reuse the same OCR/VLM pipeline from Section 3 R06. `dots.ocr` handles printed/digital documents; VLM handles handwritten ones |
| **Form templates** | Keep form field schemas (TRAVEL_REQUEST, SETTLEMENT) as versioned JSON in the DB so that changing a form does not break sessions already in progress |
| **When to call /submit** | Only when `missingFields` in the `/chat` response is empty |
| **When PDF is generated** | Only on `PUT /approve`, not on `/submit`. This ensures no PDF exists for requests that were never approved, keeping R10 from auditing unapproved requests |
| **R10 link** | The `requisitionFileId` returned by `/approve` is what Section 3 R10 uses. No separate registration step is needed — the audit engine looks up the requisition by `requestId` at audit time |

### NL-SQL / MRS-Agent (Endpoint 2)

| Topic | Guidance |
|---|---|
| **Schema format for Phase 1** | Represent each table as `table_name(col: type, ...)` with a short description line. Feed 3–5 tables per Map chunk |
| **Parallel execution** | Each Map chunk is an independent LLM call — run them with `CompletableFuture` in Spring Boot for low latency |
| **Session cache** | Cache the Phase 1 filtered schema per `sessionId`. Follow-up questions in the same session skip Phase 1 entirely |
| **Company data isolation** | Always hard-code `WHERE company_id = ?` as a mandatory predicate. Never let the LLM omit it |
| **Safe execution** | Only `SELECT` statements are allowed. `dryRun: true` is useful during development to inspect generated SQL without running it |
| **Model choice** | Qwen2.5-72B handles complex multi-table joins better. SKT A.X 4.0 is more natural for Korean business phrasing (e.g., "이번 분기", "전월 대비") |

---
