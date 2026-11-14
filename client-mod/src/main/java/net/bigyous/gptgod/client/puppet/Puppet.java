package net.bigyous.gptgod.client.puppet;

/**
 * The test-only remote control's doorstep: the one call from the mod's initializer and the two switches the
 * game hook (PuppetMixin) reads. For a normal player this is all that is ever loaded: {@link #start()} sees
 * that the JVM property gptgod.puppet.port is not set and returns, and both switches stay false.
 */
public final class Puppet {
    /** A puppet dig is going on: the game's own "attack key is up, stop digging" is skipped so it can continue. */
    public static volatile boolean digging;
    /** A puppet screenshot is waiting for the next frame. */
    public static volatile boolean shotPending;

    private Puppet() {
    }

    /** Starts puppet mode if -Dgptgod.puppet.port=<port> was given; otherwise does nothing. */
    public static void start() {
        String port = System.getProperty("gptgod.puppet.port");
        if (port == null || port.isBlank()) return;
        PuppetServer.start();
    }

    /** A frame has just been drawn while a screenshot was wanted. */
    public static void frame() {
        PuppetTasks.frame();
    }
}
