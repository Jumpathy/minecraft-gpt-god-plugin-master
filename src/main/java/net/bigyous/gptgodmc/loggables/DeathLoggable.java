package net.bigyous.gptgodmc.loggables;

import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class DeathLoggable extends BaseLoggable {
    private final String deathMessage;

    public DeathLoggable(PlayerDeathEvent event) {
        this.deathMessage = event.deathMessage() == null
                ? event.getPlayer().getName() + " died"
                : PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
    }

    @Override
    public String getLog() {
        return "DEATH: " + deathMessage;
    }

    @Override
    public String attentionReason() {
        return deathMessage;
    }
}
