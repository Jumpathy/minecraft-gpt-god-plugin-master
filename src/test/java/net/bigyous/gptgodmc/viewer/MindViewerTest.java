package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The viewer's request helpers: host checks against DNS rebinding, cookies, queries, the page. */
class MindViewerTest {
    @Test
    void onlyLocalHostHeadersPassWithoutAToken() {
        assertTrue(MindViewer.localHost("127.0.0.1:4673"));
        assertTrue(MindViewer.localHost("localhost:4673"));
        assertTrue(MindViewer.localHost("[::1]:4673"));
        assertTrue(MindViewer.localHost(null));
        assertFalse(MindViewer.localHost("evil.example:4673"));
        assertFalse(MindViewer.localHost("127.0.0.1.evil.example"));
    }

    @Test
    void readsCookiesAndQueries() {
        assertEquals("abc", MindViewer.cookie("a=1; gptgod_viewer=abc; b=2", MindViewer.COOKIE));
        assertNull(MindViewer.cookie("a=1", MindViewer.COOKIE));
        assertNull(MindViewer.cookie(null, MindViewer.COOKIE));
        Map<String, String> q = MindViewer.query("god=main&since=12&perception&bad=%zz");
        assertEquals("main", q.get("god"));
        assertEquals("12", q.get("since"));
        assertEquals("", q.get("perception"));
        assertFalse(q.containsKey("bad"), "a malformed pair is skipped");
    }

    @Test
    void thePageIsBundledWithACsrfSlot() throws Exception {
        String page = new String(MindViewer.loadPage(), StandardCharsets.UTF_8);
        assertTrue(page.contains("__CSRF__"));
        assertTrue(page.contains("<!--SAMPLE-->"));
        assertTrue(page.contains("/api/thoughts"));
        assertFalse(page.contains("http://") && page.contains("<script src"), "no external scripts");
    }
}
