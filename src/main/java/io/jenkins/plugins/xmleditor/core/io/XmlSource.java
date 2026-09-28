package io.jenkins.plugins.xmleditor.core.io;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The raw bytes of an XML file decoded to text, remembering everything needed to write it back identically:
 * charset, byte order mark and dominant line separator.
 */
public final class XmlSource {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16BE = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] BOM_UTF16LE = {(byte) 0xFF, (byte) 0xFE};

    private static final Pattern DECLARED_ENCODING =
            Pattern.compile("^<\\?xml\\s[^>]*?encoding\\s*=\\s*[\"']([A-Za-z][A-Za-z0-9._\\-]*)[\"']");

    private final String text;
    private final Charset charset;
    private final byte[] bom;
    private final String lineSeparator;

    private XmlSource(String text, Charset charset, byte[] bom) {
        this.text = text;
        this.charset = charset;
        this.bom = bom;
        this.lineSeparator = detectLineSeparator(text);
    }

    public static XmlSource decode(byte[] bytes) throws XmlEditorException {
        if (startsWith(bytes, BOM_UTF8)) {
            return new XmlSource(
                    decode(bytes, BOM_UTF8.length, StandardCharsets.UTF_8), StandardCharsets.UTF_8, BOM_UTF8);
        }
        if (startsWith(bytes, BOM_UTF16BE)) {
            return new XmlSource(
                    decode(bytes, BOM_UTF16BE.length, StandardCharsets.UTF_16BE),
                    StandardCharsets.UTF_16BE,
                    BOM_UTF16BE);
        }
        if (startsWith(bytes, BOM_UTF16LE)) {
            return new XmlSource(
                    decode(bytes, BOM_UTF16LE.length, StandardCharsets.UTF_16LE),
                    StandardCharsets.UTF_16LE,
                    BOM_UTF16LE);
        }
        if (startsWith(bytes, new byte[] {0, '<', 0, '?'})) {
            return new XmlSource(decode(bytes, 0, StandardCharsets.UTF_16BE), StandardCharsets.UTF_16BE, new byte[0]);
        }
        if (startsWith(bytes, new byte[] {'<', 0, '?', 0})) {
            return new XmlSource(decode(bytes, 0, StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE, new byte[0]);
        }
        Charset charset = declaredCharset(bytes);
        return new XmlSource(decode(bytes, 0, charset), charset, new byte[0]);
    }

    /** XML that is already text: no bytes to decode, written back (if ever) as UTF-8 without BOM. */
    public static XmlSource ofText(String text) {
        return new XmlSource(text, StandardCharsets.UTF_8, new byte[0]);
    }

    public String text() {
        return text;
    }

    public Charset charset() {
        return charset;
    }

    public boolean hasBom() {
        return bom.length > 0;
    }

    /** {@code "\r\n"} when CRLF line endings are the majority, {@code "\n"} otherwise. */
    public String lineSeparator() {
        return lineSeparator;
    }

    public boolean canEncode(CharSequence s) {
        return charset.newEncoder().canEncode(s);
    }

    /** Encodes {@code text} with the original charset and byte order mark. */
    public byte[] encode(String text) throws XmlEditorException {
        try {
            ByteBuffer encoded = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            ByteArrayOutputStream out = new ByteArrayOutputStream(bom.length + encoded.remaining());
            out.writeBytes(bom);
            out.write(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
            return out.toByteArray();
        } catch (CharacterCodingException e) {
            throw new XmlEditorException(
                    "The content contains characters that cannot be written with the file encoding " + charset.name(),
                    e);
        }
    }

    private static Charset declaredCharset(byte[] bytes) throws XmlEditorException {
        String head = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.ISO_8859_1);
        Matcher m = DECLARED_ENCODING.matcher(head);
        if (!m.find()) {
            return StandardCharsets.UTF_8;
        }
        String name = m.group(1);
        try {
            return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new XmlEditorException("Unsupported encoding declared in the XML file: " + name, e);
        }
    }

    private static String decode(byte[] bytes, int offset, Charset charset) throws XmlEditorException {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new XmlEditorException("The file is not valid " + charset.name() + " (check its encoding)", e);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String detectLineSeparator(String text) {
        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (i > 0 && text.charAt(i - 1) == '\r') {
                    crlf++;
                } else {
                    lf++;
                }
            }
        }
        return crlf > lf ? "\r\n" : "\n";
    }
}
