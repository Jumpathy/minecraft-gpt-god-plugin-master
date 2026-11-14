package net.bigyous.gptgodmc.god.script;

import java.util.List;
import java.util.Set;

/**
 * The whole of what a script can see: the names the interpreter defines, and the text that teaches them to
 * the god. Pure. A name not listed here does not exist inside a script.
 */
public final class ScriptApi {
    private ScriptApi() {
    }

    public enum Kind {
        /** Gives a value back: the world is visited at once. */
        ASK,
        /** Gives nothing back: kept until the next wait, ask or the script's end, then done in one visit. */
        DO,
        /** Handled by the interpreter itself (it may still end in host calls). */
        ENGINE
    }

    /**
     * @param name "world.set", or a bare name such as "wait"
     * @param min  the fewest arguments it takes
     * @param sig  how it is written, for error messages
     */
    public record Fn(String name, int min, Kind kind, String sig) {
    }

    public static final List<Fn> ALL = List.of(
            new Fn("world.block", 3, Kind.ASK, "world.block(x, y, z)"),
            new Fn("world.top", 2, Kind.ASK, "world.top(x, z)"),
            new Fn("world.at", 1, Kind.ASK, "world.at(target)"),
            new Fn("world.set", 4, Kind.ENGINE, "world.set(x, y, z, block)"),
            new Fn("world.fill", 7, Kind.ENGINE, "world.fill(x1, y1, z1, x2, y2, z2, block)"),
            new Fn("world.lightning", 3, Kind.DO, "world.lightning(x, y, z, harmless)"),
            new Fn("world.explode", 3, Kind.ENGINE, "world.explode(x, y, z, power, fire)"),
            new Fn("world.spawn", 4, Kind.ASK, "world.spawn(entity, x, y, z, count)"),
            new Fn("world.display", 4, Kind.ASK, "world.display(kind, x, y, z, options)"),
            new Fn("world.info", 3, Kind.ASK, "world.info(x, y, z)"),
            new Fn("world.ray", 6, Kind.ASK, "world.ray(x, y, z, dx, dy, dz, range)"),
            new Fn("world.near", 4, Kind.ASK, "world.near(x, y, z, r, type)"),
            new Fn("entity.get", 1, Kind.ASK, "entity.get(id)"),
            new Fn("entity.move", 4, Kind.DO, "entity.move(id, x, y, z, speed)"),
            new Fn("entity.teleport", 4, Kind.DO, "entity.teleport(id, x, y, z)"),
            new Fn("entity.push", 4, Kind.DO, "entity.push(id, vx, vy, vz)"),
            new Fn("entity.target", 1, Kind.DO, "entity.target(id, player)"),
            new Fn("entity.set", 2, Kind.DO, "entity.set(id, options)"),
            new Fn("entity.remove", 1, Kind.DO, "entity.remove(id)"),
            new Fn("world.particle", 4, Kind.DO, "world.particle(name, x, y, z, count, spread, color)"),
            new Fn("world.sound", 4, Kind.DO, "world.sound(id, x, y, z, volume, pitch)"),
            new Fn("world.time", 0, Kind.ASK, "world.time(ticks)"),
            new Fn("world.weather", 0, Kind.ASK, "world.weather(kind)"),
            new Fn("players.list", 0, Kind.ASK, "players.list()"),
            new Fn("players.get", 1, Kind.ASK, "players.get(name)"),
            new Fn("players.looking", 1, Kind.ASK, "players.looking(name, range)"),
            new Fn("players.effect", 3, Kind.DO, "players.effect(name, effect, seconds, level)"),
            new Fn("players.title", 2, Kind.DO, "players.title(name, title, subtitle)"),
            new Fn("players.tell", 2, Kind.DO, "players.tell(name, text)"),
            new Fn("players.push", 4, Kind.DO, "players.push(name, vx, vy, vz)"),
            new Fn("players.teleport", 4, Kind.DO, "players.teleport(name, x, y, z)"),
            new Fn("power", 1, Kind.ENGINE, "power(name, args)"),
            new Fn("web.search", 1, Kind.ENGINE, "web.search(query, focus)"),
            new Fn("web.read", 1, Kind.ENGINE, "web.read(url, focus)"),
            new Fn("powers", 0, Kind.ENGINE, "powers()"),
            new Fn("command", 1, Kind.ASK, "command(text)"),
            new Fn("wait", 1, Kind.ENGINE, "wait(ticks)"),
            new Fn("log", 0, Kind.ENGINE, "log(value)"),
            new Fn("on", 2, Kind.ENGINE, "on(event, filter, fn)"),
            new Fn("every", 2, Kind.ENGINE, "every(ticks, fn)"),
            new Fn("off", 1, Kind.ENGINE, "off(id)"));

