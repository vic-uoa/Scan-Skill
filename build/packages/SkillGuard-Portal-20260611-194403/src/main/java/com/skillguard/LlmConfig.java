package com.skillguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LlmConfig {
    public String endpoint = "";
    public String model = "";
    public String apiKey = "";
    public String temperature = "0.2";
    public String maxTokens = "900";
    public String organizationPolicy = "";
    public String requestBody = "";

    public static Path defaultPath() {
        Path preferred = Paths.get("skill-security-scanner", "config");
        if (Files.exists(preferred) || Files.exists(Paths.get("skill-security-scanner"))) {
            return preferred.resolve("llm-config.json");
        }
        return Paths.get("config", "llm-config.json");
    }

    public static LlmConfig load(Path path) throws IOException {
        LlmConfig config = new LlmConfig();
        if (path == null || !Files.exists(path)) {
            return config;
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        config.endpoint = readString(json, "endpoint");
        config.model = readString(json, "model");
        config.apiKey = readString(json, "api_key");
        config.temperature = defaultIfBlank(readString(json, "temperature"), "0.2");
        config.maxTokens = defaultIfBlank(readString(json, "max_tokens"), "900");
        config.organizationPolicy = readString(json, "organization_policy");
        config.requestBody = readString(json, "request_body");
        return config;
    }

    public void save(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        write(out, "endpoint", endpoint, true);
        write(out, "model", model, true);
        write(out, "api_key", apiKey, true);
        write(out, "temperature", temperature, true);
        write(out, "max_tokens", maxTokens, true);
        write(out, "organization_policy", organizationPolicy, true);
        write(out, "request_body", requestBody, false);
        out.append("}\n");
        Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8));
    }

    public boolean hasRequiredFields() {
        return !blank(endpoint) && !blank(model);
    }

    private static void write(StringBuilder out, String key, String value, boolean comma) {
        out.append("  \"").append(escape(key)).append("\": \"").append(escape(value)).append("\"");
        out.append(comma ? ",\n" : "\n");
    }

    private static String readString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (slash) {
                if (c == 'n') {
                    out.append('\n');
                } else if (c == 'r') {
                    out.append('\r');
                } else {
                    out.append(c);
                }
                slash = false;
            } else if (c == '\\') {
                slash = true;
            } else {
                out.append(c);
            }
        }
        if (slash) {
            out.append('\\');
        }
        return out.toString();
    }
}
