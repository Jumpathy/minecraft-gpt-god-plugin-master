package net.bigyous.gptgodmc.testutil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;

/**
 * Just enough of a Bukkit server for code that calls Material.createBlockData(): block data are
 * proxies that remember their properties.
 */
public final class FakeServer {
    private static boolean installed = false;

    private FakeServer() {
    }

    public static synchronized void install() {
        if (installed || Bukkit.getServer() != null) {
            installed = true;
            return;
        }
        Server server = (Server) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), new Class<?>[] { Server.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLogger" -> Logger.getLogger("fake-server");
                    case "getName", "getVersion", "getBukkitVersion", "getMinecraftVersion" -> "test";
                    case "createBlockData" -> args != null && args.length >= 1 && args[0] instanceof Material m
                            ? blockData(m, new HashMap<>()) : null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeServer";
                    default -> defaultValue(method.getReturnType());
                });
        try {
            // Bukkit.setServer asks the real server for its build info; a fake one has none.
            java.lang.reflect.Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not install the fake server", e);
        }
        installed = true;
    }

    static BlockData blockData(Material m, Map<String, Object> props) {
        List<Class<?>> ifaces = new ArrayList<>();
        ifaces.add(BlockData.class);
        String n = m.name();
        if (n.endsWith("_STAIRS")) ifaces.add(Stairs.class);
        else if (n.endsWith("_DOOR")) ifaces.add(Door.class);
        else if (n.endsWith("_BED")) ifaces.add(Bed.class);
        else if (n.endsWith("LANTERN")) ifaces.add(Lantern.class);
        else if (n.equals("IRON_BARS") || n.endsWith("GLASS_PANE")) ifaces.add(MultipleFacing.class);
        else if (n.equals("LADDER") || n.equals("FURNACE") || n.equals("CHEST") || n.equals("END_ROD")) {
            ifaces.add(Directional.class);
        }
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                switch (name) {
                    case "getMaterial":
                        return m;
                    case "clone":
                        return blockData(m, new HashMap<>(props));
                    case "getFaces":
                    case "getAllowedFaces":
                        return EnumSet.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                                BlockFace.UP, BlockFace.DOWN);
                    case "rotate":
                    case "mirror":
                        return null;
                    case "getAsString":
                        return "minecraft:" + m.name().toLowerCase() + props;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "FakeBlockData[" + m + " " + props + "]";
                    default:
                        break;
                }
                if (name.startsWith("set") && args != null) {
                    props.put(name.substring(3) + (args.length > 1 ? ":" + args[0] : ""), args[args.length - 1]);
                    return null;
                }
                if ((name.startsWith("get") || name.startsWith("is")) && props.containsKey(name.replaceFirst("^(get|is)", ""))) {
                    return props.get(name.replaceFirst("^(get|is)", ""));
                }
                return defaultValue(method.getReturnType());
            }
        };
        return (BlockData) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), ifaces.toArray(new Class<?>[0]), h);
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class || type == byte.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        if (type == Set.class) return Set.of();
        if (type == List.class) return List.of();
        return null;
    }
}
