package com.api.bizplay_classifier_api.service.storageService;

import com.api.bizplay_classifier_api.model.response.FileStorageResponse;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImple implements FileStorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String publicBasePath;
    private final String requestFolder;
    private final boolean failFast;
    private volatile boolean minioReady;

    public FileStorageServiceImple(
            @Value("${app.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.storage.minio.bucket:bizplay-files}") String bucketName,
            @Value("${app.storage.minio.request-folder:request-files}") String requestFolder,
            @Value("${app.storage.minio.fail-fast:false}") boolean failFast,
            @Value("${app.storage.public-base-path:/api/v1/storage/files}") String publicBasePath
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucketName;
        this.requestFolder = normalizeFolder(requestFolder);
        this.failFast = failFast;
        this.publicBasePath = publicBasePath;
        this.minioReady = tryInitializeBucket();
        if (!this.minioReady && this.failFast) {
            throw new IllegalStateException("Unable to initialize MinIO bucket: " + bucketName);
        }
    }

    @Override
    public FileStorageResponse storeFile(MultipartFile file) {
        ensureMinioReady();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        String extension = "";
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot >= 0) {
            extension = originalFileName.substring(lastDot);
        }

        String storedFileName = buildObjectName(extension);

        try (InputStream inputStream = file.getInputStream()) {
            putObject(storedFileName, inputStream, file.getSize(), file.getContentType());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store file.", e);
        }

        String fileUrl = publicBasePath + "/" + storedFileName;
        log.info("Stored file '{}' as '{}'", originalFileName, storedFileName);

        return FileStorageResponse.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .fileUrl(fileUrl)
                .size(file.getSize())
                .contentType(file.getContentType())
                .build();
    }

    @Override
    public FileStorageResponse storeBytes(byte[] bytes, String originalFileName, String contentType) {
        ensureMinioReady();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File bytes are required.");
        }
        String cleanedOriginal = StringUtils.cleanPath(
                originalFileName == null || originalFileName.isBlank() ? "file.xlsx" : originalFileName
        );
        if (cleanedOriginal.contains("..")) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        String extension = "";
        int lastDot = cleanedOriginal.lastIndexOf('.');
        if (lastDot >= 0) {
            extension = cleanedOriginal.substring(lastDot);
        }

        String storedFileName = buildObjectName(extension);
        try (InputStream inputStream = new java.io.ByteArrayInputStream(bytes)) {
            putObject(storedFileName, inputStream, bytes.length, contentType);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store file bytes.", e);
        }

        String fileUrl = publicBasePath + "/" + storedFileName;
        return FileStorageResponse.builder()
                .originalFileName(cleanedOriginal)
                .storedFileName(storedFileName)
                .fileUrl(fileUrl)
                .size((long) bytes.length)
                .contentType(contentType)
                .build();
    }

    private void putObject(String storedFileName, InputStream inputStream, long size, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(storedFileName)
                        .stream(inputStream, size, -1)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .build()
        );
    }

    private String buildObjectName(String extension) {
        String fileName = UUID.randomUUID() + extension;
        if (requestFolder.isBlank()) {
            return fileName;
        }
        return requestFolder + "/" + fileName;
    }

    private String normalizeFolder(String folder) {
        if (folder == null) {
            return "";
        }
        String cleaned = folder.trim().replace("\\", "/");
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.contains("..")) {
            throw new IllegalArgumentException("Invalid request-folder path.");
        }
        return cleaned;
    }

    @Override
    public Resource loadAsResource(String storedFileName) {
        ensureMinioReady();
        String cleaned = StringUtils.cleanPath(storedFileName);
        if (cleaned.contains("..")) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(cleaned)
                        .build()
        )) {
            byte[] bytes = stream.readAllBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return cleaned;
                }
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("File not found: " + storedFileName, e);
        }
    }

    private synchronized boolean tryInitializeBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
            log.info("MinIO storage is ready. bucket={}", bucketName);
            return true;
        } catch (Exception e) {
            log.warn("MinIO is not available now. endpoint/bucket check failed: {}", e.getMessage());
            return false;
        }
    }

    private void ensureMinioReady() {
        if (minioReady) {
            return;
        }
        minioReady = tryInitializeBucket();
        if (!minioReady) {
            throw new IllegalStateException("MinIO is unavailable. Start MinIO and retry.");
        }
    }
}
