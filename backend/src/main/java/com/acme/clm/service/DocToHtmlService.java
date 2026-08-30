package com.acme.clm.service;

import com.acme.clm.common.ApiExceptions;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Converts an uploaded document (.docx, .html, .txt, .md) into sanitized HTML so admins can
 * upload a Word document as a template body instead of editing HTML by hand.
 */
@Service
public class DocToHtmlService {

    private static final Logger log = LoggerFactory.getLogger(DocToHtmlService.class);
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private final Safelist safelist = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "hr", "table", "thead", "tbody", "tr", "td", "th");

    public String convert(MultipartFile file) {
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase();
        try {
            String html;
            if (name.endsWith(".docx")) {
                html = docxToHtml(rawDocumentXml(file));
            } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                html = Jsoup.parse(new String(file.getBytes(), StandardCharsets.UTF_8)).body().html();
            } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                StringBuilder sb = new StringBuilder();
                for (String para : new String(file.getBytes(), StandardCharsets.UTF_8).split("\\r?\\n\\s*\\r?\\n")) {
                    if (para.isBlank()) continue;
                    sb.append("<p>").append(Jsoup.clean(para.strip(), Safelist.none())).append("</p>");
                }
                html = sb.toString();
            } else {
                throw new ApiExceptions.BadRequestException(
                        "Unsupported format: upload a .docx (Word), .html or .txt file. For legacy .doc, save it as .docx first.");
            }
            html = Jsoup.clean(html, "", safelist,
                    new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)).strip();
            if (html.isBlank()) throw new ApiExceptions.BadRequestException("The uploaded document appears to be empty.");
            return html;
        } catch (ApiExceptions.BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Document conversion failed for {}: {}", file.getOriginalFilename(), e.toString());
            throw new ApiExceptions.BadRequestException("Could not read the document: " + e.getMessage());
        }
    }

    /** Plain text of already-converted HTML, used for AI extraction over the document. */
    public String toPlainText(String html) {
        return Jsoup.parse(html == null ? "" : html).body().text();
    }

    /** Returns the decoded XML of word/document.xml inside the .docx package. */
    private Document rawDocumentXml(MultipartFile file) throws Exception {
        byte[] xml = null;
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    xml = zis.readAllBytes();
                    break;
                }
            }
        }
        if (xml == null) throw new ApiExceptions.BadRequestException("Not a valid .docx file (word/document.xml missing).");
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String docxToHtml(Document doc) {
        Element body = (Element) doc.getElementsByTagNameNS(W, "body").item(0);
        if (body == null) throw new ApiExceptions.BadRequestException("Not a valid .docx document structure.");

        StringBuilder out = new StringBuilder();
        boolean inList = false;
        for (Element p : elements(body, "p")) {
            String style = styleOf(p);
            String text = textOf(p);
            if (text.isBlank()) continue;
            if (hasNumbering(p)) {
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(renderRuns(p)).append("</li>");
                continue;
            }
            if (inList) { out.append("</ul>"); inList = false; }
            out.append(switch (style) {
                case "Heading1", "Title" -> "<h1>" + renderRuns(p) + "</h1>";
                case "Heading2" -> "<h2>" + renderRuns(p) + "</h2>";
                case "Heading3" -> "<h3>" + renderRuns(p) + "</h3>";
                case "Heading4" -> "<h4>" + renderRuns(p) + "</h4>";
                default -> "<p>" + renderRuns(p) + "</p>";
            });
        }
        if (inList) out.append("</ul>");

        for (Element tbl : elements(body, "tbl")) out.append(tableHtml(tbl));
        return out.toString();
    }

    private String tableHtml(Element tbl) {
        StringBuilder sb = new StringBuilder("<table>");
        for (Element tr : elements(tbl, "tr")) {
            sb.append("<tr>");
            for (Element tc : elements(tr, "tc")) {
                StringBuilder cell = new StringBuilder();
                for (Element p : elements(tc, "p")) {
                    String t = textOf(p).strip();
                    if (!t.isBlank()) cell.append("<p>").append(renderRuns(p)).append("</p>");
                }
                sb.append("<td>").append(cell.isEmpty() ? "&nbsp;" : cell).append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String styleOf(Element p) {
        for (Element pp : elements(p, "pPr")) {
            for (Element s : elements(pp, "pStyle")) {
                String v = val(s);
                if (!v.isBlank()) return v;
            }
        }
        return "";
    }

    private static boolean hasNumbering(Element p) {
        for (Element pp : elements(p, "pPr"))
            for (Element s : children(pp)) if ("numPr".equals(s.getLocalName())) return true;
        return false;
    }

    private static String textOf(Element container) {
        StringBuilder plain = new StringBuilder();
        for (Element r : elements(container, "r")) plain.append(runsText(r));
        return plain.toString();
    }

    private static String runsText(Element run) {
        StringBuilder sb = new StringBuilder();
        for (Node n = run.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("t".equals(n.getLocalName())) sb.append(n.getTextContent());
            else if ("tab".equals(n.getLocalName())) sb.append(" ");
        }
        return sb.toString();
    }

    private static String renderRuns(Element container) {
        StringBuilder out = new StringBuilder();
        for (Element r : elements(container, "r")) {
            String text = runsText(r);
            if (text.isBlank()) { if (!text.isEmpty()) out.append(" "); continue; }
            boolean bold = false;
            boolean italic = false;
            for (Element rPr : elements(r, "rPr")) {
                for (Element k : children(rPr)) {
                    String name = k.getLocalName();
                    String v = val(k);
                    if ("false".equals(v) || "0".equals(v) || "none".equals(v)) continue;
                    if ("b".equals(name)) bold = true;
                    if ("i".equals(name)) italic = true;
                }
            }
            String body = escape(text);
            if (bold) body = "<strong>" + body + "</strong>";
            if (italic) body = "<em>" + body + "</em>";
            out.append(body);
        }
        return out.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static List<Element> children(Element parent) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e) out.add(e);
        return out;
    }

    private static List<Element> elements(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        for (Element e : children(parent)) if (localName.equals(e.getLocalName())) out.add(e);
        return out;
    }

    private static String val(Node n) {
        NamedNodeMap attrs = n.getAttributes();
        if (attrs == null) return "";
        var v = attrs.getNamedItemNS(W, "val");
        if (v == null) v = attrs.getNamedItem("w:val");
        return v == null ? "" : String.valueOf(v.getNodeValue());
    }
}