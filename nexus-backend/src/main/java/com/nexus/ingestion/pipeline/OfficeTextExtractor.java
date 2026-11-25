package com.nexus.ingestion.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Office 文档轻量文本提取器（docx / xlsx）。
 * 为什么不用 Apache POI：比赛版只需纯文本供下游 LLM 抽取，POI 引入约 15MB 依赖且
 * 在 JDK 25 上有兼容风险；docx/xlsx 本质是 ZIP 包内 XML，用 JDK 内置 StAX/DOM 即可
 * 覆盖正文与单元格文本，零新增依赖。
 *
 * 安全：XML 解析强制关闭 DTD 与外部实体，防止 XXE（上传文件不可信）。
 * 能力边界：只提取可见文本，不解析公式结果缓存/图片内文字/批注；解析失败时由调用方
 * 抛 FILE_TYPE_UNSUPPORTED，不会静默产出空文本。
 */
@Component
public class OfficeTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(OfficeTextExtractor.class);
    private static final String DOCUMENT_XML = "word/document.xml";
    private static final String SHARED_STRINGS_XML = "xl/sharedStrings.xml";

    /** 提取 docx 正文文本：word/document.xml 中所有 w:t 节点按段落拼接。 */
    public String extractDocx(byte[] content) {
        String xml = readZipEntry(content, DOCUMENT_XML);
        if (xml == null) {
            throw new IllegalArgumentException("DOCX 缺少 " + DOCUMENT_XML + "，文件可能损坏");
        }
        Document doc = parseXml(xml);
        NodeList textNodes = doc.getElementsByTagNameNS("*", "t");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < textNodes.getLength(); i++) {
            Node node = textNodes.item(i);
            String text = node.getTextContent();
            if (text != null && !text.isEmpty()) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * 提取 xlsx 文本：先读 sharedStrings 共享字符串表，再按工作表逐行拼接单元格值。
     * inlineStr（t="inlineStr"）与数值（t 缺省）一并处理，共享串按索引回查。
     */
    public String extractXlsx(byte[] content) {
        Map<String, byte[]> entries = readZipEntries(content);
        byte[] sharedBytes = entries.get(SHARED_STRINGS_XML);
        List<String> sharedStrings = new ArrayList<>();
        if (sharedBytes != null) {
            Document sharedDoc = parseXml(new String(sharedBytes, StandardCharsets.UTF_8));
            NodeList siNodes = sharedDoc.getElementsByTagNameNS("*", "si");
            for (int i = 0; i < siNodes.getLength(); i++) {
                sharedStrings.add(textOf(siNodes.item(i)));
            }
        }
        StringBuilder sb = new StringBuilder();
        entries.entrySet().stream()
                .filter(e -> e.getKey().startsWith("xl/worksheets/sheet")
                        && e.getKey().endsWith(".xml"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Document sheet = parseXml(new String(entry.getValue(), StandardCharsets.UTF_8));
                    NodeList rows = sheet.getElementsByTagNameNS("*", "row");
                    for (int r = 0; r < rows.getLength(); r++) {
                        NodeList cells = rows.item(r).getChildNodes();
                        StringBuilder line = new StringBuilder();
                        for (int c = 0; c < cells.getLength(); c++) {
                            Node cell = cells.item(c);
                            if (!"c".equalsIgnoreCase(cell.getLocalName())) {
                                continue;
                            }
                            String type = attribute(cell, "t");
                            String value = cellValue(cell, type, sharedStrings);
                            if (!value.isEmpty()) {
                                if (line.length() > 0) {
                                    line.append('\t');
                                }
                                line.append(value);
                            }
                        }
                        if (line.length() > 0) {
                            sb.append(line).append('\n');
                        }
                    }
                });
        return sb.toString();
    }

    private String cellValue(Node cell, String type, List<String> sharedStrings) {
        String raw = textOf(cell);
        if (raw.isEmpty()) {
            return "";
        }
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(raw);
                return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
            } catch (NumberFormatException e) {
                log.warn("xlsx 共享字符串索引非法: {}", raw);
                return "";
            }
        }
        // inlineStr / 数值 / 布尔 / 日期序列值：直接使用文本节点
        return raw;
    }

    /** 递归收集节点下所有文本（覆盖 <v> 数值、<t> 文本、<is> 内联串、<si> 共享串） */
    private static String textOf(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(textOf(child));
            }
        }
        return sb.toString().trim();
    }

    private static String attribute(Node node, String name) {
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? "" : attr.getNodeValue();
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE 防护：上传文件不可信，禁 DTD 与外部实体解析
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Office XML 解析失败: " + e.getMessage(), e);
        }
    }

    private static String readZipEntry(byte[] content, String target) {
        byte[] found = readZipEntries(content).get(target);
        return found == null ? null : new String(found, StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> readZipEntries(byte[] content) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (DOCUMENT_XML.equals(name) || SHARED_STRINGS_XML.equals(name)
                        || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))) {
                    entries.put(name, readAll(zis));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Office 文档 ZIP 解析失败: " + e.getMessage(), e);
        }
        return entries;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
