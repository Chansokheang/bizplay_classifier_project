package com.api.bizplay_chatbot.rag.document.service;

import com.api.bizplay_chatbot.bot.service.BotService;
import com.api.bizplay_chatbot.common.exception.BusinessException;
import com.api.bizplay_chatbot.domain.entity.Bot;
import com.api.bizplay_chatbot.domain.entity.Document;
import com.api.bizplay_chatbot.domain.enums.EmbeddingStatus;
import com.api.bizplay_chatbot.domain.repository.DocumentRepository;
import com.api.bizplay_chatbot.rag.document.dto.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentParserService parserService;
    private final BotService botService;

    @Value("${app.rag.chunk-size:800}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${app.rag.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Upload flow:
     * 1. Resolve the owning bot (404 if missing). Disabled bots are
     *    intentionally allowed — the new-bots-default-to-disabled rule
     *    expects operators to upload documents and tune the prompt while
     *    the bot is still disabled, then flip on with PATCH .../enable.
     *    Disabled bots still reject chat traffic at ChatService.chat(),
     *    so there's no retrieval-from-an-unrelated-corp risk here.
     * 2. Persist document record bound to the bot, status PROCESSING
     * 3. Extract text, chunk, embed — every chunk's metadata carries bot_id
     *    so vector retrieval at chat time can filter by bot
     * 4. Update status to COMPLETED or FAILED
     */
    @Transactional
    public DocumentResponse upload(UUID botId, MultipartFile file, String title) {
        Bot bot = botService.loadActiveBot(botId);

        Document doc = new Document();
        doc.setBot(bot);
        doc.setTitle(title);
        doc.setContentType(file.getContentType());
        doc.setFileName(file.getOriginalFilename());
        doc.setEmbeddingStatus(EmbeddingStatus.PROCESSING);
        doc = documentRepository.save(doc);

        try {
            // Save original file to disk for later download
            saveFile(doc.getId(), file);

            String text = parserService.extractText(file);
            List<String> chunks = parserService.chunk(text, chunkSize, chunkOverlap);
            embedChunks(doc, chunks);
            doc.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
        } catch (IOException e) {
            doc.setEmbeddingStatus(EmbeddingStatus.FAILED);
            log.error("Failed to process document {}: {}", doc.getId(), e.getMessage(), e);
        }

        documentRepository.save(doc);
        return toResponse(doc);
    }

    /**
     * List documents for a specific bot (the only meaningful scope now —
     * documents always belong to exactly one bot).
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listByBot(UUID botId) {
        botService.loadActiveBot(botId); // tenant-scoped existence check
        return documentRepository.findAllByBotIdOrderByCreatedAtDesc(botId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Document not found", HttpStatus.NOT_FOUND));
        return toResponse(doc);
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Document not found", HttpStatus.NOT_FOUND));

        // Delete vector chunks by doc_id in metadata JSONB column.
        // VectorStore.delete() only works with vector_store primary key IDs,
        // not metadata values — so we use direct SQL to match on metadata->>'doc_id'.
        int deleted = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'doc_id' = ?",
                id.toString());
        log.info("Deleted {} vector chunks for document {}", deleted, id);

        // Remove saved file from disk
        deleteFile(id);

        documentRepository.delete(doc);
    }

    /** Save uploaded file to disk: uploads/{docId}/{originalFileName} */
    private void saveFile(UUID docId, MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadDir, docId.toString());
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(file.getOriginalFilename()));
    }

    /** Delete saved file directory for a document */
    private void deleteFile(UUID docId) {
        try {
            Path dir = Paths.get(uploadDir, docId.toString());
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException e) {
                                log.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Failed to clean up files for document {}: {}", docId, e.getMessage());
        }
    }

    /** Resolve the file path on disk for a document */
    public Path getFilePath(UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BusinessException("Document not found", HttpStatus.NOT_FOUND));
        if (doc.getFileName() == null) {
            throw new BusinessException("No file stored for this document", HttpStatus.NOT_FOUND);
        }
        Path path = Paths.get(uploadDir, docId.toString(), doc.getFileName());
        if (!Files.exists(path)) {
            throw new BusinessException("File not found on disk", HttpStatus.NOT_FOUND);
        }
        return path;
    }

    /**
     * Build Spring AI Documents with tracking metadata and store in vector DB.
     * Metadata stored per chunk:
     * - bot_id:       bot UUID — used for bot-scoped similarity search filtering
     *                 and bot-cascade vector deletion
     * - doc_id:       document UUID — used for per-document deletion
     * - title:        document title for source attribution in chat responses
     * - file_name:    original filename for user-friendly source references
     * - content_type: MIME type (e.g., "application/pdf") for UI file type badges
     * - chunk_index:  sequential position within the document for traceability
     * - created_at:   upload timestamp for potential time-aware retrieval
     */
    private void embedChunks(Document doc, List<String> chunks) {
        List<org.springframework.ai.document.Document> aiDocs = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("bot_id", doc.getBot().getId().toString());
            meta.put("doc_id", doc.getId().toString());
            meta.put("title", doc.getTitle());
            meta.put("file_name", doc.getFileName() != null ? doc.getFileName() : "");
            meta.put("content_type", doc.getContentType() != null ? doc.getContentType() : "");
            meta.put("chunk_index", i);
            meta.put("created_at", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : "");
            aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), meta));
        }

        vectorStore.add(aiDocs);
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .botId(doc.getBot() != null ? doc.getBot().getId() : null)
                .title(doc.getTitle())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .embeddingStatus(doc.getEmbeddingStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
