package com.skillguard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LlmClient {
    private LlmClient() {
    }

    public static void testConnection(LlmConfig config) throws IOException {
        if (config == null || !config.hasRequiredFields()) {
            throw new IOException("模型地址和模型名称不能为空");
        }
        String response = post(config, "SkillGuard connection test. Reply OK.", 16);
        if (response.trim().isEmpty()) {
            throw new IOException("模型返回为空");
        }
    }

    public static String suggest(LlmConfig config, Finding finding) throws IOException {
        String prompt = "你是 SkillGuard 静态扫描报告的整改建议助手。"
                + "只根据已给出的证据、规则和原建议生成针对性整改步骤，不改变漏洞判定。"
                + "请用中文输出 3 段：优先处理、具体修改步骤、修改例子。\n\n"
                + "规则: " + safe(finding.ruleId) + " / " + safe(finding.message) + "\n"
                + "风险等级: " + finding.severity + "\n"
                + "Skill: " + safe(finding.skillName) + "\n"
                + "位置: " + safe(finding.file == null ? "" : finding.file.toString()) + ":" + finding.line + "\n"
                + "证据: " + safe(finding.evidence) + "\n"
                + "上下文: " + safe(finding.contextExcerpt) + "\n"
                + "现有建议: " + safe(finding.recommendation) + "\n"
                + "组织约束: " + safe(config.organizationPolicy) + "\n";
        String response = post(config, prompt, parseInt(config.maxTokens, 900));
        String content = extractContent(response);
        return content.trim().isEmpty() ? response.trim() : content.trim();
    }

    private static String post(LlmConfig config, String prompt, int maxTokens) throws IOException {
        String body = buildBody(config, prompt, maxTokens);
        HttpURLConnection connection = (HttpURLConnection) new URL(config.endpoint.trim()).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(90000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (config.apiKey != null && !config.apiKey.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + config.apiKey.trim());
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }
        int status = connection.getResponseCode();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (java.io.InputStream in = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream()) {
            if (in != null) {
                byte[] chunk = new byte[4096];
                int read;
                while ((read = in.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, read);
                }
            }
        }
        String response = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            throw new IOException("模型连接失败 HTTP " + status + ": " + response);
        }
        return response;
    }

    private static String buildBody(LlmConfig config, String prompt, int maxTokens) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("{");
        body.append("\"model\":\"").append(json(config.model)).append("\",");
        body.append("\"messages\":[{\"role\":\"user\",\"content\":\"").append(json(prompt)).append("\"}],");
        body.append("\"temperature\":").append(decimal(config.temperature, "0.2")).append(",");
        body.append("\"max_tokens\":").append(Math.max(1, maxTokens));
        String extra = config.requestBody == null ? "" : config.requestBody.trim();
        if (!extra.isEmpty()) {
            if (!extra.startsWith("{") || !extra.endsWith("}")) {
                throw new IOException("请求体可选项必须是 JSON 对象");
            }
            String inner = extra.substring(1, extra.length() - 1).trim();
            if (!inner.isEmpty()) {
                body.append(",").append(inner);
            }
        }
        body.append("}");
        return body.toString();
    }

    private static String extractContent(String response) {
        int key = response.indexOf("\"content\"");
        if (key < 0) {
            return "";
        }
        int colon = response.indexOf(':', key);
        if (colon < 0) {
            return "";
        }
        int quote = response.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = quote + 1; i < response.length(); i++) {
            char c = response.charAt(i);
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

    private static String decimal(String value, String fallback) {
        try {
            Double.parseDouble(value);
            return value;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String json(String value) {
        return safe(value).replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