    /** What on() can listen for. */
    public static final List<String> EVENTS = List.of("enter", "hit", "break", "chat");

    /** Powers a script may never reach through power(): itself, routines (which may hold scripts), and the operator's. */
    public static final Set<String> NOT_POWERS = Set.of("script", "library_run", "library_save", "library_forget", "command_done");

    /** The part of the tool's description that teaches the language. Kept short: the god pays for it every thought. */
    public static final String HELP = "It sees only this (x y z are block coordinates in your world):\n"
            + "world.block(x,y,z) gives 'stone'; world.top(x,z) the ground's y; world.at(target) gives {x,y,z} of a mortal, "
            + "'vessel', 'spawn' or a structure.\n"
            + "world.set(x,y,z,block), world.fill(x1,y1,z1,x2,y2,z2,block): all of one script's blocks are one work of "
            + "yours (unbuild takes it back), never over mortals' structures.\n"
            + "world.info(x,y,z) gives {block,state,biome,light,sky,claim}. world.ray(x,y,z,dx,dy,dz[,range]) and "
            + "players.looking(name[,range]) give what is hit, {x,y,z,block,face,entity}, or null. world.near(x,y,z,r[,type]) "
            + "lists creatures {type,name,x,y,z,id}.\n"
            + "world.spawn(entity,x,y,z[,count]) gives the ids of the creatures made. world.display(kind,x,y,z,options) gives "
            + "the id of a weightless shape hung in the air, harming nothing: kind 'block', 'item' or 'text'; options "
            + "{block|item|text, scale, yaw, pitch, roll, glow, color, billboard, bright, seconds}.\n"
            + "On what this script made: entity.get(id); entity.move(id,x,y,z[,speed]) (a creature walks there; a shape "
            + "glides, speed = ticks); entity.teleport(id,x,y,z); entity.push(id,vx,vy,vz); entity.target(id,player); "
            + "entity.set(id,options) with {name,glowing,silent,gravity,ai,baby}, or a shape's options plus ticks to change "
            + "smoothly; entity.remove(id).\n"
            + "world.lightning(x,y,z[,harmless]), world.explode(x,y,z[,power,fire]), "
            + "world.particle(name,x,y,z[,count,spread,color]), world.sound(id,x,y,z[,volume,pitch]) with ids like "
            + "block.bell.use, world.time([0-24000]), world.weather(['clear'|'rain'|'thunder']).\n"
            + "players.list(), players.get(name) give {name,x,y,z,yaw,pitch,look:{x,y,z},health,food,held,sneaking}. "
            + "players.effect(name,effect,seconds[,level]), players.title(name,title[,subtitle]), players.tell(name,text), "
            + "players.push(name,vx,vy,vz), players.teleport(name,x,y,z); name may be 'all'.\n"
            + "power(name,args) uses any other power of yours, with the same arguments you would give it, and gives "
            + "{ok,text,image}: read its text and feed it into the next power (image true: you are shown the last picture a "
            + "power made, such as look or map, when the script ends). powers() lists their names. command(text) runs one "
            + "raw command and gives {ok,text}. web.search(query[,focus]) searches the web, web.read(url[,focus]) reads one "
            + "page; each gives {ok,text} (an answer with sources) and is one power use. Chain them, e.g. search a tune, "
            + "work its notes out in code, then power('compose_song', {title, melody, give_to}).\n"
            + "wait(ticks) (20 = 1 s; calls made between two waits happen in the same tick). log(value): the log is "
            + "what comes back to you. args: the arguments you passed.\n"
            + "on(event[,filter],fn) gives an id: 'enter' with filter {x,y,z,r} (a mortal steps within r blocks), 'hit', "
            + "'break', 'chat'; filter {player} narrows it; fn gets {player,x,y,z,...}. every(ticks,fn) repeats. off(id) "
            + "ends one. Handlers keep running after the last line, for a bounded time.\n"
            + "Plain JavaScript: no imports, files, timers or async, and no network but web.search and web.read. A mistake comes back with its line, so "
            + "fix it and run it again. Example:\n"
            + "var c = world.at('vessel');\n"
            + "for (var i = 0; i < 30; i++) {\n"
            + "  world.set(c.x + Math.round(4 * Math.cos(i / 3)), c.y + (i >> 1), c.z + Math.round(4 * Math.sin(i / 3)), 'amethyst_block');\n"
            + "  if (i % 5 == 4) wait(4);\n"
            + "}\n"
            + "world.lightning(c.x, c.y + 16, c.z, true);";

    public static Fn find(String name) {
        for (Fn f : ALL) if (f.name().equals(name)) return f;
        return null;
    }
}
