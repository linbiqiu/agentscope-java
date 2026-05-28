package com.company.feishuagent.feishu.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LarkMarkdownOptimizer {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(`{3,})[^\\n]*\\n[\\s\\S]*?\\n\\1");
    private static final Pattern HR_PATTERN = Pattern.compile("^[\\s]*---+[\\s]*$", Pattern.MULTILINE);
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?[\\s]*:?-+:?[\\s]*(\\|[\\s]*:?-+:?[\\s]*)*\\|?$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|$", Pattern.MULTILINE);
    private static final Pattern FULL_TABLE = Pattern.compile("(^\\|.+)\\|(\\n\\|[-:\\s|]+\\|)?(\\n(\\|.+)\\|)+", Pattern.MULTILINE);
    private static final Pattern THREE_PLUS_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern INVALID_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD_HEADER_LIKE = Pattern.compile("^\\*{2}[^*]+\\*{2}\\s*$", Pattern.MULTILINE);

    private LarkMarkdownOptimizer() {}

    static String optimize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            List<String> codeBlocks = new ArrayList<>();
            String r = extractCodeBlocks(text, codeBlocks);

            r = convertTablesToList(r);
            r = convertHeaders(r);
            r = removeHorizontalRules(r);
            r = restoreCodeBlocks(r, codeBlocks);
            r = THREE_PLUS_NEWLINES.matcher(r).replaceAll("\n\n");
            r = stripInvalidImageKeys(r);
            r = r.trim();

            return r;
        } catch (Exception e) {
            return text;
        }
    }

    private static String extractCodeBlocks(String text, List<String> codeBlocks) {
        StringBuffer sb = new StringBuffer();
        Matcher m = CODE_BLOCK_PATTERN.matcher(text);
        while (m.find()) {
            String block = m.group();
            String placeholder = "___CB_" + codeBlocks.size() + "___";
            codeBlocks.add(block);
            m.appendReplacement(sb, placeholder);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String restoreCodeBlocks(String text, List<String> codeBlocks) {
        for (int i = 0; i < codeBlocks.size(); i++) {
            text = text.replace("___CB_" + i + "___", codeBlocks.get(i));
        }
        return text;
    }

    private static String convertHeaders(String text) {
        Matcher m = HEADER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String title = m.group(1).trim();
            m.appendReplacement(sb, "**" + title + "**");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String removeHorizontalRules(String text) {
        return HR_PATTERN.matcher(text).replaceAll("");
    }

    private static String convertTablesToList(String text) {
        Matcher m = FULL_TABLE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String tableBlock = m.group();
            String converted = tableToList(tableBlock);
            m.appendReplacement(sb, Matcher.quoteReplacement(converted));
        }
        m.appendTail(sb);

        if (m.hitEnd() || !FULL_TABLE.matcher(text).find()) {
            return handleRemainingTableRows(sb.toString());
        }
        return sb.toString();
    }

    private static String handleRemainingTableRows(String text) {
        Matcher m = TABLE_ROW.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String row = m.group(1);
            if (TABLE_SEPARATOR.matcher("|" + row + "|").matches()) {
                m.appendReplacement(sb, "");
            } else {
                String[] cells = row.split("\\|");
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < cells.length; i++) {
                    String cell = cells[i].trim();
                    if (!cell.isEmpty()) {
                        if (line.length() > 0) line.append("  ");
                        line.append(cell);
                    }
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(line.toString()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tableToList(String tableBlock) {
        String[] lines = tableBlock.split("\n");
        List<String[]> dataRows = new ArrayList<>();
        int colCount = 0;

        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("|")) continue;
            if (TABLE_SEPARATOR.matcher(line).matches()) continue;

            String inner = line.substring(1, line.length() - 1);
            String[] cells = inner.split("\\|");
            List<String> trimmed = new ArrayList<>();
            for (String c : cells) {
                trimmed.add(c.trim());
            }
            colCount = Math.max(colCount, trimmed.size());
            dataRows.add(trimmed.toArray(new String[0]));
        }

        if (dataRows.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        String[] headers = dataRows.get(0);

        if (dataRows.size() == 1) {
            for (String h : headers) {
                if (!h.isEmpty()) {
                    result.append("- **").append(h).append("**\n");
                }
            }
        } else {
            for (int i = 1; i < dataRows.size(); i++) {
                String[] row = dataRows.get(i);
                StringBuilder rowStr = new StringBuilder("- ");
                for (int j = 0; j < Math.min(headers.length, row.length); j++) {
                    String header = headers[j].trim();
                    String value = j < row.length ? row[j].trim() : "";
                    if (!header.isEmpty() && !value.isEmpty()) {
                        if (rowStr.length() > 2) rowStr.append(" | ");
                        rowStr.append("**").append(header).append("**: ").append(value);
                    } else if (!value.isEmpty()) {
                        if (rowStr.length() > 2) rowStr.append(" | ");
                        rowStr.append(value);
                    }
                }
                result.append(rowStr).append("\n");
            }
        }

        return result.toString().trim();
    }

    private static String stripInvalidImageKeys(String text) {
        if (!text.contains("![")) {
            return text;
        }
        Matcher m = INVALID_IMAGE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String value = m.group(2);
            if (value.startsWith("img_")) {
                m.appendReplacement(sb, m.group(0));
            } else {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
