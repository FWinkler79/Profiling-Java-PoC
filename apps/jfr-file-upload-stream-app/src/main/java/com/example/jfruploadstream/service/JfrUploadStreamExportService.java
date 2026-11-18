package com.example.jfruploadstream.service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that continuously records JFR events and periodically exports them to an OTLP backend.
 * Uses OpenTelemetry Logs API to send each 15-second JFR recording slice as a single log record
 * whose body is the raw .jfr bytes (OTLP {@code bytes_value} — no base64 encoding).
 *
 * The consumer receives an opaque .jfr blob per slice that can be written to disk and opened
 * in JDK Mission Control for full post-hoc analysis.
 */
@Service
public class JfrUploadStreamExportService {

    private static final Logger logger = LoggerFactory.getLogger(JfrUploadStreamExportService.class);

    @Value("${jfr.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${jfr.export.interval.seconds:15}")
    private int exportIntervalSeconds;

    @Value("${jfr.recording.max-age.seconds:15}")
    private int maxAgeSeconds;

    @Value("${jfr.instrumentation.scope.name:jfr-file-upload-stream-app}")
    private String scopeName;

    @Value("${jfr.instrumentation.scope.version:1.0.0}")
    private String scopeVersion;

    private Recording recording;
    private SdkLoggerProvider loggerProvider;
    private LogRecordExporter exporter;
    private final AtomicLong exportCounter = new AtomicLong(0);
    private final AtomicLong successfulExports = new AtomicLong(0);
    private final AtomicLong failedExports = new AtomicLong(0);
    private final AtomicLong totalBytesExported = new AtomicLong(0);

    @PostConstruct
    public void startRecording() {
        try {
            // Initialize OTLP log exporter
            exporter = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(Duration.ofSeconds(30))
                    .build();

            // Create logger provider with batch processor
            Resource resource = Resource.create(
                    Attributes.of(
                            AttributeKey.stringKey("service.name"), scopeName,
                            AttributeKey.stringKey("service.version"), scopeVersion,
                            AttributeKey.stringKey("telemetry.sdk.name"), "jfr-otel-exporter",
                            AttributeKey.stringKey("telemetry.sdk.language"), "java"
                    )
            );

            loggerProvider = SdkLoggerProvider.builder()
                    .setResource(resource)
                    .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter)
                            .setScheduleDelay(5, TimeUnit.SECONDS)
                            .build())
                    .build();

            logger.info("OTLP Log Exporter configured for endpoint: {}", otlpEndpoint);

            // Create and configure continuous recording
            recording = new Recording();
            recording.setName("ContinuousOtelRecording");
            recording.setMaxAge(Duration.ofSeconds(maxAgeSeconds));
            recording.setToDisk(true);

            // Enable event types to mirror the settings in the root jfr-config.jfc

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
            logger.info("JFR continuous recording started - exporting to OTLP every {} seconds", exportIntervalSeconds);

        } catch (Exception e) {
            logger.error("Failed to start JFR recording", e);
        }
    }

    /**
     * Scheduled task that exports JFR events to OTLP at regular intervals.
     * The recording continues running while we take a snapshot and export it.
     */
    @Scheduled(fixedDelayString = "${jfr.export.interval.seconds:15}000", initialDelay = 30000)
    public void exportToOtlp() {
        if (recording == null || loggerProvider == null) {
            logger.warn("Recording or logger provider not initialized, skipping export");
            return;
        }

        long exportNum = exportCounter.incrementAndGet();
        Path tempFile = null;

        try {
            Instant startTime = Instant.now();

            // Create temporary file for JFR snapshot
            tempFile = Files.createTempFile("jfr-snapshot", ".jfr");

            // Dump recording to temporary file
            recording.dump(tempFile);

            // Read raw JFR bytes and export as a single log record
            long byteCount = exportRecordingAsLog(tempFile);
            totalBytesExported.addAndGet(byteCount);

            // Force flush and check whether it succeeded before counting as a success
            boolean flushed = loggerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess();

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            if (flushed) {
                successfulExports.incrementAndGet();
                logger.info("Successfully exported {} bytes of JFR recording (slice #{}) to OTLP ({} ms)",
                        byteCount, exportNum, durationMs);
            } else {
                failedExports.incrementAndGet();
                logger.warn("Flush timed out or failed for JFR slice #{} ({} bytes, {} ms) — backend may not have received it",
                        exportNum, byteCount, durationMs);
            }

        } catch (Exception e) {
            failedExports.incrementAndGet();
            logger.error("Error during OTLP export #{}", exportNum, e);
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Reads the raw JFR recording bytes from the dumped snapshot and emits a single
     * OpenTelemetry log record whose body is the binary .jfr content (OTLP bytes_value).
     * Returns the number of bytes exported.
     */
    private long exportRecordingAsLog(Path jfrFilePath) throws IOException {
        byte[] jfrBytes = Files.readAllBytes(jfrFilePath);

        Attributes attrs = Attributes.builder()
                .put("jfr.recording.format", "jfr")
                .put(AttributeKey.longKey("jfr.recording.size_bytes"), (long) jfrBytes.length)
                .put(AttributeKey.longKey("jfr.slice.duration_seconds"), (long) maxAgeSeconds)
                .build();

        loggerProvider.get(scopeName)
                .logRecordBuilder()
                .setTimestamp(Instant.now())
                .setSeverity(Severity.INFO)
                .setBody(io.opentelemetry.api.common.Value.of(jfrBytes))
                .setAllAttributes(attrs)
                .emit();

        return jfrBytes.length;
    }

    @PreDestroy
    public void stopRecording() {
        if (recording != null) {
            try {
                logger.info("Stopping JFR recording (total exports: {}, successful: {}, failed: {}, total bytes: {})",
                        exportCounter.get(), successfulExports.get(), failedExports.get(), totalBytesExported.get());
                if (recording.getState() == RecordingState.RUNNING) {
                    recording.stop();
                }
            } catch (Exception e) {
                logger.error("Error stopping JFR recording", e);
            } finally {
                recording.close();
            }
        }

        if (loggerProvider != null) {
            try {
                loggerProvider.close();
                logger.info("OTLP logger provider shut down successfully");
            } catch (Exception e) {
                logger.error("Error shutting down OTLP logger provider", e);
            }
        }
    }

    public long getExportCount() {
        return exportCounter.get();
    }

    public long getSuccessfulExports() {
        return successfulExports.get();
    }

    public long getFailedExports() {
        return failedExports.get();
    }

    public long getTotalBytesExported() {
        return totalBytesExported.get();
    }

    public String getRecordingState() {
        return recording != null ? recording.getState().name() : "NOT_STARTED";
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }
}
