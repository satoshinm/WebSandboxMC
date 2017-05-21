package io.github.satoshinm.WebSandboxMC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Settings {
    public int httpPort = 4081;
    public boolean takeover = false;
    public String unbindMethod = "console.getServerConnection.b";

    public boolean debug = false;
    public boolean usePermissions = false;
    public String entityClassName = "Sheep";
    public boolean setCustomNames = true;
    public boolean disableGravity = true;
    public boolean disableAI = true;
    public boolean entityMoveSandbox = true;
    public boolean entityDieDisconnect = false;

    // Send blocks around this area in the Bukkit world
    public String world = "";
    public int x_center = 0;
    public int y_center = 75;
    public int z_center = 0;

    // of this radius, +/-
    public int radius = 16;

    // raised this amount in the web world, so it is clearly distinguished from the client-generated terrain
    public int y_offset = 20;

    public boolean allowBreakPlaceBlocks = true;
    public List<String> unbreakableBlocks = new ArrayList<String>();
    public boolean allowSigns = true;
    public boolean allowChatting = true;
    public boolean seeChat = true;
    public boolean seePlayers = true;

    public Map<String, Object> blocksToWebOverride = new HashMap<String, Object>();
    public boolean warnMissing = true;
    public String textureURL = null;
}
