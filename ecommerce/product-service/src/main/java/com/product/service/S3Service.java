package com.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;

/**
 * Thin wrapper around the AWS SDK S3Client for product image uploads.
 *
 * Validates MIME type and file size before uploading.
 * Works with both real AWS S3 and local MinIO (configured via S3Config).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private static final long   MAX_FILE_SIZE  = 5 * 1024 * 1024L; // 5 MB
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:product-images}")
    private String bucket;

    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    @Value("${cloud.aws.s3.region:us-east-1}")
    private String region;

    /**
     * Validates and uploads a product image to S3 / MinIO.
     *
     * @param key  S3 object key, e.g. "products/42/thumbnail.jpg"
     * @param file multipart file from the request
     * @return public URL of the uploaded object
     * @throws IllegalArgumentException if MIME type or size is invalid
     */
    public String upload(String key, MultipartFile file) {
        validateFile(file);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded image to s3://{}/{}", bucket, key);
            return buildPublicUrl(key);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size " + (file.getSize() / 1024 / 1024) + " MB exceeds the 5 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported file type '" + contentType + "'. Allowed: " + ALLOWED_TYPES);
        }
    }

    private String buildPublicUrl(String key) {
        if (!endpoint.isBlank()) {
            // MinIO: http://localhost:9000/product-images/products/42/thumb.jpg
            return endpoint.replaceAll("/+$", "") + "/" + bucket + "/" + key;
        }
        // AWS S3: https://product-images.s3.us-east-1.amazonaws.com/products/42/thumb.jpg
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }
}
