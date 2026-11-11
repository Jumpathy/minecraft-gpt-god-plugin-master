package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.Rites;

class RiteToolsTest {

    @Test
    void armorClimbsTheLadder() {
        assertEquals("IRON_HELMET", RiteTools.upgraded("LEATHER_HELMET", "next"));
        assertEquals("IRON_CHESTPLATE", RiteTools.upgraded("GOLDEN_CHESTPLATE", "next"));
        assertEquals("IRON_LEGGINGS", RiteTools.upgraded("CHAINMAIL_LEGGINGS", "next"));
        assertEquals("IRON_BOOTS", RiteTools.upgraded("COPPER_BOOTS", "next"));
        assertEquals("DIAMOND_BOOTS", RiteTools.upgraded("IRON_BOOTS", "next"));
        assertEquals("NETHERITE_CHESTPLATE", RiteTools.upgraded("DIAMOND_CHESTPLATE", "next"));
        assertNull(RiteTools.upgraded("NETHERITE_HELMET", "next"));
        assertEquals("DIAMOND_HELMET", RiteTools.upgraded("TURTLE_HELMET", "next"));
        assertEquals("NETHERITE_HELMET", RiteTools.upgraded("leather_helmet", "netherite"));
    }

    @Test
    void anExplicitTierNeverDowngrades() {
        assertEquals("DIAMOND_LEGGINGS", RiteTools.upgraded("LEATHER_LEGGINGS", "diamond"));
        assertNull(RiteTools.upgraded("DIAMOND_LEGGINGS", "diamond"));
        assertNull(RiteTools.upgraded("NETHERITE_LEGGINGS", "iron"));
        assertNull(RiteTools.upgraded("IRON_HELMET", "iron"));
        assertNull(RiteTools.upgraded("TURTLE_HELMET", "iron"));
        assertNull(RiteTools.upgraded("IRON_HELMET", "mithril"));
    }

    @Test
    void toolsAndWeaponsClimbToo() {
        assertEquals("IRON_SWORD", RiteTools.upgraded("WOODEN_SWORD", "next"));
        assertEquals("IRON_PICKAXE", RiteTools.upgraded("STONE_PICKAXE", "next"));
        assertEquals("IRON_AXE", RiteTools.upgraded("GOLDEN_AXE", "next"));
        assertEquals("IRON_SHOVEL", RiteTools.upgraded("COPPER_SHOVEL", "next"));
        assertEquals("DIAMOND_HOE", RiteTools.upgraded("IRON_HOE", "next"));
        assertEquals("NETHERITE_SPEAR", RiteTools.upgraded("DIAMOND_SPEAR", "next"));
        assertEquals("NETHERITE_PICKAXE", RiteTools.upgraded("WOODEN_PICKAXE", "netherite"));
    }

    @Test
    void otherThingsCannotBeUpgraded() {
        for (String name : List.of("BOW", "SHIELD", "ELYTRA", "DIAMOND", "IRON_INGOT", "STICK", "TRIDENT",
                "IRON_HORSE_ARMOR", "MACE", "CARVED_PUMPKIN", "PICKAXE", "")) {
            assertNull(RiteTools.upgraded(name, "next"), name);
        }
        assertNull(RiteTools.upgraded(null, "next"));
    }

    @Test
    void bothToolsHaveDescribedSchemas() {
        List<GodTool> tools = RiteTools.all();
        assertEquals(List.of("empower", "rite"), tools.stream().map(GodTool::name).toList());
        for (GodTool t : tools) {
            assertTrue(t.description().length() > 80, t.name());
            assertTrue(t.mainThread(), t.name());
            t.schema().properties().forEach((k, v) -> assertNotNull(v.get("description"), t.name() + "." + k));
        }
        assertEquals(List.of("player"), tools.get(0).schema().required());
        assertEquals(Rites.kindNames(), tools.get(1).schema().properties().get("kind").get("enum"));
    }

    @Test
    void powersGetFittingRites() {
        assertEquals(Rites.Kind.GIFT, RiteTools.riteFor("give_item", input("player", "Steve")));
        assertEquals(Rites.Kind.GIFT, RiteTools.riteFor("supply_chest", input("player", "Steve")));
        assertEquals(Rites.Kind.HEALING, RiteTools.riteFor("heal", input("player", "all")));
        assertEquals(Rites.Kind.SUMMON, RiteTools.riteFor("spawn_entity", input("near", "Steve")));
        assertEquals(Rites.Kind.FORGE, RiteTools.riteFor("build", input("at", "Steve")));
        assertEquals(Rites.Kind.BLINK, RiteTools.riteFor("teleport", input("player", "Steve")));
        assertEquals(Rites.Kind.SIGIL, RiteTools.riteFor("set_quest", input("for", "Steve")));
        assertEquals(Rites.Kind.RESURRECTION, RiteTools.riteFor("revive", input("player", "Steve")));
        JsonObject xp = input("player", "Steve");
        xp.addProperty("levels", 5);
        assertEquals(Rites.Kind.ANOINT, RiteTools.riteFor("grant_xp", xp));
        xp.addProperty("levels", -5);
        assertNull(RiteTools.riteFor("grant_xp", xp));
        assertEquals(Rites.Kind.CURSE, RiteTools.riteFor("apply_effect", input("effect", "Minecraft:Poison")));
        assertEquals(Rites.Kind.CURSE, RiteTools.riteFor("apply_effect", input("effect", "mining fatigue")));
        assertEquals(Rites.Kind.CURSE, RiteTools.riteFor("apply_effect", input("effect", "fire")));
        assertEquals(Rites.Kind.BLESSING, RiteTools.riteFor("apply_effect", input("effect", "speed")));
        assertNull(RiteTools.riteFor("empower", input("player", "Steve")));
        assertNull(RiteTools.riteFor("rite", input("target", "Steve")));
        assertNull(RiteTools.riteFor("smite", input("player", "Steve")));
    }

    private static JsonObject input(String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        return o;
    }
}
