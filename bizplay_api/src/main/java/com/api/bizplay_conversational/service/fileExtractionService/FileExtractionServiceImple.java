package com.api.bizplay_conversational.service.fileExtractionService;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores conversational uploads in MinIO (object storage). Self-contained to the
 * conversational module: uses its own MinioClient bean and its own bucket/folder config.
 * The returned fileId is the MinIO object key.
 */
@Slf4j
@Service
public class FileExtractionServiceImple implements FileExtractionService {

    private static final String META_FILENAME = "filename";

    private final MinioClient minioClient;
    private final String bucket;
    private final String folder;
    private volatile boolean ready;

    public FileExtractionServiceImple(
            @Qualifier("conversationalMinioClient") MinioClient minioClient,
            @Value("${app.conversational.minio.bucket:bizplay-conversational}") String bucket,
            @Value("${app.conversational.minio.folder:uploads}") String folder) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.folder = normalizeFolder(folder);
        this.ready = ensureBucket();
    }

    @Override
    public String store(String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File content is required.");
        }
        ensureReady();

        String safeName = (filename == null || filename.isBlank()) ? "file" : filename;
        // The client-facing fileId is a bare UUID; the MinIO object key is derived from it
        // internally, so the path/extension never leaks to the caller.
        String fileId = UUID.randomUUID().toString();
        String objectKey = keyFor(fileId);

        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .userMetadata(java.util.Map.of(META_FILENAME, safeName))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store uploaded file in MinIO.", e);
        }

        log.info("Stored uploaded file fileId={}, objectKey={}, name={}, bytes={}",
                fileId, objectKey, safeName, content.length);
        return fileId;
    }

    /** Map a bare-UUID fileId to its MinIO object key. */
    private String keyFor(String fileId) {
        return folder.isBlank() ? fileId : folder + "/" + fileId;
    }

    @Override
    public Optional<UploadedFile> get(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        ensureReady();
        String id = fileId.trim();
        String objectKey = keyFor(id);
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            String filename = stat.userMetadata() != null ? stat.userMetadata().get(META_FILENAME) : null;
            String contentType = stat.contentType();
            try (GetObjectResponse stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                byte[] content = stream.readAllBytes();
                return Optional.of(new UploadedFile(id, filename, contentType, content));
            }
        } catch (Exception e) {
            log.warn("Could not load uploaded file fileId={}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized boolean ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            log.info("Conversational MinIO storage ready. bucket={}", bucket);
            return true;
        } catch (Exception e) {
            log.warn("Conversational MinIO not available: {}", e.getMessage());
            return false;
        }
    }

    private void ensureReady() {
        if (ready) {
            return;
        }
        ready = ensureBucket();
        if (!ready) {
            throw new IllegalStateException("MinIO is unavailable. Start MinIO and retry.");
        }
    }

    private String normalizeFolder(String f) {
        if (f == null) {
            return "";
        }
        String cleaned = f.trim().replace("\\", "/");
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.contains("..")) {
            throw new IllegalArgumentException("Invalid folder path.");
        }
        return cleaned;
    }
}
