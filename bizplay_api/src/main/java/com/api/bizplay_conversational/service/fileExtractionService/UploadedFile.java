package com.api.bizplay_conversational.service.fileExtractionService;

/** A file held in the upload store, keyed by fileId. */
public record UploadedFile(String fileId, String filename, String contentType, byte[] content) {
}
