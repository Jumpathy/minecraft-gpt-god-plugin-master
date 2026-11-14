package net.bigyous.gptgod.client.puppet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.bigyous.gptgod.client.puppet.PuppetServer.Reply;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the puppet does over several client ticks: holding movement keys, walking to a place, digging, holding
 * the use key, a row of attacks, and the screenshot that waits for the next frame. Runs at the end of every
 * client tick, on the client thread. Two things can run at once: one that moves the body (move or goto) and one
 * that uses the hands (dig, use or attack); a new one replaces the running one of its kind.
 */
final class PuppetTasks {
    /** Client ticks since the game started. */
    static volatile long now;
    static final boolean AUTO_PACK = PuppetServer.flag("autoPack");
    static final boolean AUTO_RESPAWN = PuppetServer.flag("autoRespawn");
    static final boolean KEEP_ALIVE = PuppetServer.flag("keepAlive");

    private PuppetTasks() {
    }

    // ---- keys ----

    /** The keys the puppet may hold. Set straight on the game's key mappings: no real key is ever pressed. */
    enum Key {
        FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, USE;

        KeyMapping of(Options o) {
            return switch (this) {
                case FORWARD -> o.keyUp;
                case BACK -> o.keyDown;
                case LEFT -> o.keyLeft;
                case RIGHT -> o.keyRight;
                case JUMP -> o.keyJump;
                case SNEAK -> o.keyShift;
                case SPRINT -> o.keySprint;
                case USE -> o.keyUse;
            };
        }
    }

    /** Asked for this tick by the running tasks; held = what was set last tick. */
    private static final EnumSet<Key> want = EnumSet.noneOf(Key.class);
    private static final EnumSet<Key> held = EnumSet.noneOf(Key.class);

    static void want(Key key) {
        want.add(key);
    }

    /**
     * Sets the wanted keys down and lets go of those no longer wanted. Repeated every tick, because opening a
     * screen or an unfocused window releases every key. Keys the puppet never asked for are not touched.
     */
    private static void applyKeys(Minecraft mc) {
        for (Key key : Key.values()) {
            boolean down = want.contains(key);
            if (!down && !held.contains(key)) continue;
            KeyMapping mapping = key.of(mc.options);
            if (mapping.isDown() != down) {
                mapping.setDown(down);
                if (mapping.isDown() != down) mapping.setDown(true); // a key set to "toggle" in the options flips on each press
            }
        }
        held.clear();
        held.addAll(want);
        want.clear();
    }

    // ---- tasks ----

    abstract static class Task {
        final String name;
        final Reply reply;
        int age;

        Task(String name, Reply reply) {
            this.name = name;
            this.reply = reply;
        }

        /** One client tick (age counts from 1). True when the task is over. */
        abstract boolean tick(Minecraft mc, LocalPlayer player);

        /** Stopped from outside: POST /stop, a newer task of its kind, or the player left the world. */
        abstract void cancel(Minecraft mc, String why);
    }

    private static Task motion;
    private static Task hands;

    static void startMotion(Minecraft mc, Task task) {
        if (motion != null) motion.cancel(mc, "replaced by " + task.name);
        motion = task;
    }

    static void startHands(Minecraft mc, Task task) {
        if (hands != null) hands.cancel(mc, "replaced by " + task.name);
        hands = task;
    }

    /** Stops everything that is running; returns the names of what was stopped. */
    static JsonArray stopAll(Minecraft mc, String why) {
        JsonArray stopped = new JsonArray();
        if (motion != null) {
            stopped.add(motion.name);
            motion.cancel(mc, why);
            motion = null;
        }
        if (hands != null) {
            stopped.add(hands.name);
            hands.cancel(mc, why);
            hands = null;
        }
        Puppet.digging = false;
        if (mc.gameMode != null && mc.player != null) mc.gameMode.stopDestroyBlock();
        return stopped;
    }

    static JsonArray running() {
        JsonArray names = new JsonArray();
        if (motion != null) names.add(motion.name);
        if (hands != null) names.add(hands.name);
        return names;
    }

    // ---- things that wait for a later tick ----

    private record Waiter(long deadline, BooleanSupplier finished, Runnable expired) {
    }

    private static final List<Waiter> waiters = new ArrayList<>();

