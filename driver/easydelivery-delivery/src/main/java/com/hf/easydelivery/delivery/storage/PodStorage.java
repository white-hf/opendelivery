package com.hf.easydelivery.delivery.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class PodStorage {
    private final Path root;

    public PodStorage(@Value("${opendelivery.pod.storage-path:./data/pod}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public StoredPod store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("POD file is empty");
        try {
            Files.createDirectories(root);
            String extension = safeExtension(file.getOriginalFilename());
            Path target = root.resolve(UUID.randomUUID() + extension).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid POD filename");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredPod(target.toUri().toString(), HexFormat.of().formatHex(digest.digest()),
                    file.getContentType(), Files.size(target));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store POD file", ex);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unable to hash POD file", ex);
        }
    }

    private String safeExtension(String filename) {
        if (filename == null) return "";
        int index = filename.lastIndexOf('.');
        if (index < 0 || filename.length() - index > 10) return "";
        String extension = filename.substring(index).toLowerCase();
        return extension.matches("\\.[a-z0-9]+") ? extension : "";
    }

    public record StoredPod(String uri, String sha256, String contentType, long size) {}
}
