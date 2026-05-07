package ir.bita.esm.route.service;

import ir.bita.common.domain.FileType;
import ir.bita.esm.route.entity.Route;
import ir.bita.esm.route.entity.RouteFile;
import ir.bita.esm.route.repository.RouteFileRepository;
import ir.bita.esm.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing route files (WSDL, XSD, Properties, Java, JAR).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteFileService {

    private final RouteFileRepository fileRepository;
    private final RouteRepository routeRepository;

    @Value("${app.file.storage-path:/var/bita/route-files}")
    private String storagePath;

    /**
     * Maximum file size for inline storage (1MB).
     */
    private static final long MAX_INLINE_SIZE = 1024 * 1024;

    /**
     * Uploads a file and attaches it to a route.
     */
    @Transactional
    public RouteFile uploadFile(Long routeId, MultipartFile file, FileType fileType) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));

        // Validate file
        validateFile(file, fileType);

        // Check for duplicate filename
        if (fileRepository.existsByRouteIdAndFilename(routeId, file.getOriginalFilename())) {
            throw new IllegalArgumentException("File with name '" + file.getOriginalFilename() + "' already exists");
        }

        try {
            // Calculate hash
            String contentHash = calculateHash(file.getBytes());

            // Determine storage strategy
            String savedPath;
            String inlineContent = null;

            if (file.getSize() <= MAX_INLINE_SIZE && isTextFile(fileType)) {
                // Store content inline for small text files
                inlineContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                savedPath = "inline";
            } else {
                // Store on filesystem
                savedPath = saveToFilesystem(file, routeId);
            }

            // Create file entity
            RouteFile routeFile = RouteFile.builder()
                    .route(route)
                    .filename(file.getOriginalFilename())
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .storagePath(savedPath)
                    .contentHash(contentHash)
                    .content(inlineContent)
                    .build();

            routeFile = fileRepository.save(routeFile);

            log.info("Uploaded file '{}' to route {} (ID: {})",
                    file.getOriginalFilename(), routeId, routeFile.getId());

            return routeFile;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    /**
     * Downloads a file.
     */
    public Resource downloadFile(Long fileId) {
        RouteFile routeFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if ("inline".equals(routeFile.getStoragePath())) {
            // Return inline content as resource
            throw new UnsupportedOperationException("Inline files should be fetched via content field");
        }

        try {
            Path filePath = Paths.get(routeFile.getStoragePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + routeFile.getStoragePath());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path", e);
        }
    }

    /**
     * Gets a file by ID.
     */
    public RouteFile getFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
    }

    /**
     * Gets all files for a route.
     */
    public List<RouteFile> getFilesForRoute(Long routeId) {
        return fileRepository.findByRouteId(routeId);
    }

    /**
     * Deletes a file.
     */
    @Transactional
    public void deleteFile(Long routeId, Long fileId) {
        RouteFile file = fileRepository.findByIdAndRouteId(fileId, routeId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Delete from filesystem if not inline
        if (!"inline".equals(file.getStoragePath())) {
            try {
                Files.deleteIfExists(Paths.get(file.getStoragePath()));
            } catch (IOException e) {
                log.warn("Failed to delete file from filesystem: {}", file.getStoragePath());
            }
        }

        fileRepository.delete(file);
        log.info("Deleted file {} from route {}", fileId, routeId);
    }

    /**
     * Validates a file based on its type.
     */
    public void validateFile(MultipartFile file, FileType fileType) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename is required");
        }

        // Validate extension
        String extension = getFileExtension(filename).toLowerCase();
        boolean validExtension = switch (fileType) {
            case WSDL -> extension.equals("wsdl");
            case XSD -> extension.equals("xsd");
            case PROPERTIES -> extension.equals("properties");
            case JAVA -> extension.equals("java");
            case JAR -> extension.equals("jar");
            case OTHER -> true;
        };

        if (!validExtension) {
            throw new IllegalArgumentException(
                    String.format("Invalid file extension '%s' for type %s", extension, fileType));
        }

        // Validate content for WSDL and XSD
        if ((fileType == FileType.WSDL || fileType == FileType.XSD) && file.getSize() <= MAX_INLINE_SIZE) {
            try {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                validateXmlContent(content, fileType);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file content", e);
            }
        }
    }

    private void validateXmlContent(String content, FileType fileType) {
        // Basic XML validation
        if (!content.trim().startsWith("<?xml") && !content.trim().startsWith("<")) {
            throw new IllegalArgumentException("File does not appear to be valid XML");
        }

        // Check for required root elements
        if (fileType == FileType.WSDL && !content.contains("definitions")) {
            throw new IllegalArgumentException("WSDL file must contain <definitions> element");
        }
        if (fileType == FileType.XSD && !content.contains("schema")) {
            throw new IllegalArgumentException("XSD file must contain <schema> element");
        }
    }

    private String saveToFilesystem(MultipartFile file, Long routeId) throws IOException {
        // Create directory structure: /storage/route-files/{routeId}/
        Path directory = Paths.get(storagePath, String.valueOf(routeId));
        Files.createDirectories(directory);

        // Generate unique filename
        String uniqueFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = directory.resolve(uniqueFilename);

        // Write file
        Files.write(filePath, file.getBytes());

        return filePath.toString();
    }

    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private boolean isTextFile(FileType fileType) {
        return fileType == FileType.WSDL ||
               fileType == FileType.XSD ||
               fileType == FileType.PROPERTIES ||
               fileType == FileType.JAVA;
    }
}