    /** Runs r at the end of the client tick "ticks" from now. */
    static void after(int ticks, Runnable r) {
        waiters.add(new Waiter(now + ticks, () -> false, r));
    }

    /** Asks "finished" every tick until it says true; after maxTicks runs "expired" instead. */
    static void until(int maxTicks, BooleanSupplier finished, Runnable expired) {
        waiters.add(new Waiter(now + maxTicks, finished, expired));
    }

    // ---- the tick ----

    /** End of every client tick (registered only in puppet mode). */
    static void tick(Minecraft mc) {
        now++;
        if (KEEP_ALIVE) {
            mc.options.pauseOnLostFocus = false; // an unfocused window must not open the pause menu
            mc.getFramerateLimitTracker().onInputReceived(); // nor drop to the "away from keyboard" frame rate
        }
        if (AUTO_PACK) acceptPack(mc);
        PuppetEvents.tick(mc);

        for (Runnable job; (job = PuppetServer.JOBS.poll()) != null;) job.run();

        if (!waiters.isEmpty()) {
            for (Waiter w : new ArrayList<>(waiters)) {
                boolean over;
                try {
                    over = w.finished.getAsBoolean();
                    if (!over && now >= w.deadline) {
                        over = true;
                        w.expired.run();
                    }
                } catch (RuntimeException e) {
                    PuppetServer.LOG.warn("[gptgod-puppet] a waiting request failed", e);
                    over = true;
                }
                if (over) waiters.remove(w);
            }
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            if (motion != null || hands != null) stopAll(mc, "not in a world");
        } else {
            motion = step(mc, player, motion);
            hands = step(mc, player, hands);
        }
        applyKeys(mc);
    }

    private static Task step(Minecraft mc, LocalPlayer player, Task task) {
        if (task == null) return null;
        task.age++;
        try {
            return task.tick(mc, player) ? null : task;
        } catch (RuntimeException e) {
            PuppetServer.LOG.warn("[gptgod-puppet] {} failed", task.name, e);
            task.cancel(mc, "failed: " + e);
            task.reply.fail(500, task.name + " failed: " + e);
            return null;
        }
    }

    /** The server offers its resource pack: say yes, as a player would (the first button is Yes / Proceed). */
    private static Screen packAnswered;

