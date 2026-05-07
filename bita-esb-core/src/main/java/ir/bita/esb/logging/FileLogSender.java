package ir.bita.esb.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Log sender for file backup.
 */
@Slf4j
public class FileLogSender implements LogSender {

    private final Path logDirectory;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();
    private BufferedWriter currentWriter;
    private LocalDate currentDate;

    public FileLogSender(String logDirectory) {
        this.logDirectory = Path.of(logDirectory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        
        try {
            Files.createDirectories(this.logDirectory);
        } catch (IOException e) {
            log.error("Failed to create log directory", e);
        }
    }

    @Override
    public void send(LogEntry entry) {
        lock.lock();
        try {
            ensureWriter();
            String json = objectMapper.writeValueAsString(entry);
            currentWriter.write(json);
            currentWriter.newLine();
            currentWriter.flush();
        } catch (Exception e) {
            log.error("Error writing log to file", e);
        } finally {
            lock.unlock();
        }
    }

    private void ensureWriter() throws IOException {
        LocalDate today = LocalDate.now();
        
        if (currentWriter == null || !today.equals(currentDate)) {
            closeWriter();
            
            String filename = "esb-logs-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".jsonl";
            Path logFile = logDirectory.resolve(filename);
            
            currentWriter = Files.newBufferedWriter(logFile, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            currentDate = today;
            
            log.info("Opened log file: {}", logFile);
        }
    }

    private void closeWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                log.error("Error closing log writer", e);
            }
            currentWriter = null;
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closeWriter();
        } finally {
            lock.unlock();
        }
    }
}
