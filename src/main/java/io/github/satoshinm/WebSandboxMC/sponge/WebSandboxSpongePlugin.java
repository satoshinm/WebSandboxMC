package io.github.satoshinm.WebSandboxMC.sponge;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;

@Plugin(id = "websandboxmc", name = "WebSandboxMC", version = "1.6.0")
public class WebSandboxSpongePlugin {

    @Inject
    private Logger logger;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        logger.info("WebSandboxMC/Sponge starting");
    }
}
