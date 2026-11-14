package net.bigyous.gptgod.client.puppet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.bigyous.gptgod.client.GodClientMod;
import net.bigyous.gptgod.client.puppet.PuppetServer.Handler;
import net.bigyous.gptgod.client.puppet.PuppetServer.Refusal;
import net.bigyous.gptgod.client.puppet.PuppetServer.Reply;
import net.bigyous.gptgod.client.puppet.PuppetTasks.Key;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The puppet's endpoints: each handler runs on the client thread and does what a player's hands would do,
 * through the same game calls the keyboard and mouse lead to.
 */
final class PuppetActions {
    static final Map<String, Handler> ROUTES = new LinkedHashMap<>();

    static {
        ROUTES.put("GET /state", PuppetActions::state);
        ROUTES.put("GET /inventory", PuppetActions::inventory);
        ROUTES.put("POST /chat", PuppetActions::chat);
        ROUTES.put("POST /look", PuppetActions::look);
        ROUTES.put("POST /move", PuppetActions::move);
        ROUTES.put("POST /stop", PuppetActions::stop);
        ROUTES.put("POST /goto", PuppetActions::walkTo);
        ROUTES.put("POST /attack", PuppetActions::attack);
        ROUTES.put("POST /dig", PuppetActions::dig);
        ROUTES.put("POST /use", PuppetActions::use);
        ROUTES.put("POST /hotbar", PuppetActions::hotbar);
        ROUTES.put("POST /drop", PuppetActions::drop);
        ROUTES.put("POST /screen", PuppetActions::screen);
        ROUTES.put("POST /screenshot", PuppetActions::screenshot);
        ROUTES.put("POST /connect", PuppetActions::connect);
        ROUTES.put("POST /disconnect", PuppetActions::disconnect);
        ROUTES.put("POST /quit", PuppetActions::quit);
    }

    private PuppetActions() {
    }

    // ---- reading the request ----

    private static JsonElement field(JsonObject body, String key) {
        JsonElement e = body.get(key);
        return e == null || e.isJsonNull() ? null : e;
    }

    private static double number(JsonObject body, String key, Double fallback) {
        JsonElement e = field(body, key);
        if (e == null) {
            if (fallback == null) throw new Refusal(400, "missing number \"" + key + "\"");
            return fallback;
        }
        try {
            double v = e.getAsDouble();
            if (Double.isFinite(v)) return v;
        } catch (RuntimeException ex) {
            // refused below
        }
        throw new Refusal(400, "\"" + key + "\" must be a number");
    }

    private static int integer(JsonObject body, String key, int fallback, int min, int max) {
        double v = number(body, key, (double) fallback);
        if (v != Math.rint(v) || v < min || v > max) throw new Refusal(400, "\"" + key + "\" must be a whole number " + min + ".." + max);
        return (int) v;
    }

    private static boolean flag(JsonObject body, String key, boolean fallback) {
        JsonElement e = field(body, key);
        if (e == null) return fallback;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        throw new Refusal(400, "\"" + key + "\" must be true or false");
    }

    private static String text(JsonObject body, String key, String fallback) {
        JsonElement e = field(body, key);
        if (e == null) return fallback;
        if (e.isJsonPrimitive()) return e.getAsString();
        throw new Refusal(400, "\"" + key + "\" must be a string");
    }

