package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/** Secrets never reach the mind viewer. */
class RedactTest {
    @Test
    void findsSecretKeysInConfig() {
        assertTrue(Redact.isSecretKey("anthropic.api-key"));
        assertTrue(Redact.isSecretKey("bridge.token"));
        assertTrue(Redact.isSecretKey("viewer.token"));
        assertTrue(Redact.isSecretKey("debug.webhook-url"));
        assertTrue(Redact.isSecretKey("voice.transcription.server.apiKey"));
        assertTrue(Redact.isSecretKey("db.password"));
        assertFalse(Redact.isSecretKey("anthropic.model"));
        assertFalse(Redact.isSecretKey("vessel.look"));
        assertFalse(Redact.isSecretKey("brain.max-tokens-per-thought"), "only the last segment counts, and whole");

        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("anthropic.api-key", "sk-ant-api03-SECRETSECRETSECRET");
        flat.put("anthropic.model", "claude-sonnet-5-5");
        flat.put("bridge.token", "");
        flat.put("voice.keys.token", List.of("tok-one-123", "tok-two-456"));
        flat.put("voice", Map.of());
        assertEquals(List.of("sk-ant-api03-SECRETSECRETSECRET", "", "tok-one-123", "tok-two-456"),
                Redact.secretsIn(flat));
    }

    @Test
    void masksSecretsAndKeyShapedText() {
        Redact r = Redact.of(List.of("hunter2-long-secret", "", "abc", "my\"quoted\"token"));
        assertEquals("pass is [redacted].", r.apply("pass is hunter2-long-secret."));
        assertEquals("abc stays", r.apply("abc stays"), "short values are not masked one by one");
        assertEquals("key [redacted] here", r.apply("key sk-ant-api03-abcdefghijklmnop here"));
        assertEquals("Authorization: [redacted]", r.apply("Authorization: Bearer 0123456789abcdef0123"));
        assertEquals("openai [redacted]", r.apply("openai sk-proj1234567890abcdefghijk"));
        assertNull(r.apply(null));
    }

    @Test
    void masksSecretsInsideJson() {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonObject o = new JsonObject();
        o.addProperty("perception", "the operator said my\"quoted\"token aloud");
        String json = gson.toJson(o);
        String out = Redact.of(List.of("my\"quoted\"token")).apply(json);
        assertFalse(out.contains("quoted"), out);
        assertTrue(out.contains(Redact.MASK));
    }
}
