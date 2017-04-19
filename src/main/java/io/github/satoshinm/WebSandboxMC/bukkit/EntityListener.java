package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.bridge.PlayersBridge;
import io.github.satoshinm.WebSandboxMC.bridge.WebPlayerBridge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class EntityListener implements Listener {

    private final WebPlayerBridge webPlayerBridge;

    public EntityListener(WebPlayerBridge webPlayerBridge) {
        this.webPlayerBridge = webPlayerBridge;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        String username = webPlayerBridge.entityId2Username.get(entity.getEntityId());
        if (username == null) {
            return;
        }

        EntityDamageEvent entityDamageEvent = entity.getLastDamageCause();
        // TODO: how to get killer?
        EntityDamageEvent.DamageCause damageCause = entityDamageEvent != null ? entityDamageEvent.getCause() : null;

        webPlayerBridge.notifyDied(username, damageCause);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Entity entity = event.getEntity();

        String username = webPlayerBridge.entityId2Username.get(entity.getEntityId());
        if (username == null) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getDisplayName();

        webPlayerBridge.notifySheared(username, playerName);
    }
}
