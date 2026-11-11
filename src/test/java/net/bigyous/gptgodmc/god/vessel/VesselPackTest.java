package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class VesselPackTest {

    @Test
    void layoutFitsAndRegionsDoNotOverlap() {
        VesselSkin.Layout layout = VesselSkin.LAYOUT;
        assertEquals(Integer.bitCount(layout.height()), 1, "power-of-two height " + layout.height());
        List<VesselSkin.Rect> rects = new ArrayList<>();
        for (VesselSkin.Region r : layout.regions().values()) {
            for (VesselSkin.Face f : VesselSkin.Face.values()) {
                VesselSkin.Rect rect = r.face(f);
                assertTrue(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.w() <= layout.width()
                        && rect.y() + rect.h() <= layout.height(), r.key() + " " + f + " outside the atlas");
                rects.add(rect);
            }
        }
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                VesselSkin.Rect a = rects.get(i), b = rects.get(j);
                boolean apart = a.x() + a.w() <= b.x() || b.x() + b.w() <= a.x() || a.y() + a.h() <= b.y()
                        || b.y() + b.h() <= a.y();
                assertTrue(apart, "regions overlap: " + a + " " + b);
            }
        }
    }

    @Test
    void faceMappingRoundTrips() {
        for (VesselSkin.Face f : VesselSkin.Face.values()) {
            float[] p = f.point(0.3, 0.7);
            double[] st = f.st(p[0], p[1], p[2]);
            assertEquals(0.3, st[0], 1e-6, f.name());
            assertEquals(0.7, st[1], 1e-6, f.name());
        }
    }

    @Test
    void everyPartHasAModelInEveryLook() {
        Map<String, byte[]> files = VesselPack.files();
        JsonObject meta = JsonParser.parseString(new String(files.get("pack.mcmeta"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
        assertEquals(VesselPack.PACK_FORMAT, meta.get("min_format").getAsInt());
        Pattern uv = Pattern.compile("\"uv\": \\[([^\\]]+)\\]");
        for (String look : VesselLook.NAMES) {
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/vessel/" + look + ".png"));
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/vessel/" + look + ".png.mcmeta"));
            for (String key : VesselSkin.keys()) {
                String item = new String(files.get("assets/gptgodmc/items/vessel/" + look + "/" + key + ".json"),
                        StandardCharsets.UTF_8);
                assertTrue(JsonParser.parseString(item).getAsJsonObject().getAsJsonObject("model").get("model")
                        .getAsString().equals("gptgodmc:item/vessel/" + look + "/" + key));
                String model = new String(files.get("assets/gptgodmc/models/item/vessel/" + look + "/" + key + ".json"),
                        StandardCharsets.UTF_8);
                JsonParser.parseString(model);
                Matcher m = uv.matcher(model);
                int faces = 0;
                while (m.find()) {
                    faces++;
                    for (String v : m.group(1).split(",")) {
                        double d = Double.parseDouble(v.trim());
                        assertTrue(d >= 0 && d <= 16, look + "/" + key + " uv " + d);
                    }
                }
                assertEquals(6, faces, look + "/" + key);
            }
        }
        // Every textured part of every look has a region; horns keep their blocks.
        for (String look : VesselLook.NAMES) {
            for (VesselModel.Part p : VesselModel.parts(VesselLook.resolve(look, null))) {
                if (p.piece().textured()) assertTrue(VesselSkin.keys().contains(p.piece().id()), p.piece().id());
            }
        }
    }

    @Test
    void packIsTheSameEveryTime() {
        VesselPack.Built a = VesselPack.build(), b = VesselPack.build();
        assertArrayEquals(a.sha1(), b.sha1());
        // About 160 KB per look (8 animated frames each); 24 looks.
        assertTrue(a.zip().length < 6_000_000, "pack is " + a.zip().length + " bytes");
    }

    @Test
    void itemTransformCoversTheSameBox() {
        Transformation block = new Transformation(new Vector3f(1, 2, 3), new Quaternionf().rotationZ(0.7f),
                new Vector3f(0.5f, 2f, 0.25f), new Quaternionf());
        Transformation item = VesselModel.toItem(block);
        // The block box's center (corner + rotated half size) is the item model's center.
        Vector3f center = block.getLeftRotation().transform(new Vector3f(0.25f, 1f, 0.125f)).add(1, 2, 3);
        assertTrue(item.getTranslation().distance(center) < 1e-5);
        assertEquals(block.getScale(), item.getScale());
        // The client's own half turn is undone: right rotation times a half turn is no turn.
        Quaternionf net = new Quaternionf(item.getRightRotation()).mul(new Quaternionf().rotationY((float) Math.PI));
        assertTrue(Math.abs(Math.abs(net.w) - 1) < 1e-5, "net rotation " + net);
    }

    @Test
    void urlsWorkForEveryKindOfHost() {
        assertEquals("http://play.example.net:8770/gptgodmc-vessel-0123456789ab.zip",
                VesselPackHost.url("play.example.net", 8770, "0123456789abcdef0123"));
        assertEquals("http://[::1]:8770/gptgodmc-vessel-0123456789ab.zip",
                VesselPackHost.url("::1", 8770, "0123456789abcdef0123"));
    }
}
