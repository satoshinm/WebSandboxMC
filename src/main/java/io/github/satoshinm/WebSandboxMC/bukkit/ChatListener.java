package io.github.satoshinm.WebSandboxMC.bukkit;

import io.github.satoshinm.WebSandboxMC.ws.WebSocketServerThread;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final WebSocketServerThread webSocketServerThread;

    public ChatListener(WebSocketServerThread webSocketServerThread) {
        this.webSocketServerThread = webSocketServerThread;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String formattedMessage = event.getFormat().format(event.getMessage());
        formattedMessage = "<" + event.getPlayer().getDisplayName() + "> " + formattedMessage; // TODO: why isn't getFormat() take care of this?

        // TODO: refactor logic out of thread into a ChatBridge
        webSocketServerThread.notifyChat(formattedMessage);
    }
}
