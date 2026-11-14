package com.restrovision.security;

import java.util.regex.Pattern;

/**
 * Normalizes user-supplied feedback comments to safe plain-text.
 * Strips active HTML tags, script blocks, event handlers, and dangerous URL protocols
 * while preserving legitimate text, quotes, punctuation, newlines, and multilingual Unicode (Hindi/Marathi).
 * 
 * Complies with OWASP recommendation: store normalized plain text when downstream
 * consumers (such as React) natively encode text nodes, preventing double-encoding artifacts.
 */
public final class HtmlSanitizer {

    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?i)<script[\\s\\S]*?</script>");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?i)<style[\\s\\S]*?</style>");
    private static final Pattern IFRAME_BLOCK = Pattern.compile("(?i)<iframe[\\s\\S]*?</iframe>");
    private static final Pattern OBJECT_BLOCK = Pattern.compile("(?i)<(object|embed|applet)[\\s\\S]*?</\\1>");
    private static final Pattern GENERIC_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)\\bon[a-z]{3,20}\\s*=");
    private static final Pattern DANGEROUS_PROTOCOLS = Pattern.compile("(?i)(javascript|vbscript|data):");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");

    private HtmlSanitizer() {}

    public static String sanitizeComment(String raw) {
        if (raw == null) {
            return null;
        }

        // 1. Strip non-printable control characters (except newline, return, tab)
        String clean = CONTROL_CHARS.matcher(raw).replaceAll("");

        // 2. Strip active executable blocks entirely (tag + content)
        clean = SCRIPT_BLOCK.matcher(clean).replaceAll("");
        clean = STYLE_BLOCK.matcher(clean).replaceAll("");
        clean = IFRAME_BLOCK.matcher(clean).replaceAll("");
        clean = OBJECT_BLOCK.matcher(clean).replaceAll("");

        // 3. Strip any remaining HTML tags (leaving internal text content)
        clean = GENERIC_TAG.matcher(clean).replaceAll("");

        // 4. Strip dangerous protocol schemes and event handler assignments
        clean = DANGEROUS_PROTOCOLS.matcher(clean).replaceAll("");
        clean = EVENT_HANDLER.matcher(clean).replaceAll("");

        // 5. Replace any stray unclosed angle brackets with safe HTML entities
        clean = clean.replace("<", "&lt;").replace(">", "&gt;");

        // 6. Return trimmed normalized plain-text
        return clean.trim();
    }
}
