package com.example.jfrjdkapi.service;

import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that continuously records JFR events and periodically exports them to files.
 * Uses the JDK Flight Recorder API to create snapshots without stopping the application.
 */
@Service
public class JfrJdkApiService {

    private static final Logger logger = LoggerFactory.getLogger(JfrJdkApiService.class);
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Value("${jfr.export.directory:./jfr-exports}")
    private String exportDirectory;

    @Value("${jfr.export.interval.seconds:15}")
    private int exportIntervalSeconds;

    @Value("${jfr.recording.max-age.seconds:15}")
    private int maxAgeSeconds;

    private Recording recording;
    private final AtomicLong exportCounter = new AtomicLong(0);
    private final AtomicLong eventCounter = new AtomicLong(0);

    @PostConstruct
    public void startRecording() {
        try {
            // Create export directory if it doesn't exist
            Path exportPath = Paths.get(exportDirectory);
            Files.createDirectories(exportPath);
            logger.info("JFR export directory: {}", exportPath.toAbsolutePath());

            // Create and configure continuous recording
            recording = new Recording();
            recording.setName("ContinuousRecording");
            recording.setMaxAge(Duration.ofSeconds(maxAgeSeconds));
            recording.setToDisk(true);

            // Enable event types to mirror the settings in the root jfr-config.jfc
            // (keep this list in sync with that file).

            // Method Profiling - sample every 10ms
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));

            // CPU Load
            recording.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));

            // Thread Statistics
            recording.enable("jdk.JavaThreadStatistics").withPeriod(Duration.ofSeconds(1));

            // GC Events
            recording.enable("jdk.GarbageCollection").withThreshold(Duration.ZERO);
            recording.enable("jdk.YoungGarbageCollection").withThreshold(Duration.ZERO);
            recording.enable("jdk.OldGarbageCollection").withThreshold(Duration.ZERO);

            // Memory Allocation
            recording.enable("jdk.ObjectAllocationInNewTLAB").withStackTrace();
            recording.enable("jdk.ObjectAllocationOutsideTLAB").withStackTrace();

            // Thread Events
            recording.enable("jdk.ThreadStart").withStackTrace();
            recording.enable("jdk.ThreadEnd");

            // Exception Events
            recording.enable("jdk.JavaExceptionThrow").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.JavaErrorThrow").withStackTrace();

            // I/O Events
            recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.SocketRead").withThreshold(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.SocketWrite").withThreshold(Duration.ofMillis(10)).withStackTrace();

            // Class Loading
            recording.enable("jdk.ClassLoad").withStackTrace();

            // Compilation
            recording.enable("jdk.Compilation").withThreshold(Duration.ofMillis(100));

            // Monitor Events
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(10)).withStackTrace();

            // Environment
            recording.enable("jdk.InitialEnvironmentVariable");

            recording.start();
            logger.info("JFR continuous recording started - exporting every {} seconds", exportIntervalSeconds);

        } catch (Exception e) {
            logger.error("Failed to start JFR recording", e);
        }
    }

    /**
     * Scheduled task that exports JFR events to a file at regular intervals.
     * The recording continues running while we take a snapshot.
     */
    @Scheduled(fixedDelayString = "${jfr.export.interval.seconds:15}000", initialDelay = 30000)
    public void exportEvents() {
        if (recording == null) {
            logger.warn("Recording is not initialized, skipping export");
            return;
        }

        long exportNum = exportCounter.incrementAndGet();
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        String filename = String.format("jfr-export-%s-%04d.jfr", timestamp, exportNum);
        Path exportFile = Paths.get(exportDirectory, filename);

        try {
            Instant startTime = Instant.now();

            // Create a copy/snapshot of the current recording data
            // This doesn't stop the main recording
            recording.dump(exportFile);

            // Count events in the exported file
            long eventCount = countEventsInFile(exportFile);
            eventCounter.addAndGet(eventCount);

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            logger.info("Exported JFR snapshot #{} to {} ({} events, {} ms)",
                    exportNum, exportFile.getFileName(), eventCount, durationMs);

        } catch (IOException e) {
            logger.error("Failed to export JFR snapshot #{}", exportNum, e);
        }
    }

    /**
     * Counts the number of events in a JFR file.
     */
    private long countEventsInFile(Path jfrFile) {
        long count = 0;
        try (RecordingFile recordingFile = new RecordingFile(jfrFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (event != null) {
                    count++;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to count events in {}", jfrFile.getFileName(), e);
        }
        return count;
    }

    @PreDestroy
    public void stopRecording() {
        if (recording != null) {
            try {
                logger.info("Stopping JFR recording (total exports: {}, total events processed: {})",
                        exportCounter.get(), eventCounter.get());
                // The recording may already be stopped (e.g. JFR stops it once maxAge
                // elapses), so only stop it when it is still running to avoid an
                // IllegalStateException ("Can't stop an already stopped recording").
                if (recording.getState() == RecordingState.RUNNING) {
                    recording.stop();
                }
            } catch (Exception e) {
                logger.error("Error stopping JFR recording", e);
            } finally {
                // close() releases the recording's resources and is safe to call
                // regardless of state, so it always runs.
                recording.close();
            }
        }
    }

    public long getExportCount() {
        return exportCounter.get();
    }

    public long getTotalEventCount() {
        return eventCounter.get();
    }

    public String getRecordingState() {
        return recording != null ? "RUNNING" : "NOT_STARTED";
    }
}