    private static void acceptPack(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof ConfirmScreen) || screen == packAnswered) return;
        if (!screen.getClass().getName().endsWith("PackConfirmScreen")) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.active) {
                packAnswered = screen;
                button.onPress(new MouseButtonInfo(0, 0));
                PuppetEvents.add("info", "accepted the server resource pack");
                return;
            }
        }
    }

    // ---- move ----

    /** Holds movement keys for a number of ticks. */
    static final class Move extends Task {
        private final EnumSet<Key> keys;
        private final int ticks;
        private final boolean wait;

        Move(EnumSet<Key> keys, int ticks, boolean wait, Reply reply) {
            super("move", reply);
            this.keys = keys;
            this.ticks = ticks;
            this.wait = wait;
        }

        @Override
        boolean tick(Minecraft mc, LocalPlayer player) {
            if (age <= ticks) {
                want.addAll(keys);
                return false;
            }
            if (wait) reply.ok(where(player, false));
            return true;
        }

        @Override
        void cancel(Minecraft mc, String why) {
            if (wait && !reply.done()) {
                JsonObject o = mc.player == null ? PuppetServer.ok() : where(mc.player, true);
                o.addProperty("cancelled", true);
                o.addProperty("reason", why);
                reply.ok(o);
            }
        }

        private static JsonObject where(LocalPlayer player, boolean cancelled) {
            JsonObject o = PuppetServer.ok();
            PuppetActions.position(o, player);
            o.addProperty("onGround", player.onGround());
            o.addProperty("tick", now);
            if (!cancelled) o.addProperty("cancelled", false);
            return o;
        }
    }

    // ---- goto ----

    /** Walks straight at a point: turns toward it, holds forward, jumps at obstacles, swims up. No pathfinding. */
    static final class Goto extends Task {
        private final double x;
        private final double z;
        private final double range;
        private final int timeout;
        private final boolean sprint;
        private double markX;
        private double markZ;
        private int stalls;

        Goto(double x, double z, double range, int timeout, boolean sprint, Reply reply) {
            super("goto", reply);
            this.x = x;
            this.z = z;
            this.range = range;
            this.timeout = timeout;
            this.sprint = sprint;
        }

        @Override
        boolean tick(Minecraft mc, LocalPlayer player) {
            double dx = x - player.getX();
            double dz = z - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= range) return finish(player, distance, "arrived");
            if (player.isDeadOrDying()) return finish(player, distance, "dead");
            if (age > timeout) return finish(player, distance, "timeout");
            if (age == 1) {
                markX = player.getX();
                markZ = player.getZ();
            } else if (age % 40 == 0) { // two seconds with hardly any progress, twice in a row: stuck
                double moved = Math.hypot(player.getX() - markX, player.getZ() - markZ);
                stalls = moved < 0.4 ? stalls + 1 : 0;
                markX = player.getX();
                markZ = player.getZ();
                if (stalls >= 2) return finish(player, distance, "stuck");
            }
            PuppetActions.turn(player, (float) (Mth.atan2(-dx, dz) * 180 / Math.PI), player.getXRot());
            want(Key.FORWARD);
            if (sprint) want(Key.SPRINT);
            if (player.isInWater() || player.isInLava() || player.horizontalCollision && player.onGround()) want(Key.JUMP);
            return false;
        }

        private boolean finish(LocalPlayer player, double distance, String reason) {
            JsonObject o = PuppetServer.ok();
            o.addProperty("arrived", reason.equals("arrived"));
            o.addProperty("reason", reason);
            PuppetActions.position(o, player);
            o.addProperty("distance", PuppetActions.round(distance));
            o.addProperty("ticks", age);
            reply.ok(o);
            return true;
        }

        @Override
        void cancel(Minecraft mc, String why) {
            if (reply.done()) return;
            JsonObject o = PuppetServer.ok();
            o.addProperty("arrived", false);
            o.addProperty("reason", "cancelled: " + why);
            if (mc.player != null) {
                PuppetActions.position(o, mc.player);
                o.addProperty("distance", PuppetActions.round(Math.hypot(x - mc.player.getX(), z - mc.player.getZ())));
            }
            o.addProperty("ticks", age);
            reply.ok(o);
        }
    }

    // ---- dig ----

    /** Keeps hitting one block, as holding the attack button does, until it changes or time runs out. */
    static final class Dig extends Task {
        private final BlockPos pos;
        private final Direction face;
        private final BlockState before;
        private final JsonObject block;
        private final int maxTicks;
        private int changedAt = -1;

        Dig(BlockPos pos, Direction face, BlockState before, JsonObject block, int maxTicks, Reply reply) {
            super("dig", reply);
            this.pos = pos;
            this.face = face;
            this.before = before;
            this.block = block;
            this.maxTicks = maxTicks;
        }

        @Override
        boolean tick(Minecraft mc, LocalPlayer player) {
            // A different block, not merely a different state: redstone ore lights up when it is hit.
            boolean changed = mc.level.getBlockState(pos).getBlock() != before.getBlock();
            if (changed) {
                // The client breaks the block at once and the server may put it back: wait a moment to be sure.
                if (changedAt < 0) {
                    changedAt = age;
                    Puppet.digging = false;
                }
                return age - changedAt >= 6 && finish(true, null);
            }
            if (changedAt >= 0) return finish(false, "the server put the block back");
            if (age > maxTicks) {
                mc.gameMode.stopDestroyBlock();
                return finish(false, "not broken after " + maxTicks + " ticks");
            }
            Puppet.digging = true;
            if (mc.gameMode.continueDestroyBlock(pos, face)) PuppetActions.swingAttack(player);
            return false;
        }

        private boolean finish(boolean broken, String reason) {
            Puppet.digging = false;
            JsonObject o = PuppetServer.ok();
            o.addProperty("broken", broken);
            o.add("block", block);
            o.addProperty("ticks", age);
            if (reason != null) o.addProperty("reason", reason);
            reply.ok(o);
            return true;
        }

        @Override
        void cancel(Minecraft mc, String why) {
            Puppet.digging = false;
            if (mc.gameMode != null && mc.player != null) mc.gameMode.stopDestroyBlock();
            if (!reply.done()) finish(false, "cancelled: " + why);
        }
    }

    // ---- attack ----

    /** One or more left clicks, a fixed number of ticks apart. */
    static final class Attack extends Task {
        private final int times;
        private final int interval;
        private final JsonArray hits = new JsonArray();

        Attack(int times, int interval, Reply reply) {
            super("attack", reply);
            this.times = times;
            this.interval = interval;
        }

        @Override
        boolean tick(Minecraft mc, LocalPlayer player) {
            if ((age - 1) % interval != 0) return false;
            hits.add(PuppetActions.attackOnce(mc, player));
            if (hits.size() < times) return false;
            finish(null);
            return true;
        }

        private void finish(String cancelled) {
            JsonObject o = PuppetServer.ok();
            o.addProperty("times", hits.size());
            o.add("hit", hits.isEmpty() ? com.google.gson.JsonNull.INSTANCE : hits.get(0));
            o.add("hits", hits);
            if (cancelled != null) o.addProperty("cancelled", cancelled);
            reply.ok(o);
        }

        @Override
        void cancel(Minecraft mc, String why) {
            if (!reply.done()) finish(why);
        }
    }

    // ---- use ----

    /**
     * A right click; with ticks above 1 the use key then stays down (eating, drawing a bow) and is let go at the
     * end. With sneak, the sneak key goes down three ticks before the click, so the server knows, and stays down
     * until the end.
     */
    static final class Use extends Task {
        private static final int SNEAK_LEAD = 3;
        private final InteractionHand hand;
        private final int ticks;
        private final boolean sneak;
        private JsonObject result;
        private int clickedAt;

        Use(InteractionHand hand, int ticks, boolean sneak, Reply reply) {
            super("use", reply);
            this.hand = hand;
            this.ticks = ticks;
            this.sneak = sneak;
        }

        @Override
        boolean tick(Minecraft mc, LocalPlayer player) {
            if (sneak) want(Key.SNEAK);
            if (sneak && age <= SNEAK_LEAD) return false;
            if (result == null) {
                result = PuppetActions.useOnce(mc, player, hand);
                clickedAt = age;
            }
            int heldFor = age - clickedAt + 1;
            if (heldFor >= ticks) {
                result.addProperty("ticks", heldFor);
                reply.ok(result);
                return true; // the use key is not asked for again: the game lets go of the item next tick
            }
            // The game itself clicks again while the key is down and nothing is in use (every 4 ticks for a real
            // player); hold off that long so one click is not two.
            if (player.isUsingItem() || heldFor > 4) want(Key.USE);
            return false;
        }

        @Override
        void cancel(Minecraft mc, String why) {
            if (reply.done()) return;
            JsonObject o = result != null ? result : PuppetServer.ok();
            o.addProperty("cancelled", why);
            reply.ok(o);
        }
    }

    // ---- screenshot ----

    private record Shot(Path path, boolean showHudAgain, Reply reply, long asked) {
    }

    private static volatile Shot shot;

    /** Called on the client thread: the frame drawn next is saved to path. */
    static void screenshot(Minecraft mc, Path path, boolean hideHud, Reply reply) {
        Shot waiting = shot;
        if (waiting != null && !waiting.reply.done() && now - waiting.asked < 200) {
            throw new PuppetServer.Refusal(409, "another screenshot is still being taken");
        }
        boolean hide = hideHud && !mc.gui.hud.isHidden();
        if (hide) mc.gui.hud.toggle();
        shot = new Shot(path, hide, reply, now);
        Puppet.shotPending = true;
    }

    /** After each frame is drawn (PuppetMixin, through Puppet), while a screenshot is wanted: copy the picture and write the file. */
    static void frame() {
        Shot s = shot;
        shot = null;
        Puppet.shotPending = false;
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            Screenshot.takeScreenshot(target, image -> Util.ioPool().execute(() -> {
                try (image) {
                    Path parent = s.path.toAbsolutePath().getParent();
                    if (parent != null) Files.createDirectories(parent);
                    image.writeToFile(s.path);
                    JsonObject o = PuppetServer.ok();
                    o.addProperty("path", s.path.toAbsolutePath().toString());
                    o.addProperty("width", image.getWidth());
                    o.addProperty("height", image.getHeight());
                    s.reply.ok(o);
                } catch (Exception e) {
                    s.reply.fail(500, "could not write " + s.path + ": " + e);
                }
            }));
        } catch (RuntimeException e) {
            s.reply.fail(500, "screenshot failed: " + e);
        } finally {
            if (s.showHudAgain && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        }
    }
}
