package ca.admin.delivermore.data.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LogViewerService {

    public static final String CURRENT_FILE_ID = "__CURRENT__";
    private static final long MAX_TAIL_BYTES = 512 * 1024;

    @Value("${logviewer.sources.admin.current-file:./logs/dm_admin.log}")
    private String adminCurrentFile;

    @Value("${logviewer.sources.admin.archive-dir:./logs/archived}")
    private String adminArchiveDir;

    @Value("${logviewer.sources.collector.current-file:../dm_collector/logs/dm_collector.log}")
    private String collectorCurrentFile;

    @Value("${logviewer.sources.collector.archive-dir:../dm_collector/logs/archived}")
    private String collectorArchiveDir;

    public Map<String, String> getSourceOptions() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("admin", "DM Admin");
        sources.put("collector", "Collector");
        return sources;
    }

    public List<LogFileItem> listFiles(String sourceKey) {
        Path current = currentFilePath(sourceKey);
        Path archiveDir = archiveDirPath(sourceKey);

        List<LogFileItem> items = new ArrayList<>();
        items.add(new LogFileItem(
                CURRENT_FILE_ID,
                "Current - " + current.getFileName(),
                current,
                Files.exists(current),
                Files.exists(current) ? lastModified(current) : null));

        if (Files.isDirectory(archiveDir)) {
            try (Stream<Path> stream = Files.list(archiveDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".log"))
                        .sorted(Comparator.comparing(this::lastModified).reversed())
                        .forEach(path -> items.add(new LogFileItem(
                                path.getFileName().toString(),
                                path.getFileName().toString(),
                                path,
                                true,
                                lastModified(path))));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to list archived log files for source " + sourceKey, ex);
            }
        }

        return items;
    }

    public String tail(String sourceKey, String fileId, int lines) {
        Path file = resolveFile(sourceKey, fileId);
        if (!Files.exists(file)) {
            return "Log file not found: " + file;
        }
        int safeLines = Math.max(10, Math.min(lines, 10000));

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) {
                return "";
            }

            long pointer = length - 1;
            int found = 0;
            while (pointer >= 0) {
                raf.seek(pointer);
                int b = raf.read();
                if (b == '\n') {
                    found++;
                    if (found > safeLines) {
                        pointer++;
                        break;
                    }
                }
                pointer--;
            }

            long start = Math.max(0, pointer + 1);
            start = Math.max(start, length - MAX_TAIL_BYTES);
            int toRead = (int) (length - start);
            byte[] bytes = new byte[toRead];
            raf.seek(start);
            raf.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read log file " + file, ex);
        }
    }

    public LogChunk readChunk(String sourceKey, String fileId, long offset, int maxBytes) {
        Path file = resolveFile(sourceKey, fileId);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Log file not found: " + file);
        }

        long safeOffset = Math.max(0, offset);
        int safeMax = Math.max(1024, Math.min(maxBytes, 1024 * 1024));

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = raf.length();
            boolean rotated = fileLength < safeOffset;
            long readFrom = rotated ? 0 : safeOffset;
            long available = Math.max(0, fileLength - readFrom);
            int toRead = (int) Math.min(safeMax, available);

            if (toRead == 0) {
                return new LogChunk("", fileLength, rotated);
            }

            byte[] buffer = new byte[toRead];
            raf.seek(readFrom);
            raf.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            long nextOffset = readFrom + toRead;
            return new LogChunk(text, nextOffset, rotated);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read log chunk from " + file, ex);
        }
    }

    public LogBackChunk readChunkBefore(String sourceKey, String fileId, long beforeOffset, int maxBytes) {
        Path file = resolveFile(sourceKey, fileId);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Log file not found: " + file);
        }

        long safeBefore = Math.max(0, beforeOffset);
        int safeMax = Math.max(1024, Math.min(maxBytes, 1024 * 1024));

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long fileLength = raf.length();
            long end = Math.min(safeBefore, fileLength);
            long start = Math.max(0, end - safeMax);
            int toRead = (int) (end - start);

            if (toRead == 0) {
                return new LogBackChunk("", start, end);
            }

            byte[] buffer = new byte[toRead];
            raf.seek(start);
            raf.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            return new LogBackChunk(text, start, end);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read older log chunk from " + file, ex);
        }
    }

    public long fileSize(String sourceKey, String fileId) {
        Path file = resolveFile(sourceKey, fileId);
        try {
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read log size for " + file, ex);
        }
    }

    public InputStream openFileStream(String sourceKey, String fileId) {
        Path file = resolveFile(sourceKey, fileId);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Log file not found: " + file);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to open log stream for " + file, ex);
        }
    }

    public String fileName(String sourceKey, String fileId) {
        return resolveFile(sourceKey, fileId).getFileName().toString();
    }

    private Path resolveFile(String sourceKey, String fileId) {
        if (CURRENT_FILE_ID.equals(fileId)) {
            return currentFilePath(sourceKey);
        }

        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("Missing log file id");
        }

        if (fileId.contains("/") || fileId.contains("\\") || fileId.contains("..")) {
            throw new IllegalArgumentException("Invalid log file id");
        }

        Path archiveDir = archiveDirPath(sourceKey);
        Path candidate = archiveDir.resolve(fileId).toAbsolutePath().normalize();
        Path archiveRoot = archiveDir.toAbsolutePath().normalize();

        if (!candidate.startsWith(archiveRoot)) {
            throw new IllegalArgumentException("Invalid log file path");
        }

        return candidate;
    }

    private Path currentFilePath(String sourceKey) {
        return switch (sourceKey) {
            case "admin" -> normalizePath(adminCurrentFile, "logviewer.sources.admin.current-file");
            case "collector" -> normalizePath(collectorCurrentFile, "logviewer.sources.collector.current-file");
            default -> throw new IllegalArgumentException("Unknown source key: " + sourceKey);
        };
    }

    private Path archiveDirPath(String sourceKey) {
        return switch (sourceKey) {
            case "admin" -> normalizePath(adminArchiveDir, "logviewer.sources.admin.archive-dir");
            case "collector" -> normalizePath(collectorArchiveDir, "logviewer.sources.collector.archive-dir");
            default -> throw new IllegalArgumentException("Unknown source key: " + sourceKey);
        };
    }

    private Path normalizePath(String pathValue, String propertyName) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalStateException("Missing required log viewer property: " + propertyName);
        }
        return Paths.get(pathValue).toAbsolutePath().normalize();
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    public record LogFileItem(
            String id,
            String displayName,
            Path path,
            boolean available,
            Instant lastModified) {
    }

    public record LogChunk(String text, long nextOffset, boolean rotated) {
    }

    public record LogBackChunk(String text, long startOffset, long endOffset) {
    }
}
