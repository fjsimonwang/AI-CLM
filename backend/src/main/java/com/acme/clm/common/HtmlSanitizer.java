package com.acme.clm.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/** Allowlist-based HTML sanitizer for user-authored rich text (comments). */
@Component
public class HtmlSanitizer {

    private final Safelist safelist = Safelist.basic()
            .addTags("h3", "h4", "pre", "span", "hr")
            .addAttributes("span", "style")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addAttributes("code", "class")
            .preserveRelativeLinks(false);

    public String clean(String html) {
        if (html == null) return "";
        String cleaned = Jsoup.clean(html, "", safelist,
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
        return cleaned.strip();
    }

    public String plainText(String html) {
        if (html == null) return "";
        return Jsoup.parse(html).text();
    }
}
