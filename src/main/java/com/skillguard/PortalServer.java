package com.skillguard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class PortalServer {
    private static final int DEFAULT_PORT = 8765;
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int MAX_TREE_ITEMS = 160;
    private static final int MAX_TREE_DEPTH = 5;
    private static final Map<String, ScanJob> JOBS = new ConcurrentHashMap<>();

    private PortalServer() {
    }

    public static int run(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--host".equals(arg) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--port".equals(arg) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("--port must be a number");
                    return 2;
                }
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return 0;
            } else {
                System.err.println("Unknown portal option: " + arg);
                printHelp();
                return 2;
            }
        }

        try {
            Path portalRoot = locatePortalRoot();
            Path reportRoot = Paths.get("build", "portal-reports").toAbsolutePath().normalize();
            Files.createDirectories(reportRoot);

            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/preview", new PreviewHandler());
            server.createContext("/api/upload/start", new UploadStartHandler());
            server.createContext("/api/upload/file", new UploadFileHandler());
            server.createContext("/api/upload/finish", new UploadFinishHandler());
            server.createContext("/api/scan", new ScanHandler(reportRoot));
            server.createContext("/api/config/test", new ConfigTestHandler());
            server.createContext("/api/config", new ConfigHandler());
            server.createContext("/reports", new ReportHandler(reportRoot));
            server.createContext("/", new StaticHandler(portalRoot));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            String url = "http://" + host + ":" + port + "/";
            System.out.println("SkillGuard Portal started: " + url);
            System.out.println("Serving portal from: " + portalRoot);
            System.out.println("Serving reports from: " + reportRoot);
            System.out.println("Press Ctrl+C to stop.");
            return waitForever();
        } catch (IOException e) {
            System.err.println("Failed to start portal: " + e.getMessage());
            return 2;
        }
    }

    private static int waitForever() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 130;
            }
        }
        return 0;
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar dist/skillguard.jar portal [--host 127.0.0.1] [--port 8765]");
    }

    private static Path locatePortalRoot() throws IOException {
        Path[] candidates = new Path[] {
                Paths.get("skill-security-scanner", "portal"),
                Paths.get("portal"),
                Paths.get("..", "portal")
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized) && Files.exists(normalized.resolve("index.html"))) {
                return normalized;
            }
        }
        throw new IOException("portal/index.html not found");
    }

    private static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            sendJson(exchange, 200, "{\"ok\":true,\"service\":\"SkillGuard Portal\"}");
        }
    }

    private static final class PreviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only POST is supported"));
                return;
            }
            String body = readBody(exchange);
            String pathValue = readJsonString(body, "path");
            if (blank(pathValue)) {
                sendJson(exchange, 400, errorJson("Please provide a local folder path"));
                return;
            }
            Path root = Paths.get(pathValue).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                sendJson(exchange, 404, errorJson("Path does not exist: " + root));
                return;
            }
            if (!Files.isDirectory(root)) {
                sendJson(exchange, 400, errorJson("Path must be a folder: " + root));
                return;
            }
            TreeResult tree = buildTree(root);
            StringBuilder json = new StringBuilder();
            json.append("{");
            field(json, "ok", true, true);
            field(json, "path", root.toString(), true);
            field(json, "name", root.getFileName() == null ? root.toString() : root.getFileName().toString(), true);
            field(json, "hasSkillMd", tree.hasSkillMd, true);
            json.append("\"items\":[");
            appendTreeItems(json, tree.items);
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static final class UploadStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only POST is supported"));
                return;
            }
            String body = readBody(exchange);
            String name = defaultIfBlank(readJsonString(body, "name"), "uploaded-skill");
            String sessionId = timestamp() + "-" + UUID.randomUUID().toString().substring(0, 8);
            Path root = uploadRoot().resolve(sessionId).resolve(cleanSegment(name)).normalize();
            Files.createDirectories(root);
            StringBuilder json = new StringBuilder();
            json.append("{");
            field(json, "ok", true, true);
            field(json, "sessionId", sessionId, true);
            field(json, "path", root.toString(), false);
            json.append("}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static final class UploadFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod()) && !"PUT".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only POST and PUT are supported"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String session = query.get("session");
            String relative = query.get("path");
            if (blank(session) || blank(relative)) {
                sendJson(exchange, 400, errorJson("Missing upload session or file path"));
                return;
            }
            Path sessionRoot = findUploadSessionRoot(session);
            if (sessionRoot == null) {
                sendJson(exchange, 404, errorJson("Upload session not found"));
                return;
            }
            Path target = safeResolve(sessionRoot, relative);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = exchange.getRequestBody(); OutputStream out = Files.newOutputStream(target)) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) >= 0) {
                    out.write(chunk, 0, read);
                }
            }
            sendJson(exchange, 200, "{\"ok\":true}");
        }
    }

    private static final class UploadFinishHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only POST is supported"));
                return;
            }
            String body = readBody(exchange);
            String session = readJsonString(body, "sessionId");
            Path root = findUploadSessionRoot(session);
            if (root == null) {
                sendJson(exchange, 404, errorJson("Upload session not found"));
                return;
            }
            TreeResult tree = buildTree(root);
            StringBuilder json = new StringBuilder();
            json.append("{");
            field(json, "ok", true, true);
            field(json, "path", root.toString(), true);
            field(json, "name", root.getFileName() == null ? root.toString() : root.getFileName().toString(), true);
            field(json, "hasSkillMd", tree.hasSkillMd, true);
            json.append("\"items\":[");
            appendTreeItems(json, tree.items);
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static final class ScanHandler implements HttpHandler {
        private final Path reportRoot;

        private ScanHandler(Path reportRoot) {
            this.reportRoot = reportRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(method) && "/api/scan".equals(path)) {
                startScan(exchange);
                return;
            }
            if ("GET".equals(method) && path.startsWith("/api/scan/")) {
                String jobId = path.substring("/api/scan/".length());
                ScanJob job = JOBS.get(jobId);
                if (job == null) {
                    sendJson(exchange, 404, errorJson("Scan job not found"));
                    return;
                }
                sendJson(exchange, 200, job.toJson());
                return;
            }
            sendJson(exchange, 404, errorJson("Unknown scan endpoint"));
        }

        private void startScan(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            String pathValue = readJsonString(body, "path");
            String format = normalizeFormat(readJsonString(body, "format"));
            boolean ai = readJsonBoolean(body, "ai");
            boolean review = readJsonBoolean(body, "review");
            if (blank(pathValue)) {
                sendJson(exchange, 400, errorJson("Please provide a local folder path"));
                return;
            }
            Path target = Paths.get(pathValue).toAbsolutePath().normalize();
            if (!Files.isDirectory(target)) {
                sendJson(exchange, 400, errorJson("Path must be an existing folder: " + target));
                return;
            }
            String jobId = timestamp() + "-" + UUID.randomUUID().toString().substring(0, 8);
            ScanJob job = new ScanJob(jobId, target, format, ai, review);
            JOBS.put(jobId, job);
            Thread worker = new Thread(new ScanRunner(job, reportRoot), "skillguard-portal-scan-" + jobId);
            worker.setDaemon(true);
            worker.start();
            sendJson(exchange, 202, job.toJson());
        }
    }

    private static final class ScanRunner implements Runnable {
        private final ScanJob job;
        private final Path reportRoot;

        private ScanRunner(ScanJob job, Path reportRoot) {
            this.job = job;
            this.reportRoot = reportRoot;
        }

        @Override
        public void run() {
            try {
                job.update("running", 12, "checking SKILL.md and file tree");
                TreeResult tree = buildTree(job.target);
                if (!tree.hasSkillMd) {
                    throw new IOException("SKILL.md was not found under: " + job.target);
                }

                job.update("running", 32, "matching static security rules");
                SkillScanner scanner = new SkillScanner(BuiltinRules.all());
                ScanSummary summary = scanner.scan(job.target);

                if (job.ai) {
                    job.update("running", 68, "generating AI remediation suggestions");
                    LlmConfig config = LlmConfig.load(LlmConfig.defaultPath());
                    if (!config.hasRequiredFields()) {
                        throw new IOException("AI scan requires endpoint and model in " + LlmConfig.defaultPath());
                    }
                    LlmRemediationService.apply(summary, config);
                }

                job.update("running", 86, "writing HTML, PDF and JSON reports");
                Files.createDirectories(reportRoot);
                Path html = reportRoot.resolve(job.id + ".html");
                Path json = reportRoot.resolve(job.id + ".json");
                Path pdf = reportRoot.resolve(job.id + ".pdf");
                Files.write(html, ReportWriter.html(summary, job.review, job.ai).getBytes(StandardCharsets.UTF_8));
                Files.write(json, ReportWriter.json(summary).getBytes(StandardCharsets.UTF_8));
                Files.write(pdf, ReportWriter.pdf(summary));

                job.summary = summary;
                job.htmlUrl = "/reports/" + html.getFileName();
                job.jsonUrl = "/reports/" + json.getFileName();
                job.pdfUrl = "/reports/" + pdf.getFileName();
                job.primaryUrl = "pdf".equals(job.format) ? job.pdfUrl : "json".equals(job.format) ? job.jsonUrl : job.htmlUrl;
                job.update("complete", 100, "report generated");
            } catch (Exception e) {
                job.error = e.getMessage() == null ? e.toString() : e.getMessage();
                job.update("failed", 100, "scan failed");
            }
        }
    }

    private static final class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                LlmConfig config = LlmConfig.load(LlmConfig.defaultPath());
                StringBuilder json = new StringBuilder();
                json.append("{");
                field(json, "ok", true, true);
                field(json, "path", LlmConfig.defaultPath().toString(), true);
                field(json, "endpoint", config.endpoint, true);
                field(json, "model", config.model, true);
                field(json, "apiKeyConfigured", !blank(config.apiKey), true);
                field(json, "temperature", config.temperature, true);
                field(json, "maxTokens", config.maxTokens, true);
                field(json, "organizationPolicy", config.organizationPolicy, true);
                field(json, "requestBody", config.requestBody, false);
                json.append("}");
                sendJson(exchange, 200, json.toString());
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                LlmConfig existing = LlmConfig.load(LlmConfig.defaultPath());
                LlmConfig config = new LlmConfig();
                config.endpoint = readJsonString(body, "endpoint");
                config.model = readJsonString(body, "model");
                String apiKey = readJsonString(body, "apiKey");
                config.apiKey = blank(apiKey) ? existing.apiKey : apiKey;
                config.temperature = defaultIfBlank(readJsonString(body, "temperature"), "0.2");
                config.maxTokens = defaultIfBlank(readJsonString(body, "maxTokens"), "900");
                config.organizationPolicy = readJsonString(body, "organizationPolicy");
                config.requestBody = readJsonString(body, "requestBody");
                config.save(LlmConfig.defaultPath());
                sendJson(exchange, 200, "{\"ok\":true}");
                return;
            }
            sendJson(exchange, 405, errorJson("Only GET and POST are supported"));
        }
    }

    private static final class ConfigTestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only POST is supported"));
                return;
            }
            try {
                LlmConfig config = LlmConfig.load(LlmConfig.defaultPath());
                LlmClient.testConnection(config);
                sendJson(exchange, 200, "{\"ok\":true,\"message\":\"connection ok\"}");
            } catch (IOException e) {
                sendJson(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private static final class ReportHandler implements HttpHandler {
        private final Path reportRoot;

        private ReportHandler(Path reportRoot) {
            this.reportRoot = reportRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            String name = exchange.getRequestURI().getPath().substring("/reports/".length());
            if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                sendJson(exchange, 400, errorJson("Invalid report name"));
                return;
            }
            Path file = reportRoot.resolve(name).normalize();
            if (!file.startsWith(reportRoot) || !Files.exists(file) || Files.isDirectory(file)) {
                sendJson(exchange, 404, errorJson("Report not found"));
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if ("1".equals(query.get("download")) || "true".equalsIgnoreCase(query.get("download"))) {
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"" + escapeHeader(file.getFileName().toString()) + "\"");
            }
            sendFile(exchange, file);
        }
    }

    private static final class StaticHandler implements HttpHandler {
        private final Path root;

        private StaticHandler(Path root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (preflight(exchange)) {
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson("Only GET is supported"));
                return;
            }
            String rawPath = exchange.getRequestURI().getPath();
            String decoded = URLDecoder.decode(rawPath, "UTF-8");
            if (decoded.equals("/") || decoded.isEmpty()) {
                decoded = "/index.html";
            }
            if ("/favicon.ico".equals(decoded)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            Path file = root.resolve(decoded.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) {
                sendJson(exchange, 404, errorJson("Not found"));
                return;
            }
            sendFile(exchange, file);
        }
    }

    private static final class ScanJob {
        final String id;
        final Path target;
        final String format;
        final boolean ai;
        final boolean review;
        volatile String status = "queued";
        volatile int progress = 0;
        volatile String message = "queued";
        volatile String error = "";
        volatile ScanSummary summary;
        volatile String htmlUrl = "";
        volatile String pdfUrl = "";
        volatile String jsonUrl = "";
        volatile String primaryUrl = "";

        ScanJob(String id, Path target, String format, boolean ai, boolean review) {
            this.id = id;
            this.target = target;
            this.format = format;
            this.ai = ai;
            this.review = review;
        }

        void update(String status, int progress, String message) {
            this.status = status;
            this.progress = progress;
            this.message = message;
        }

        String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            field(json, "ok", true, true);
            field(json, "id", id, true);
            field(json, "status", status, true);
            field(json, "progress", progress, true);
            field(json, "message", message, true);
            field(json, "ai", ai, true);
            field(json, "format", format, true);
            field(json, "htmlUrl", htmlUrl, true);
            field(json, "pdfUrl", pdfUrl, true);
            field(json, "jsonUrl", jsonUrl, true);
            field(json, "primaryUrl", primaryUrl, true);
            field(json, "error", error, true);
            json.append("\"summary\":");
            appendSummary(json, summary);
            json.append("}");
            return json.toString();
        }
    }

    private static final class TreeResult {
        final List<TreeItem> items = new ArrayList<>();
        boolean hasSkillMd;
    }

    private static final class TreeItem {
        final String path;
        final String name;
        final String type;
        final int depth;

        TreeItem(String path, String name, String type, int depth) {
            this.path = path;
            this.name = name;
            this.type = type;
            this.depth = depth;
        }
    }

    private static TreeResult buildTree(final Path root) throws IOException {
        final TreeResult result = new TreeResult();
        result.items.add(new TreeItem(".", root.getFileName() == null ? root.toString() : root.getFileName().toString(), "dir", 0));
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                int depth = root.relativize(dir).getNameCount();
                if (shouldSkipDir(dir.getFileName().toString())) {
                    addTreeItem(root, dir, "skipped_dir", depth);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (depth > MAX_TREE_DEPTH || result.items.size() >= MAX_TREE_ITEMS) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                addTreeItem(root, dir, "dir", depth);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (result.items.size() >= MAX_TREE_ITEMS) {
                    return FileVisitResult.TERMINATE;
                }
                int depth = root.relativize(file).getNameCount();
                if (depth <= MAX_TREE_DEPTH) {
                    addTreeItem(root, file, "file", depth);
                }
                if ("SKILL.md".equalsIgnoreCase(file.getFileName().toString())) {
                    result.hasSkillMd = true;
                }
                return FileVisitResult.CONTINUE;
            }

            private void addTreeItem(Path base, Path item, String type, int depth) {
                String relative = base.relativize(item).toString().replace('\\', '/');
                result.items.add(new TreeItem(relative, item.getFileName().toString(), type, depth));
            }
        });
        return result;
    }

    private static void appendTreeItems(StringBuilder json, List<TreeItem> items) {
        for (int i = 0; i < items.size(); i++) {
            TreeItem item = items.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            field(json, "path", item.path, true);
            field(json, "name", item.name, true);
            field(json, "type", item.type, true);
            field(json, "depth", item.depth, false);
            json.append("}");
        }
    }

    private static Path uploadRoot() throws IOException {
        Path root = Paths.get("build", "portal-uploads").toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static Path findUploadSessionRoot(String session) throws IOException {
        if (blank(session) || session.contains("/") || session.contains("\\") || session.contains("..")) {
            return null;
        }
        Path sessionDir = uploadRoot().resolve(session).normalize();
        if (!sessionDir.startsWith(uploadRoot()) || !Files.isDirectory(sessionDir)) {
            return null;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(sessionDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    return child.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        String normalized = relative.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe upload path: " + relative);
        }
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Unsafe upload path: " + relative);
        }
        return target;
    }

    private static String cleanSegment(String value) {
        String cleaned = value == null ? "uploaded-skill" : value.replace('\\', '/');
        int slash = cleaned.indexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(0, slash);
        }
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        return blank(cleaned) ? "uploaded-skill" : cleaned;
    }

    private static boolean shouldSkipDir(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return ".git".equals(normalized)
                || "node_modules".equals(normalized)
                || "build".equals(normalized)
                || "dist".equals(normalized)
                || ".venv".equals(normalized)
                || "__pycache__".equals(normalized);
    }

    private static void appendSummary(StringBuilder json, ScanSummary summary) {
        if (summary == null) {
            json.append("null");
            return;
        }
        json.append("{");
        field(json, "risk", summary.riskLevel().name(), true);
        field(json, "admission", summary.admissionDecision(), true);
        field(json, "totalFindings", summary.totalFindings(), true);
        field(json, "rawFindings", summary.totalRawFindings(), true);
        field(json, "filteredFindings", summary.totalFilteredFindings(), true);
        field(json, "blockingFindings", summary.blockingFindingsCount(), true);
        field(json, "manualReviewFindings", summary.manualReviewFindingsCount(), true);
        field(json, "filesScanned", summary.totalFiles(), true);
        field(json, "skills", summary.reports.size(), true);
        json.append("\"findings\":[");
        int emitted = 0;
        for (SkillReport report : summary.reports) {
            for (Finding finding : report.findings) {
                if (emitted >= 8) {
                    break;
                }
                if (emitted > 0) {
                    json.append(",");
                }
                json.append("{");
                field(json, "severity", finding.severity.name(), true);
                field(json, "rule", finding.ruleId, true);
                field(json, "message", finding.message, true);
                field(json, "file", finding.file == null ? "" : finding.file.toString(), true);
                field(json, "line", finding.line, true);
                field(json, "decision", finding.decision, false);
                json.append("}");
                emitted++;
            }
            if (emitted >= 8) {
                break;
            }
        }
        json.append("]}");
    }

    private static void sendFile(HttpExchange exchange, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        send(exchange, 200, contentType(file), bytes);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        addCors(headers);
        exchange.sendResponseHeaders(status, "HEAD".equals(exchange.getRequestMethod()) ? -1 : bytes.length);
        if (!"HEAD".equals(exchange.getRequestMethod())) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private static boolean preflight(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = exchange.getRequestBody()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String readJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (slash) {
                if (c == 'n') {
                    out.append('\n');
                } else if (c == 'r') {
                    out.append('\r');
                } else if (c == 't') {
                    out.append('\t');
                } else {
                    out.append(c);
                }
                slash = false;
            } else if (c == '\\') {
                slash = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean readJsonBoolean(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return false;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) {
            return false;
        }
        String value = json.substring(colon + 1).trim();
        return value.startsWith("true");
    }

    private static List<String> readJsonStringArray(String json, String key) {
        List<String> values = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) {
            return values;
        }
        int colon = json.indexOf(':', keyIndex + needle.length());
        int open = colon < 0 ? -1 : json.indexOf('[', colon + 1);
        if (open < 0) {
            return values;
        }
        boolean inString = false;
        boolean slash = false;
        StringBuilder current = new StringBuilder();
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (slash) {
                    if (c == 'n') {
                        current.append('\n');
                    } else if (c == 'r') {
                        current.append('\r');
                    } else if (c == 't') {
                        current.append('\t');
                    } else {
                        current.append(c);
                    }
                    slash = false;
                } else if (c == '\\') {
                    slash = true;
                } else if (c == '"') {
                    values.add(current.toString());
                    current.setLength(0);
                    inString = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == ']') {
                return values;
            }
        }
        return values;
    }

    private static Map<String, String> parseQuery(String rawQuery) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return values;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            values.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return values;
    }

    private static void field(StringBuilder json, String name, String value, boolean comma) {
        json.append("\"").append(escape(name)).append("\":\"").append(escape(value)).append("\"");
        if (comma) {
            json.append(",");
        }
    }

    private static void field(StringBuilder json, String name, int value, boolean comma) {
        json.append("\"").append(escape(name)).append("\":").append(value);
        if (comma) {
            json.append(",");
        }
    }

    private static void field(StringBuilder json, String name, boolean value, boolean comma) {
        json.append("\"").append(escape(name)).append("\":").append(value);
        if (comma) {
            json.append(",");
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String errorJson(String message) {
        return "{\"ok\":false,\"error\":\"" + escape(message) + "\"}";
    }

    private static String escapeHeader(String value) {
        if (value == null) {
            return "download";
        }
        return value.replace("\\", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_");
    }

    private static String normalizeFormat(String format) {
        if ("pdf".equalsIgnoreCase(format)) {
            return "pdf";
        }
        if ("json".equalsIgnoreCase(format)) {
            return "json";
        }
        return "html";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }
}
