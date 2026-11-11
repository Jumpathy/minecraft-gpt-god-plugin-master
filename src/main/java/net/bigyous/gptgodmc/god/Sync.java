package net.bigyous.gptgodmc.god;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.tools.ToolException;

/** Runs world-touching code on the server thread and waits for the result. */
public final class Sync {
    private Sync() {
    }

    public interface WorldCall<T> {
        T call() throws Exception;
    }

    public static <T> T call(WorldCall<T> work, long timeoutMs) throws ToolException {
        if (Bukkit.isPrimaryThread()) {
            try {
                return work.call();
            } catch (ToolException e) {
                throw e;
            } catch (Exception e) {
                throw wrap(e);
            }
        }
        if (!GPTGOD.get().isEnabled()) {
            throw new ToolException("The server is shutting down.");
        }
        // The server thread serves every god, so carry "which god is acting" across with the work.
        Realm realm = Realm.current();
        Callable<T> callable = () -> realm.call(work);
        Future<T> future = Bukkit.getScheduler().callSyncMethod(GPTGOD.get(), callable);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            throw new ToolException("The world did not respond in time (server busy).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Interrupted.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ToolException te) {
                throw te;
            }
            throw wrap(cause);
        }
    }

    private static ToolException wrap(Throwable t) {
        GPTGOD.LOGGER.warn("A god power failed", t);
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return new ToolException("The power failed: " + msg);
    }
}
