package io.jenkins.plugins.xmleditor.core.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.xmleditor.core.XmlEditorException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class XmlSourceTest {

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static void assertRoundTrip(byte[] bytes) throws XmlEditorException {
        XmlSource src = XmlSource.decode(bytes);
        assertArrayEquals(bytes, src.encode(src.text()));
    }

    @Test
    void utf8WithoutBom() throws Exception {
        byte[] bytes = "<a>è</a>".getBytes(StandardCharsets.UTF_8);
        XmlSource src = XmlSource.decode(bytes);
        assertEquals("<a>è</a>", src.text());
        assertEquals(StandardCharsets.UTF_8, src.charset());
        assertFalse(src.hasBom());
        assertRoundTrip(bytes);
    }

    @Test
    void utf8WithBomIsRememberedAndRestored() throws Exception {
        byte[] bytes =
                concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "<a/>".getBytes(StandardCharsets.UTF_8));
        XmlSource src = XmlSource.decode(bytes);
        assertEquals("<a/>", src.text());
        assertTrue(src.hasBom());
        assertEquals(StandardCharsets.UTF_8, src.charset());
        assertRoundTrip(bytes);
    }

    @Test
    void utf16LittleEndianWithBom() throws Exception {
        byte[] bytes = concat(new byte[] {(byte) 0xFF, (byte) 0xFE}, "<a>ü</a>".getBytes(StandardCharsets.UTF_16LE));
        XmlSource src = XmlSource.decode(bytes);
        assertEquals("<a>ü</a>", src.text());
        assertEquals(StandardCharsets.UTF_16LE, src.charset());
        assertTrue(src.hasBom());
        assertRoundTrip(bytes);
    }

    @Test
    void utf16BigEndianWithBom() throws Exception {
        byte[] bytes = concat(new byte[] {(byte) 0xFE, (byte) 0xFF}, "<a/>".getBytes(StandardCharsets.UTF_16BE));
        XmlSource src = XmlSource.decode(bytes);
        assertEquals("<a/>", src.text());
        assertEquals(StandardCharsets.UTF_16BE, src.charset());
        assertRoundTrip(bytes);
    }

    @Test
    void utf16WithoutBomIsDetectedFromFirstBytes() throws Exception {
        byte[] le = "<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_16LE);
        assertEquals(StandardCharsets.UTF_16LE, XmlSource.decode(le).charset());
        byte[] be = "<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_16BE);
        assertEquals(StandardCharsets.UTF_16BE, XmlSource.decode(be).charset());
        assertRoundTrip(le);
        assertRoundTrip(be);
    }

    @Test
    void declaredIso88591IsHonoured() throws Exception {
        byte[] bytes = "<?xml version='1.0' encoding='ISO-8859-1'?><a>è</a>".getBytes(StandardCharsets.ISO_8859_1);
        XmlSource src = XmlSource.decode(bytes);
        assertEquals(StandardCharsets.ISO_8859_1, src.charset());
        assertTrue(src.text().endsWith("<a>è</a>"));
        assertRoundTrip(bytes);
    }

    @Test
    void invalidUtf8BytesAreRejected() {
        byte[] bytes = {'<', 'a', '>', (byte) 0xC3, (byte) 0x28, '<', '/', 'a', '>'};
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> XmlSource.decode(bytes));
        assertTrue(e.getMessage().contains("encoding"), e.getMessage());
    }

    @Test
    void unknownDeclaredEncodingIsRejected() {
        byte[] bytes = "<?xml version='1.0' encoding='NOPE-42'?><a/>".getBytes(StandardCharsets.US_ASCII);
        XmlEditorException e = assertThrows(XmlEditorException.class, () -> XmlSource.decode(bytes));
        assertTrue(e.getMessage().contains("NOPE-42"), e.getMessage());
    }

    @Test
    void lineSeparatorFollowsTheMajority() throws Exception {
        assertEquals(
                "\r\n",
                XmlSource.decode("<a>\r\n<b/>\r\n</a>".getBytes(StandardCharsets.UTF_8))
                        .lineSeparator());
        assertEquals(
                "\n",
                XmlSource.decode("<a>\n<b/>\n</a>".getBytes(StandardCharsets.UTF_8))
                        .lineSeparator());
        assertEquals(
                "\r\n",
                XmlSource.decode("<a>\r\n<b/>\r\n<c/>\n</a>".getBytes(StandardCharsets.UTF_8))
                        .lineSeparator());
        assertEquals(
                "\n",
                XmlSource.decode("<a>\r\n<b/>\n</a>".getBytes(StandardCharsets.UTF_8))
                        .lineSeparator());
        assertEquals(
                "\n", XmlSource.decode("<a/>".getBytes(StandardCharsets.UTF_8)).lineSeparator());
    }

    @Test
    void encodeRejectsCharactersTheCharsetCannotRepresent() throws Exception {
        XmlSource src = XmlSource.decode(
                "<?xml version='1.0' encoding='ISO-8859-1'?><a/>".getBytes(StandardCharsets.ISO_8859_1));
        assertFalse(src.canEncode("€"));
        assertTrue(src.canEncode("è"));
        assertThrows(XmlEditorException.class, () -> src.encode("<a>€</a>"));
    }
}
