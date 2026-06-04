package com.pernasua.dreambot.mcp;

final class RuntimeText {
    private RuntimeText() {
    }

    static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String text = value
            .replace("<br>", " ")
            .replace("<br/>", " ")
            .replace("<br />", " ")
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
        return text.replaceAll("\\s+", " ").trim();
    }
}
