package com.skillguard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class LlmConfigDialog {
    private static final Color PAGE_BG = new Color(246, 248, 251);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER = new Color(221, 226, 235);
    private static final Color PRIMARY = new Color(31, 94, 184);
    private static final Color PRIMARY_DARK = new Color(21, 70, 145);
    private static final Color TEXT = new Color(31, 41, 55);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color OK = new Color(22, 101, 52);
    private static final Color ERROR = new Color(185, 28, 28);

    private LlmConfigDialog() {
    }

    public static LlmConfig configure(Path path) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException("当前环境无法打开模型配置窗口");
        }
        final LlmConfig initial = LlmConfig.load(path);
        final AtomicReference<LlmConfig> result = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(showDialog(path, initial));
                } catch (Exception e) {
                    error.set(e);
                }
            });
        } catch (Exception e) {
            throw new IOException("打开模型配置窗口失败: " + e.getMessage(), e);
        }
        if (error.get() != null) {
            throw new IOException(error.get().getMessage(), error.get());
        }
        return result.get();
    }

    private static LlmConfig showDialog(Path path, LlmConfig initial) {
        applyLookAndFeel();

        JDialog dialog = new JDialog((JFrame) null, "SkillGuard 模型配置", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(PAGE_BG);

        JTextField endpoint = textField(initial.endpoint, 44);
        JTextField model = textField(initial.model, 24);
        JPasswordField apiKey = passwordField(initial.apiKey, 32);
        JTextField temperature = textField(initial.temperature, 10);
        JTextField maxTokens = textField(initial.maxTokens, 10);
        JTextArea policy = textArea(initial.organizationPolicy, 4, 44);
        JTextArea requestBody = textArea(initial.requestBody, 7, 44);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE_BG);
        root.add(header(), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(PAGE_BG);
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        JPanel connection = card("连接信息", "用于调用 OpenAI-compatible Chat Completions 接口。配置会保存到本地，下次打开会自动带出。");
        addRow(connection, 0, "API 地址", endpoint,
                "示例：https://api.example.com/v1/chat/completions");
        addRow(connection, 1, "模型名称", model,
                "例如 deepseek-ai/DeepSeek-V4-Pro、gpt-4.1-mini 或你的网关模型名。");
        addRow(connection, 2, "API Key", apiKey,
                "如果内部网关不需要密钥，可以留空；否则建议使用最小权限密钥。");
        content.add(connection);

        JPanel params = card("生成参数", "只影响整改建议的表达方式，不参与风险判定、误报过滤和准入结论。");
        JPanel inline = new JPanel(new GridBagLayout());
        inline.setBackground(CARD_BG);
        addInlineField(inline, 0, "temperature", temperature, "建议 0.1 - 0.3，保持建议稳定。");
        addInlineField(inline, 1, "max tokens", maxTokens, "单条问题建议的最大输出长度。");
        addWideRow(params, 2, inline);
        content.add(params);

        JPanel policyCard = card("组织约束", "可写入本行或团队的整改口径，模型会优先遵守这些约束生成建议。");
        addWideRow(policyCard, 2, scroll(policy, 110));
        content.add(policyCard);

        JPanel bodyCard = card("请求体可选项 JSON", "可填写额外 JSON 字段并合并到默认请求体，例如 provider 特有参数；留空即可。");
        addWideRow(bodyCard, 2, scroll(requestBody, 150));
        JLabel bodyHint = hint("示例：{\"top_p\":0.8,\"response_format\":{\"type\":\"text\"}}。请填写合法 JSON 对象。");
        addWideRow(bodyCard, 3, bodyHint);
        content.add(bodyCard);

        JScrollPane scroller = new JScrollPane(content);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        root.add(scroller, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBackground(CARD_BG);
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JLabel status = new JLabel("连接成功后会保存配置并继续扫描；取消则不会生成 AI 报告。");
        status.setForeground(MUTED);
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));
        actions.add(status, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton clearBody = secondaryButton("清空请求体");
        JButton cancel = secondaryButton("取消");
        JButton save = primaryButton("测试连接并保存");
        buttons.add(clearBody);
        buttons.add(cancel);
        buttons.add(save);
        actions.add(buttons, BorderLayout.EAST);
        root.add(actions, BorderLayout.SOUTH);

        final LlmConfig[] selected = new LlmConfig[1];
        clearBody.addActionListener(event -> requestBody.setText(""));
        save.addActionListener(event -> {
            LlmConfig config = collect(endpoint, model, apiKey, temperature, maxTokens, policy, requestBody);
            if (config.endpoint.isEmpty() || config.model.isEmpty()) {
                status.setText("请先填写 API 地址和模型名称。");
                status.setForeground(ERROR);
                return;
            }
            setBusy(dialog, save, true);
            status.setText("正在测试模型连接...");
            status.setForeground(MUTED);
            try {
                LlmClient.testConnection(config);
                config.save(path);
                selected[0] = config;
                status.setText("连接成功，配置已保存。");
                status.setForeground(OK);
                JOptionPane.showMessageDialog(dialog, "模型连接成功，配置已保存。", "连接成功",
                        JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (Exception ex) {
                status.setText("连接失败，请检查 API 地址、模型名称、密钥或请求体。");
                status.setForeground(ERROR);
                JOptionPane.showMessageDialog(dialog,
                        "模型连接失败：\n" + ex.getMessage(),
                        "连接失败",
                        JOptionPane.ERROR_MESSAGE);
            } finally {
                setBusy(dialog, save, false);
            }
        });
        cancel.addActionListener(event -> dialog.dispose());

        dialog.setContentPane(root);
        dialog.setMinimumSize(new Dimension(780, 700));
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return selected[0];
    }

    private static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep Swing's default look and feel when the system one is unavailable.
        }
    }

    private static JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PRIMARY);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JLabel title = new JLabel("AI 个性化整改建议配置");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(title, BorderLayout.NORTH);

        JLabel desc = new JLabel("连接模型后，SkillGuard 会根据静态证据和内置建议生成更具体的整改步骤。");
        desc.setForeground(new Color(219, 234, 254));
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 13f));
        desc.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        panel.add(desc, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel card(String title, String description) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(PAGE_BG);
        outer.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        addWideRow(panel, 0, titleLabel);

        JLabel descLabel = new JLabel(description);
        descLabel.setForeground(MUTED);
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 12f));
        descLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 10, 0));
        addWideRow(panel, 1, descLabel);

        outer.add(panel, BorderLayout.CENTER);
        return panel;
    }

    private static void addRow(JPanel form, int row, String label, Component field, String help) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row + 2;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(7, 0, 7, 14);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(TEXT);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD, 12f));
        form.add(labelComponent, left);

        JPanel fieldPanel = new JPanel(new BorderLayout());
        fieldPanel.setOpaque(false);
        fieldPanel.add(field, BorderLayout.NORTH);
        JLabel hint = hint(help);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        fieldPanel.add(hint, BorderLayout.SOUTH);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row + 2;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(7, 0, 7, 0);
        form.add(fieldPanel, right);
    }

    private static void addWideRow(JPanel form, int row, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(field, c);
    }

    private static void addInlineField(JPanel form, int column, String label, Component field, String help) {
        JPanel item = new JPanel(new BorderLayout());
        item.setOpaque(false);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(TEXT);
        labelComponent.setFont(labelComponent.getFont().deriveFont(Font.BOLD, 12f));
        item.add(labelComponent, BorderLayout.NORTH);
        item.add(field, BorderLayout.CENTER);
        JLabel hint = hint(help);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        item.add(hint, BorderLayout.SOUTH);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, column == 0 ? 0 : 12, 0, column == 0 ? 12 : 0);
        form.add(item, c);
    }

    private static JTextField textField(String value, int columns) {
        JTextField field = new JTextField(value == null ? "" : value, columns);
        styleInput(field);
        return field;
    }

    private static JPasswordField passwordField(String value, int columns) {
        JPasswordField field = new JPasswordField(value == null ? "" : value, columns);
        styleInput(field);
        return field;
    }

    private static JTextArea textArea(String value, int rows, int columns) {
        JTextArea area = new JTextArea(value == null ? "" : value, rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        area.setForeground(TEXT);
        return area;
    }

    private static JScrollPane scroll(JTextArea area, int height) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(620, height));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        return scroll;
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }

    private static void styleInput(JComponent field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        field.setForeground(TEXT);
        field.setBackground(Color.WHITE);
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_DARK),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setForeground(TEXT);
        button.setBackground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return button;
    }

    private static LlmConfig collect(JTextField endpoint, JTextField model, JPasswordField apiKey,
            JTextField temperature, JTextField maxTokens, JTextArea policy, JTextArea requestBody) {
        LlmConfig config = new LlmConfig();
        config.endpoint = endpoint.getText().trim();
        config.model = model.getText().trim();
        config.apiKey = new String(apiKey.getPassword()).trim();
        config.temperature = temperature.getText().trim();
        config.maxTokens = maxTokens.getText().trim();
        config.organizationPolicy = policy.getText();
        config.requestBody = requestBody.getText().trim();
        return config;
    }

    private static void setBusy(JDialog dialog, JButton save, boolean busy) {
        save.setEnabled(!busy);
        save.setText(busy ? "正在连接..." : "测试连接并保存");
        dialog.setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }
}
