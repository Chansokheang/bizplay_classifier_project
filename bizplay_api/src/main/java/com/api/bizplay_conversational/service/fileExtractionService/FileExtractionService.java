package com.api.bizplay_conversational.service.fileExtractionService;

import java.util.Optional;

/**
 * Stores uploaded files so they can later be referenced by fileId (e.g. from /trip-plan).
 * Decouples the binary upload step from the JSON conversation endpoint.
 */
public interface FileExtractionService {

    /** Store raw file bytes and return a generated fileId. */
    String store(String filename, String contentType, byte[] content);

    /** Retrieve a previously stored file by id. */
    Optional<UploadedFile> get(String fileId);
}
