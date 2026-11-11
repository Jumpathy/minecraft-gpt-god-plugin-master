package net.bigyous.gptgodmc.loggables;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import net.bigyous.gptgodmc.GPTGOD;

/** Environmental and projectile damage to players (melee is covered by AttackLoggable). */
public class DamageLoggable extends BaseLoggable {
    protected String entityName;
    protected double damageAmount;
    protected String damageSource;
    protected boolean isValid;

    public DamageLoggable(EntityDamageEvent event) {
        this.entityName = event.getEntity().getName();
        this.damageAmount = event.getFinalDamage();
        this.isValid = event.getCause() != DamageCause.ENTITY_ATTACK
                && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK
                && event.getEntityType() == EntityType.PLAYER;
        this.damageSource = event.getCause().name().toLowerCase(Locale.ROOT);
        if (event.getCause() == DamageCause.PROJECTILE) {
            Entity direct = event.getDamageSource().getDirectEntity();
            if (direct instanceof Projectile projectile && projectile.getOwnerUniqueId() != null) {
                Entity owner = GPTGOD.SERVER.getEntity(projectile.getOwnerUniqueId());
                if (owner != null) {
                    damageSource = "a projectile from " + owner.getName();
                }
            }
        }
    }

    @Override
    public String getLog() {
        if (!isValid || damageAmount < 0.5) {
            return null;
        }
        return entityName + " took " + Math.round(damageAmount) + " damage from " + damageSource;
    }

    @Override
    public boolean combine(Loggable other) {
        if (!(other instanceof DamageLoggable loggable) || !isValid || !loggable.isValid) {
            return false;
        }
        if (loggable.entityName.equals(this.entityName) && loggable.damageSource.equals(this.damageSource)) {
            this.damageAmount += loggable.damageAmount;
            return true;
        }
        return false;
    }
}
