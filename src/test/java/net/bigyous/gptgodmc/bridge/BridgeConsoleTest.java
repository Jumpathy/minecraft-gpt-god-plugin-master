package net.bigyous.gptgodmc.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Edicts;

/** The debug bridge's console commands may give binding commands; nobody else gains anything by it. */
class BridgeConsoleTest {

    /** A sender (or player) that answers hasPermission with `admin` and nothing else. */
    private static <T extends CommandSender> T fake(Class<T> type, boolean admin) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission", "isOp" -> admin;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "getName", "toString" -> "fake";
                    default -> null;
                }));
    }

    @Test
    void onlyTheBridgesOwnSenderCountsAsTheConsole() {
        CommandSender bridge = fake(CommandSender.class, true);
        // The same kind of sender made by something else, e.g. the god's own run_command power.
        CommandSender other = fake(CommandSender.class, true);
        Player mortal = fake(Player.class, false);
        Player operator = fake(Player.class, true);

        assertFalse(Edicts.mayCommand(bridge), "outside a bridge command it is nobody");
        assertFalse(DebugBridge.isConsole(null));
        DebugBridge.asConsole(bridge, () -> {
            assertTrue(DebugBridge.isConsole(bridge));
            assertTrue(Edicts.mayCommand(bridge), "the bridge speaks as the console");
            assertFalse(Edicts.mayCommand(other), "an identity check, not a kind of sender");
            assertFalse(Edicts.mayCommand(mortal), "players without gptgodmc.admin are still refused");
            assertTrue(Edicts.mayCommand(operator));
        });
        assertFalse(DebugBridge.isConsole(bridge), "only while its command runs");
        assertFalse(Edicts.mayCommand(bridge));
        // A player is judged by permission alone, whatever the bridge is doing.
        DebugBridge.asConsole(mortal, () -> assertFalse(Edicts.mayCommand(mortal)));
    }
}
