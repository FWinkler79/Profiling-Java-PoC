package com.example.jfrotelprofiles.service;

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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that continuously records JFR events and periodically exports them to an OTLP backend.
 * Uses OpenTelemetry Logs API to stream JFR event data as structured logs.
 *
 * This is a practical implementation that works with current OpenTelemetry releases.
 * For production use with Profile Signal support, use the opentelemetry-sdk-extension-jfr-profiles
 * once it's released to Maven Central.
 */
@Service
public class JfrOtelExportService {

    private static final Logger logger = LoggerFactory.getLogger(JfrOtelExportService.class);

    @Value("${jfr.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${jfr.export.interval.seconds:15}")
    private int exportIntervalSeconds;

    @Value("${jfr.instrumentation.scope.name:jfr-otel-app}")
    private String scopeName;

    @Value("${jfr.instrumentation.scope.version:1.0.0}")
    private String scopeVersion;

    private Recording recording;
    private SdkLoggerProvider loggerProvider;
    private LogRecordExporter exporter;
    private final AtomicLong exportCounter = new AtomicLong(0);
    private final AtomicLong successfulExports = new AtomicLong(0);
    private final AtomicLong failedExports = new AtomicLong(0);
    private final AtomicLong totalEventsExported = new AtomicLong(0);

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

            // Read and export JFR events as OTLP logs
            long eventCount = exportJfrEventsAsLogs(tempFile);
            totalEventsExported.addAndGet(eventCount);

            // Force flush to ensure events are sent
            loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            successfulExports.incrementAndGet();
            logger.info("Successfully exported {} JFR events (snapshot #{}) to OTLP ({} ms)",
                    eventCount, exportNum, durationMs);

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
     * Reads JFR events from a file and exports them as OpenTelemetry log records.
     * Each JFR event becomes a structured log entry with all event fields as attributes.
     */
    private long exportJfrEventsAsLogs(Path jfrFilePath) throws IOException {
        long count = 0;
        Map<String, Long> eventTypeCounts = new HashMap<>();

        try (RecordingFile recordingFile = new RecordingFile(jfrFilePath)) {
            var logger = loggerProvider.get(scopeName);

            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (event == null) {
                    continue;
                }

                // Build attributes from JFR event fields
                var attributesBuilder = Attributes.builder();
                attributesBuilder.put("jfr.event.type", event.getEventType().getName());
                attributesBuilder.put("jfr.event.duration", event.getDuration().toNanos());

                // Add thread information if available
                if (event.getThread() != null) {
                    attributesBuilder.put("thread.name", event.getThread().getJavaName());
                    attributesBuilder.put("thread.id", event.getThread().getJavaThreadId());
                }

                // Add stack trace if available
                if (event.getStackTrace() != null) {
                    var frames = event.getStackTrace().getFrames();
                    if (!frames.isEmpty()) {
                        var topFrame = frames.get(0);
                        if (topFrame.getMethod() != null) {
                            attributesBuilder.put("code.function", topFrame.getMethod().getName());
                            if (topFrame.getMethod().getType() != null) {
                                attributesBuilder.put("code.namespace",
                                        topFrame.getMethod().getType().getName());
                            }
                        }
                    }
                    attributesBuilder.put("stack.depth", frames.size());
                }

                // Extract key fields based on event type
                String eventType = event.getEventType().getName();
                switch (eventType) {
                    case "jdk.CPULoad":
                        if (event.hasField("jvmUser")) {
                            attributesBuilder.put("cpu.jvm.user", event.getFloat("jvmUser"));
                        }
                        if (event.hasField("jvmSystem")) {
                            attributesBuilder.put("cpu.jvm.system", event.getFloat("jvmSystem"));
                        }
                        break;
                    case "jdk.GarbageCollection":
                    case "jdk.YoungGarbageCollection":
                    case "jdk.OldGarbageCollection":
                        if (event.hasField("name")) {
                            attributesBuilder.put("gc.name", event.getString("name"));
                        }
                        break;
                    case "jdk.JavaExceptionThrow":
                    case "jdk.JavaErrorThrow":
                        if (event.hasField("thrownClass")) {
                            var thrownClass = event.getClass("thrownClass");
                            if (thrownClass != null) {
                                attributesBuilder.put("exception.type", thrownClass.getName());
                            }
                        }
                        if (event.hasField("message")) {
                            attributesBuilder.put("exception.message", event.getString("message"));
                        }
                        break;
                }

                // Emit as log record
                logger.logRecordBuilder()
                        .setTimestamp(event.getStartTime())
                        .setSeverity(Severity.INFO)
                        .setBody(eventType)
                        .setAllAttributes(attributesBuilder.build())
                        .emit();

                count++;
                eventTypeCounts.merge(eventType, 1L, Long::sum);
            }
        }

        // Log summary
        logger.debug("Exported {} events across {} types", count, eventTypeCounts.size());
        eventTypeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> logger.debug("  {}: {} events", e.getKey(), e.getValue()));

        return count;
    }

    @PreDestroy
    public void stopRecording() {
        if (recording != null) {
            try {
                logger.info("Stopping JFR recording (total exports: {}, successful: {}, failed: {}, total events: {})",
                        exportCounter.get(), successfulExports.get(), failedExports.get(), totalEventsExported.get());
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

    public long getTotalEventsExported() {
        return totalEventsExported.get();
    }

    public String getRecordingState() {
        return recording != null ? recording.getState().name() : "NOT_STARTED";
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }
}