    private static LocalPlayer player(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) throw new Refusal(409, "not in a world");
        return mc.player;
    }

    // ---- describing the game ----

    static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    static void position(JsonObject o, LocalPlayer player) {
        o.addProperty("x", round(player.getX()));
        o.addProperty("y", round(player.getY()));
        o.addProperty("z", round(player.getZ()));
    }

    private static JsonObject item(ItemStack stack) {
        JsonObject o = new JsonObject();
        o.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        o.addProperty("name", stack.getHoverName().getString());
        return o;
    }

    private static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static JsonObject entity(Entity entity) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "entity");
        o.addProperty("id", entity.getId());
        o.addProperty("entityType", typeId(entity));
        o.addProperty("name", entity.getName().getString());
        o.addProperty("uuid", entity.getUUID().toString());
        return o;
    }

    /** What the crosshair is on: {type:"block",id,x,y,z,face,distance}, {type:"entity",id,entityType,name,uuid,distance} or null. */
    static JsonElement lookingAt(Minecraft mc, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return JsonNull.INSTANCE;
        JsonObject o;
        if (hit instanceof EntityHitResult eh) {
            o = entity(eh.getEntity());
        } else if (hit instanceof BlockHitResult bh) {
            BlockPos pos = bh.getBlockPos();
            o = new JsonObject();
            o.addProperty("type", "block");
            o.addProperty("id", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
            o.addProperty("x", pos.getX());
            o.addProperty("y", pos.getY());
            o.addProperty("z", pos.getZ());
            o.addProperty("face", bh.getDirection().getName());
        } else {
            return JsonNull.INSTANCE;
        }
        o.addProperty("distance", round(player.getEyePosition().distanceTo(hit.getLocation())));
        return o;
    }

    private static String screenName(Screen screen) {
        String simple = screen.getClass().getSimpleName();
        if (!simple.isEmpty()) return simple;
        String full = screen.getClass().getName();
        return full.substring(full.lastIndexOf('.') + 1);
    }

    // ---- GET /state, GET /inventory ----

    private static void state(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean in = p != null && mc.level != null && mc.gameMode != null;
        Screen screen = mc.gui.screen();
        JsonObject o = PuppetServer.ok();
        o.addProperty("name", mc.getUser().getName());
        o.addProperty("inWorld", in);
        o.addProperty("loading", mc.gui.overlay() != null || !mc.isGameLoadFinished());
        o.addProperty("screen", screen == null ? null : screenName(screen));
        o.addProperty("screenTitle", screen == null ? null : screen.getTitle().getString());
        ServerData server = mc.getCurrentServer();
        o.addProperty("server", server == null ? null : server.ip);
        if (in) {
            position(o, p);
            o.addProperty("yaw", round(Mth.wrapDegrees(p.getYRot())));
            o.addProperty("pitch", round(p.getXRot()));
            o.addProperty("health", round(p.getHealth()));
            o.addProperty("food", p.getFoodData().getFoodLevel());
            o.addProperty("gamemode", mc.gameMode.getPlayerMode().getName());
            o.addProperty("dimension", mc.level.dimension().identifier().toString());
            o.addProperty("dead", p.isDeadOrDying());
            o.add("held", item(p.getMainHandItem()));
            o.addProperty("hotbarSlot", p.getInventory().getSelectedSlot());
            o.addProperty("onGround", p.onGround());
            o.addProperty("sneaking", p.isShiftKeyDown());
            o.add("lookingAt", lookingAt(mc, p));
            o.addProperty("dayTime", Math.floorMod(mc.level.getDefaultClockTime(), 24000L));
            o.addProperty("raining", mc.level.isRaining());
        } else {
            for (String key : new String[] { "x", "y", "z", "yaw", "pitch", "health", "food", "gamemode", "dimension", "dead", "held",
                    "hotbarSlot", "onGround", "sneaking", "lookingAt", "dayTime", "raining" }) {
                o.add(key, JsonNull.INSTANCE);
            }
        }
        JsonArray players = new JsonArray();
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            List<String> names = new ArrayList<>();
            for (PlayerInfo info : connection.getListedOnlinePlayers()) names.add(info.getProfile().name());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            names.forEach(players::add);
        }
        o.add("players", players);
        o.addProperty("modLinked", GodClientMod.STATE.linked());
        o.add("tasks", PuppetTasks.running());
        o.addProperty("eventSeq", PuppetEvents.latest());
        o.addProperty("tick", PuppetTasks.now);
        o.addProperty("fps", mc.getFps());
        reply.ok(o);
    }

    private static void inventory(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        Inventory inv = p.getInventory();
        JsonArray items = new JsonArray();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("slot", slot);
            o.asMap().putAll(item(stack).asMap());
            EquipmentSlot worn = Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);
            o.addProperty("where", worn != null ? worn.getName() : slot < Inventory.getSelectionSize() ? "hotbar" : "main");
            items.add(o);
        }
        JsonObject o = PuppetServer.ok();
        o.addProperty("hotbarSlot", inv.getSelectedSlot());
        o.add("items", items);
        JsonObject armor = new JsonObject();
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack stack = p.getItemBySlot(slot);
            armor.add(slot.getName(), stack.isEmpty() ? JsonNull.INSTANCE : item(stack));
        }
        o.add("armor", armor);
        o.add("offhand", p.getOffhandItem().isEmpty() ? JsonNull.INSTANCE : item(p.getOffhandItem()));
        reply.ok(o);
    }

    // ---- POST /chat ----

    private static void chat(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        String text = text(body, "text", "").trim();
        if (text.isEmpty()) throw new Refusal(400, "missing \"text\"");
        if (text.length() > 256) throw new Refusal(400, "\"text\" is longer than 256 characters");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < ' ' || c == 127 || c == '§') throw new Refusal(400, "\"text\" has a character chat does not allow");
        }
        boolean command = text.startsWith("/");
        if (command) p.connection.sendCommand(text.substring(1));
        else p.connection.sendChat(text);
        JsonObject o = PuppetServer.ok();
        o.addProperty("sent", command ? "command" : "chat");
        reply.ok(o);
    }

    // ---- POST /look ----

    /** Points the player there at once: the previous-tick angles too, so nothing turns back or eases in. */
    static void turn(LocalPlayer p, float yaw, float pitch) {
        float y = Mth.wrapDegrees(yaw);
        float x = Mth.clamp(pitch, -90, 90);
        p.setYRot(y);
        p.setXRot(x);
        p.yRotO = y;
        p.xRotO = x;
        p.setYHeadRot(y);
        p.yHeadRotO = y;
        p.yBodyRot = y;
        p.yBodyRotO = y;
        p.yBob = y;
        p.yBobO = y;
        p.xBob = x;
        p.xBobO = x;
    }

    private static void turnTo(LocalPlayer p, Vec3 point) {
        Vec3 eye = p.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1e-6 && Math.abs(dy) < 1e-6) return;
        float yaw = flat < 1e-6 ? p.getYRot() : (float) (Mth.atan2(-dx, dz) * 180 / Math.PI);
        turn(p, yaw, (float) (-Mth.atan2(dy, flat) * 180 / Math.PI));
    }

    /** The nearest entity within radius that is this player name, entity name, entity type id or entity number. */
    private static Entity find(Minecraft mc, LocalPlayer p, String what, double radius) {
        String key = what.trim();
        String asType = key.contains(":") ? key : "minecraft:" + key;
        Integer number = null;
        try {
            number = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            // a name or a type
        }
        Entity best = null;
        double bestSq = radius * radius;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == p) continue;
            boolean match = number != null ? e.getId() == number
                    : e.getName().getString().equalsIgnoreCase(key) || typeId(e).equalsIgnoreCase(asType);
            if (!match) continue;
            double sq = e.distanceToSqr(p);
            if (sq <= bestSq) {
                bestSq = sq;
                best = e;
            }
        }
        return best;
    }

    private static void look(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        JsonObject target = null;
        if (field(body, "entity") != null) {
            double radius = number(body, "radius", 32.0);
            Entity e = find(mc, p, text(body, "entity", ""), radius);
            if (e == null) throw new Refusal(404, "no entity \"" + text(body, "entity", "") + "\" within " + radius + " blocks");
            Vec3 centre = e.getBoundingBox().getCenter();
            turnTo(p, centre);
            target = entity(e);
            target.addProperty("x", round(centre.x));
            target.addProperty("y", round(centre.y));
            target.addProperty("z", round(centre.z));
            target.addProperty("distance", round(p.getEyePosition().distanceTo(centre)));
        } else if (field(body, "x") != null || field(body, "y") != null || field(body, "z") != null) {
            turnTo(p, new Vec3(number(body, "x", null), number(body, "y", null), number(body, "z", null)));
        } else if (field(body, "yaw") != null || field(body, "pitch") != null) {
            turn(p, (float) number(body, "yaw", (double) p.getYRot()), (float) number(body, "pitch", (double) p.getXRot()));
        } else {
            throw new Refusal(400, "give yaw and pitch, or x y z, or entity");
        }
        JsonObject aimed = target;
        // The crosshair's target is worked out in the next tick: answer after it.
        PuppetTasks.after(1, () -> {
            LocalPlayer now = mc.player;
            if (now == null || mc.level == null) {
                reply.fail(409, "not in a world");
                return;
            }
            JsonObject o = PuppetServer.ok();
            o.addProperty("yaw", round(Mth.wrapDegrees(now.getYRot())));
            o.addProperty("pitch", round(now.getXRot()));
            o.add("lookingAt", lookingAt(mc, now));
            if (aimed != null) o.add("target", aimed);
            reply.ok(o);
        });
    }

    // ---- POST /move, /stop, /goto ----

    private static void move(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        player(mc);
        EnumSet<Key> keys = EnumSet.noneOf(Key.class);
        if (flag(body, "forward", false)) keys.add(Key.FORWARD);
        if (flag(body, "back", false)) keys.add(Key.BACK);
        if (flag(body, "left", false)) keys.add(Key.LEFT);
        if (flag(body, "right", false)) keys.add(Key.RIGHT);
        if (flag(body, "jump", false)) keys.add(Key.JUMP);
        if (flag(body, "sneak", false)) keys.add(Key.SNEAK);
        if (flag(body, "sprint", false)) keys.add(Key.SPRINT);
        int ticks = integer(body, "ticks", 20, 1, 600);
        boolean wait = flag(body, "wait", false);
        if (wait) reply.allowTicks(ticks + 5);
        PuppetTasks.startMotion(mc, new PuppetTasks.Move(keys, ticks, wait, reply));
        if (!wait) {
            JsonObject o = PuppetServer.ok();
            o.addProperty("untilTick", PuppetTasks.now + ticks);
            o.addProperty("tick", PuppetTasks.now);
            reply.ok(o);
        }
    }

    private static void stop(JsonObject body, Map<String, String> query, Reply reply) {
        JsonObject o = PuppetServer.ok();
        o.add("stopped", PuppetTasks.stopAll(Minecraft.getInstance(), "stopped"));
        reply.ok(o);
    }

    private static void walkTo(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        player(mc);
        double x = number(body, "x", null);
        double z = number(body, "z", null);
        double range = Math.max(0.2, number(body, "range", 1.5));
        int timeout = integer(body, "timeoutTicks", 400, 1, 2400);
        reply.allowTicks(timeout + 10);
        PuppetTasks.startMotion(mc, new PuppetTasks.Goto(x, z, range, timeout, flag(body, "sprint", false), reply));
    }

    // ---- POST /attack, /dig ----

    /** The arm swing and the "I punched" packet that follow a left click. */
    static void swingAttack(LocalPlayer p) {
        p.swing(InteractionHand.MAIN_HAND, p.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
        p.connection.send(ServerboundPunchPacket.INSTANCE);
    }

    /** One left click on whatever the crosshair is on, as the game does it. Returns what was hit ({type:"miss"} for nothing). */
    static JsonObject attackOnce(Minecraft mc, LocalPlayer p) {
        HitResult hit = mc.hitResult;
        JsonObject what;
        if (lookingAt(mc, p) instanceof JsonObject seen) {
            what = seen;
        } else {
            what = new JsonObject();
            what.addProperty("type", "miss");
        }
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        String skip = p.isHandsBusy() ? "hands are busy" : mc.gameMode.isSpectator() ? "spectators cannot attack"
                : !held.isItemEnabled(mc.level.enabledFeatures()) || p.cannotAttackWithItem(held, 0) ? "cannot attack with the held item" : null;
        if (skip != null) {
            what.addProperty("skipped", skip);
            return what;
        }
        SwingAnimation swing = held.getAttackAnimation();
        PiercingWeapon piercing = held.get(DataComponents.PIERCING_WEAPON);
        if (piercing != null) {
            mc.gameMode.piercingAttack(swing, piercing);
            return what;
        }
        if (hit instanceof EntityHitResult eh && hit.getType() == HitResult.Type.ENTITY) {
            mc.gameMode.attack(p, eh.getEntity());
        } else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && !mc.level.getBlockState(bh.getBlockPos()).isAir()) {
            mc.gameMode.startDestroyBlock(bh.getBlockPos(), bh.getDirection());
        } else {
            p.resetAttackStrengthTicker();
        }
        p.swing(InteractionHand.MAIN_HAND, swing, false);
        p.connection.send(ServerboundPunchPacket.INSTANCE);
        return what;
    }

    private static void attack(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        player(mc);
        int times = integer(body, "times", 1, 1, 20);
        int interval = integer(body, "intervalTicks", 12, 1, 100);
        reply.allowTicks(times * interval);
        PuppetTasks.startHands(mc, new PuppetTasks.Attack(times, interval, reply));
    }

    private static void dig(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        int maxTicks = integer(body, "maxTicks", 200, 1, 1200);
        if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) {
            throw new Refusal(409, "not looking at a block within reach");
        }
        BlockState before = mc.level.getBlockState(bh.getBlockPos());
        if (before.isAir()) throw new Refusal(409, "not looking at a block within reach");
        JsonObject block = lookingAt(mc, p).getAsJsonObject();
        reply.allowTicks(maxTicks + 10);
        PuppetTasks.startHands(mc, new PuppetTasks.Dig(bh.getBlockPos(), bh.getDirection(), before, block, maxTicks, reply));
    }

    // ---- POST /use ----

    /**
     * One right click with one hand, in the game's own order: the entity under the crosshair, else the block,
     * else the held item in the air. Returns {ok,target,hand,via,result}: via is interact, useItemOn, useItem
     * or none; result is success, fail, pass, tryemptyhandinteraction or none.
     */
    static JsonObject useOnce(Minecraft mc, LocalPlayer p, InteractionHand hand) {
        JsonObject o = PuppetServer.ok();
        HitResult hit = mc.hitResult;
        o.add("target", lookingAt(mc, p));
        o.addProperty("hand", hand == InteractionHand.MAIN_HAND ? "main" : "off");
        ItemStack held = p.getItemInHand(hand);
        o.add("held", held.isEmpty() ? JsonNull.INSTANCE : item(held));
        String via = "none";
        InteractionResult result = null;
        if (p.isHandsBusy() || !held.isItemEnabled(mc.level.enabledFeatures())) {
            o.addProperty("skipped", p.isHandsBusy() ? "hands are busy" : "the held item is disabled");
        } else {
            SwingAnimation swing = held.getInteractAnimation();
            boolean settled = false;
            if (hit instanceof EntityHitResult eh && hit.getType() == HitResult.Type.ENTITY) {
                Entity e = eh.getEntity();
                if (!mc.level.getWorldBorder().isWithinBounds(e.blockPosition())) {
                    settled = true;
                } else if (p.isWithinEntityInteractionRange(e, 0.0)) {
                    via = "interact";
                    result = mc.gameMode.interact(p, e, eh, hand);
                    if (result instanceof InteractionResult.Success success) {
                        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) p.swing(hand, swing, false);
                        settled = true;
                    }
                }
            } else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                int count = held.getCount();
                via = "useItemOn";
                result = mc.gameMode.useItemOn(p, hand, bh);
                if (result instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                        p.swing(hand, swing, false);
                        if (!held.isEmpty() && (held.getCount() != count || p.hasInfiniteMaterials())) p.itemUsed(hand);
                    }
                    settled = true;
                } else if (result instanceof InteractionResult.Fail) {
                    settled = true;
                }
            }
            if (!settled && !held.isEmpty()) {
                via = "useItem";
                result = mc.gameMode.useItem(p, hand);
                if (result instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) p.swing(hand, swing, false);
                    p.itemUsed(hand);
                }
            }
        }
        o.addProperty("via", via);
        o.addProperty("result", result == null ? "none" : result.getClass().getSimpleName().toLowerCase(Locale.ROOT));
        o.addProperty("using", p.isUsingItem());
        return o;
    }

    private static void use(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        player(mc);
        String handName = text(body, "hand", "main").toLowerCase(Locale.ROOT);
        InteractionHand hand = switch (handName) {
            case "main", "mainhand", "main_hand" -> InteractionHand.MAIN_HAND;
            case "off", "offhand", "off_hand" -> InteractionHand.OFF_HAND;
            default -> throw new Refusal(400, "\"hand\" must be main or off");
        };
        int ticks = integer(body, "ticks", 1, 1, 200);
        reply.allowTicks(ticks + 10);
        PuppetTasks.startHands(mc, new PuppetTasks.Use(hand, ticks, flag(body, "sneak", false), reply));
    }

    // ---- POST /hotbar, /drop ----

    private static void hotbar(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        if (field(body, "slot") == null) throw new Refusal(400, "missing \"slot\" (0..8)");
        int slot = integer(body, "slot", 0, 0, 8);
        p.getInventory().setSelectedSlot(slot);
        JsonObject o = PuppetServer.ok();
        o.addProperty("slot", slot);
        o.add("held", item(p.getMainHandItem()));
        reply.ok(o);
    }

    private static void drop(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = player(mc);
        if (p.isSpectator()) throw new Refusal(409, "spectators cannot drop items");
        boolean all = flag(body, "all", false);
        ItemStack before = p.getMainHandItem();
        JsonObject o = PuppetServer.ok();
        o.add("dropped", before.isEmpty() ? JsonNull.INSTANCE : item(before));
        if (!before.isEmpty()) {
            if (!all) o.getAsJsonObject("dropped").addProperty("count", 1);
            mc.gameMode.dropItem(p, all);
        }
        o.add("held", item(p.getMainHandItem()));
        reply.ok(o);
    }

    // ---- POST /screen, /screenshot ----

    private static void screen(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        String action = text(body, "action", "").toLowerCase(Locale.ROOT);
        Screen screen = mc.gui.screen();
        JsonObject o = PuppetServer.ok();
        switch (action) {
            case "close" -> {
                LocalPlayer p = player(mc); // the title, connecting and loading screens are not closed this way
                if (screen instanceof DeathScreen || p.isDeadOrDying()) throw new Refusal(409, "dead: use {\"action\":\"respawn\"}");
                o.addProperty("closed", screen == null ? null : screenName(screen));
                if (screen != null) {
                    screen.onClose();
                    if (mc.gui.screen() == screen) mc.gui.setScreen(null);
                }
            }
            case "respawn" -> {
                LocalPlayer p = player(mc);
                if (!p.isDeadOrDying()) throw new Refusal(409, "not dead");
                p.respawn();
            }
            case "press" -> {
                String label = text(body, "text", "");
                if (screen == null) throw new Refusal(409, "no screen is open");
                List<String> seen = new ArrayList<>();
                AbstractButton found = null;
                for (GuiEventListener child : screen.children()) {
                    if (!(child instanceof AbstractButton button)) continue;
                    String name = button.getMessage().getString();
                    seen.add(name);
                    if (found == null && name.equalsIgnoreCase(label)) found = button;
                }
                if (found == null) throw new Refusal(404, "no button \"" + label + "\" on " + screenName(screen) + "; it has " + seen);
                if (!found.active) throw new Refusal(409, "the button \"" + label + "\" is not active yet");
                found.onPress(new MouseButtonInfo(0, 0));
                o.addProperty("pressed", found.getMessage().getString());
            }
            default -> throw new Refusal(400, "\"action\" must be close, respawn or press");
        }
        Screen after = mc.gui.screen();
        o.addProperty("screen", after == null ? null : screenName(after));
        reply.ok(o);
    }

    private static void screenshot(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        String name = text(body, "path", "");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) throw new Refusal(400, "\"path\" must end in .png");
        Path path;
        try {
            path = Path.of(name);
        } catch (RuntimeException e) {
            throw new Refusal(400, "\"path\" is not a valid file path");
        }
        if (!path.isAbsolute()) throw new Refusal(400, "\"path\" must be absolute");
        reply.allowTicks(100);
        PuppetTasks.screenshot(mc, path, flag(body, "hideHud", false), reply);
    }

    // ---- POST /connect, /disconnect, /quit ----

    private static void connect(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null || mc.level != null) throw new Refusal(409, "already in a world: POST /disconnect first");
        if (mc.gui.overlay() != null || !mc.isGameLoadFinished()) throw new Refusal(409, "the game is still loading");
        if (mc.gui.screen() instanceof ConnectScreen) throw new Refusal(409, "already connecting");
        String host = text(body, "host", "127.0.0.1").trim();
        if (host.isEmpty() || host.length() > 255) throw new Refusal(400, "bad \"host\"");
        int port = integer(body, "port", 25565, 1, 65535);
        String address = host + ":" + port;
        ServerData data = new ServerData("puppet", address, ServerData.Type.OTHER);
        if (PuppetTasks.AUTO_PACK) data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED); // take its resource pack unasked
        ConnectScreen.startConnecting(new TitleScreen(), mc, new ServerAddress(host, port), data, false, null);
        if (!flag(body, "wait", false)) {
            JsonObject o = PuppetServer.ok();
            o.addProperty("connecting", address);
            reply.ok(o);
            return;
        }
        int limit = 20 * 60;
        reply.allowTicks(limit);
        PuppetTasks.until(limit, () -> {
            if (mc.player != null && mc.level != null) {
                JsonObject o = PuppetServer.ok();
                o.addProperty("connected", address);
                position(o, mc.player);
                reply.ok(o);
                return true;
            }
            if (mc.gui.screen() instanceof DisconnectedScreen refused) {
                reply.fail(502, "could not join " + address + ": " + refused.getNarrationMessage().getString());
                return true;
            }
            return false;
        }, () -> reply.fail(504, "not in the world after 60 s"));
    }

    private static void disconnect(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        boolean was = mc.level != null;
        PuppetTasks.stopAll(mc, "disconnecting");
        if (was) {
            mc.disconnectFromWorld(Component.literal("puppet disconnect"));
            mc.gui.setScreen(new TitleScreen());
        } else if (mc.gui.screen() instanceof DisconnectedScreen) {
            mc.gui.setScreen(new TitleScreen());
        }
        JsonObject o = PuppetServer.ok();
        o.addProperty("wasConnected", was);
        reply.ok(o);
    }

    private static void quit(JsonObject body, Map<String, String> query, Reply reply) {
        Minecraft mc = Minecraft.getInstance();
        reply.ok(PuppetServer.ok());
        PuppetTasks.after(5, mc::stop); // a few ticks later, so this answer is sent first
    }
}
