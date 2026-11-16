package cz.upce.fei.nnptp.taskpulse.cli;

import cz.upce.fei.nnptp.taskpulse.domain.Priority;
import cz.upce.fei.nnptp.taskpulse.service.ProjectService;
import cz.upce.fei.nnptp.taskpulse.service.TaskService;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryProjectRepository;
import cz.upce.fei.nnptp.taskpulse.storage.InMemoryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ReportCliHandlerTest {
    @TempDir Path tempDir;
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private ReportCliHandler handler;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        var projectRepo = new InMemoryProjectRepository();
        var taskRepo = new InMemoryTaskRepository();
        var projects = new ProjectService(projectRepo, taskRepo);
        var tasks = new TaskService(taskRepo, projectRepo);
        projectId = projects.createProject("Course", "").getId();
        tasks.createTask(projectId, "Implementation", "", Priority.MEDIUM, 30, null);
        handler = new ReportCliHandler(projects, tasks,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8),
                Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void invalidCommandAndMissingParametersExplainUsage() {
        assertThatThrownBy(() -> handler.handle(List.of())).hasMessageContaining("Usage:");
        assertThatThrownBy(() -> handler.handle(List.of("unknown", projectId.toString())))
                .hasMessageContaining("Usage:");
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString())))
                .hasMessageContaining("Missing --format");
    }

    @Test
    void unknownAndDuplicateOptionsAreRejected() {
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(), "--unused", "value")))
                .hasMessageContaining("Unknown report option");
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(),
                "--format", "json", "--format", "markdown")))
                .hasMessageContaining("Duplicate option: --format");
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(),
                "--format", "json", "--output", "a", "--output", "b")))
                .hasMessageContaining("Duplicate option: --output");
    }

    @Test
    void missingOrBlankOptionValuesAreRejected() {
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(), "--format")))
                .hasMessageContaining("Missing value for --format");
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(), "--format", "  ")))
                .hasMessageContaining("Missing value for --format");
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(), "--format", "--output")))
                .hasMessageContaining("Missing value for --format");
    }

    @Test
    void unsupportedFormatIsRejected() {
        assertThatThrownBy(() -> handler.handle(List.of("export", projectId.toString(),
                "--format", "xml")))
                .hasMessageContaining("Unsupported report format");
    }

    @Test
    void markdownIsPrintedWhenOutputIsOmitted() {
        handler.handle(List.of("EXPORT", projectId.toString(), "--format", "MARKDOWN"));
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Course", "Implementation");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void jsonExportCreatesNewFile() throws Exception {
        Path destination = tempDir.resolve("summary.json");
        handler.handle(List.of("export", projectId.toString(),
                "--format", "json", "--output", destination.toString()));
        assertThat(Files.readString(destination)).contains("Course", "Implementation");
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Report exported to:");
    }

    @Test
    void existingOutputIsNotOverwritten() throws Exception {
        Path destination = tempDir.resolve("existing.json");
        Files.writeString(destination, "original");
        handler.handle(List.of("export", projectId.toString(),
                "--format", "json", "--output", destination.toString()));
        assertThat(Files.readString(destination)).isEqualTo("original");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Output already exists");
    }

    @Test
    void missingParentDirectoryReturnsWriteError() {
        Path destination = tempDir.resolve("missing").resolve("report.md");
        handler.handle(List.of("export", projectId.toString(),
                "--format", "markdown", "--output", destination.toString()));
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Cannot write report");
    }
}
