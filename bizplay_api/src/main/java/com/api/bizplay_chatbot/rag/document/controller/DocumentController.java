package com.api.bizplay_chatbot.rag.document.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.rag.document.dto.DocumentResponse;
import com.api.bizplay_chatbot.rag.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "Documents",
        description = "Upload, list, and manage knowledge-base documents. Every document belongs to "
                + "exactly one bot — only chats with the same bot retrieve from a document's chunks.")
@RestController
@RequestMapping("/chatbot/api/v1/rag/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a document for a specific bot",
            description = "Upload a PDF, DOCX, or TXT file with a title, scoped to one bot. "
                    + "The file is parsed, chunked, embedded with bot_id in chunk metadata, and stored. "
                    + "Only chats with the same bot will retrieve from this document.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @Parameter(description = "Bot UUID this document belongs to")
            @RequestParam("botId") UUID botId,
            @Parameter(description = "File to upload (PDF, DOCX, or TXT)")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Document title")
            @RequestParam("title") String title) {
        log.info("POST /chatbot/api/v1/rag/documents/upload - botId={}, file={}, size={}, title={}",
                botId, file.getOriginalFilename(), file.getSize(), title);
        DocumentResponse response = documentService.upload(botId, file, title);
        log.info("POST /chatbot/api/v1/rag/documents/upload - completed, docId={}, status={}",
                response.getId(), response.getEmbeddingStatus());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "List documents for a bot",
            description = "Returns metadata for every document uploaded to the given bot.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list(
            @Parameter(description = "Bot UUID — required") @RequestParam("botId") UUID botId) {
        log.info("GET /chatbot/api/v1/rag/documents - botId={}", botId);
        List<DocumentResponse> docs = documentService.listByBot(botId);
        log.info("GET /chatbot/api/v1/rag/documents - returning {} documents for bot {}", docs.size(), botId);
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    @Operation(summary = "Get document by ID",
            description = "Retrieve metadata (incl. owning botId) for a single document.")
    @GetMapping("/{docId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> get(
            @Parameter(description = "Document UUID") @PathVariable UUID docId) {
        log.info("GET /chatbot/api/v1/rag/documents/{}", docId);
        return ResponseEntity.ok(ApiResponse.ok(documentService.getById(docId)));
    }

    @Operation(summary = "View/download a document", description = "View the original uploaded file inline or download it")
    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> download(
            @Parameter(description = "Document UUID") @PathVariable UUID docId) {
        log.info("GET /chatbot/api/v1/rag/documents/{}/download", docId);
        DocumentResponse doc = documentService.getById(docId);
        Path filePath = documentService.getFilePath(docId);
        Resource resource = new FileSystemResource(filePath);

        String contentType = doc.getContentType() != null
                ? doc.getContentType()
                : "application/octet-stream";

        // RFC 5987 encoding via Spring's ContentDisposition builder. Without it,
        // Tomcat strips the header when the filename contains non-ASCII (HTTP/1.1
        // headers are limited to ISO-8859-1 / codepoints 0-255). The builder
        // emits filename*=UTF-8''<percent-encoded> alongside an ASCII fallback,
        // which every modern browser understands.
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(filePath.getFileName().toString(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @Operation(summary = "Delete a document",
            description = "Remove a document, the file on disk under uploads/{docId}/, and every "
                    + "vector chunk whose metadata.doc_id matches. Other documents on the same bot "
                    + "are unaffected — to remove all documents of a bot in one shot, delete the "
                    + "bot itself via DELETE /chatbot/api/v1/bots/{id} which cascades.")
    @DeleteMapping("/{docId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Document UUID") @PathVariable UUID docId) {
        log.info("DELETE /chatbot/api/v1/rag/documents/{}", docId);
        documentService.delete(docId);
        log.info("DELETE /chatbot/api/v1/rag/documents/{} - deleted", docId);
        return ResponseEntity.ok(ApiResponse.ok("Document deleted", null));
    }
}
