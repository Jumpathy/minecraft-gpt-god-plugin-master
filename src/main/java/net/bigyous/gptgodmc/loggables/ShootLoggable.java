package net.bigyous.gptgodmc.loggables;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityShootBowEvent;


public class ShootLoggable extends BaseLoggable {
    private String shooter;
    private String target;
    private boolean isValid;
    private String projectile;
    @SuppressWarnings("null")
    public ShootLoggable(EntityShootBowEvent event){
        
        isValid = event.getEntityType().equals(EntityType.PLAYER);
        if(isValid){
            LivingEntity ent = event.getEntity();
            shooter = ent.getName();
            this.projectile = event.getProjectile().getName();
            Entity targetEnt = ent.getTargetEntity(24);
            isValid = targetEnt != null;
            this.target = isValid? targetEnt.getName() : null;
        }

    }

    @Override
    public String getLog() {
        if(!isValid) return null;
        return String.format("%s launched a(n) %s at %s", shooter, projectile, target);
    }
}
