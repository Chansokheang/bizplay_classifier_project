# BizPlay Classifier API

`bizplay_classifier` is company expense classification workflows.
It can create company. Within the company, user can manage rule and category, Excel transaction-file upload and use it to classify whether it fall into any type of the category based on rule, and chatbot configuration.

The system supports rule-based processing of uploaded Excel transaction files, stores processed files in MinIO-compatible object storage, and exposes endpoints to retrieve file metadata by company.
It is designed as a modular service layer + MyBatis repository architecture on PostgreSQL, with JWT-based security and OpenAPI/Swagger for API testing.

