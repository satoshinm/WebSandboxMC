package io.github.satoshinm.WebSandboxMC.bukkit;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;

public class ClickableLinks {
    public static void sendLink(Player player, String url, boolean clickableLinksTellraw) {
        String linkText = "Click here to login";
        String hoverText = "Login to the web sandbox as " + player.getName();

        // There are two strategies since TextComponents fails with on Glowstone with an error:
        // java.lang.UnsupportedOperationException: Not supported yet.
        // at org.bukkit.entity.Player$Spigot.sendMessage(Player.java:1734)
        // see https://github.com/GlowstoneMC/Glowkit-Legacy/pull/8
        if (clickableLinksTellraw) {
            JSONObject json = new JSONObject();
            json.put("text", linkText);
            json.put("bold", true);

            JSONObject clickEventJson = new JSONObject();
            clickEventJson.put("action", "open_url");
            clickEventJson.put("value", url);
            json.put("clickEvent", clickEventJson);

            JSONObject hoverEventJson = new JSONObject();
            hoverEventJson.put("action", "show_text");
            JSONObject hoverTextObject = new JSONObject();
            hoverTextObject.put("text", hoverText);
            hoverEventJson.put("value", hoverTextObject);
            json.put("hoverEvent", hoverEventJson);

            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json.toJSONString());
        } else {
            TextComponent message = new TextComponent(linkText);
            message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] { new TextComponent(hoverText) }));
            message.setBold(true);

            player.spigot().sendMessage(message);
        }
    }
}
