# WebSandboxMC

Bukkit plugin providing a web-based interface with an interactive WebGL 3D preview or glimpse of your server

![Screenshot](screenshot.png)

**Downloads: [WebSandboxMC at Spigot Resources](https://www.spigotmc.org/resources/websandboxmc.39415/)**, or [GitHub releases](https://github.com/satoshinm/WebSandboxMC/releases/)

[![CircleCI](https://circleci.com/gh/satoshinm/WebSandboxMC.svg?style=svg)](https://circleci.com/gh/satoshinm/WebSandboxMC)

## Features
Currently supports:

* Exposes a small piece of your server to web users, with configurable location and dimensions
* Web users can place/break blocks, and see server block changes in realtime
* Web users can send/receive chat, and other users can see their chat messages
* Web users can see players on your server and other web users move and rotate
* Sheep are spawned with custom names and track the web users movements

TODO: missing features

## Compilation
* Install [Maven 3](http://maven.apache.org/download.html)
* Check out this repository
* Check out and build [NetCraft](https://github.com/satoshinm/NetCraft) using emscripten, when it completes copy the build output into resources:
* `cp ../NetCraft/release-build-js/craft.* src/main/resources/`
* Build the WebSandboxMC plugin: `mvn package`

## Usage
* Copy target/WebSandboxMC.jar to the `plugins` folder of your [Glowstone](https://www.glowstone.net) server (note: may/not work with other Bukkit-compatible servers, TODO: test)
* Visit http://localhost:4081/ in a modern browser (requires WebGL, Pointer Lock, WebSockets)
* Play the game

## Configuration

After an initial run, `plugins/WebSandboxMC/config.yml` should be populated with configuration defaults.
The settings are as follows:

### http
Configures the HTTP and WebSocket server:

* `port` (4081): TCP port for the HTTP server to listen on
* `takeover` (false): advanced experimental option to reuse the server port from Bukkit (ignoring `port`) before startup, allowing this plugin to be used on hosts where only one port is allowed
* `unbind_method` ('console.getServerConnection.b'): if `takeover` enabled, this method is called on `Bukkit.getServer()`, may need to change depending on your Bukkit server implementation

### mc
Configures what part of your world to expose:

* `debug` (false): if true, enables vast amounts of additional logging with FINEST log level
* `use_permissions` (false): if false, `/websandbox` command requires op; if true, checks for `websandbox.command.`+subcommand permission node
* `world` (""): name of world for web clients to spawn in, or an empty string to use the first available
* `x_center` (0): specifies the center of the world from the web client's perspective, X coordinate
* `y_center` (75): " ", Y coordinate
* `z_center` (0): " ", Z coordinate
 * If x/y/z center are all 0, then the world's spawn location is used instead
* `radius` (16): range out of the center to expose in each direction (cube), setting too high will slow down web client loading
* `clickable_links` (true): send clickable links in chat commands from `/websandbox auth` if true, or as plain text if false
* `clickable_links_tellraw` (false): use the `/tellraw` command to send richly formatted messages if true, or use the TextComponents API if false, change this if you get a formatting error with `/websandbox auth`
* `entity` ("Sheep"): name of entity class to spawn on server for web users, set to "" to disable
* `entity_custom_names` (true): add web player names to the spawned entity's nametag if true
* `entity_disable_gravity` (true): disable gravity for the spawned entities if true
* `entity_disable_ai` (true): disable AI for the spawned living entities if true, otherwise they may move on their own
* `entity_move_sandbox` (true): constrain the web player entity's movement to within the sandbox, otherwise they can go anywhere
* `entity_die_disconnect` (false): disconnect the web player when their entity dies, otherwise they remain connected invisibly

### nc
Configures the NetCraft web client:

* `y_offset` (20): height to shift the web client blocks upwards, to distinguish from the pre-generated landscape
* `allow_break_place_blocks` (true): allow web users to break/place blocks, set to false for view-only (see also `allow_signs`)
* `unbreakable_blocks` (`BEDROCK`): list of block types to deny the client from breaking or placing
* `allow_signs` (true): allow web users to place signs (by typing backquote followed by the text)
* `allow_chatting` (true): allow web users to send chat messages to the server
* `see_chat` (true): allow web users to receive chat messages from the server
* `see_players` (true): allow web users to see other player positions
* `blocks_to_web_override`: map of Bukkit material names to NetCraft web client IDs -- you can add additional block types here if they don't show up correctly
 * This overrides the built-in map, and by default is empty (`blocks_to_web` pre-1.4.2 is no longer used).
 * The special material name "missing" is used for unmapped blocks, interesting values:
  * 16 (default): clouds, a solid white block you can walk through, useful as a placeholder so you know to edit spawn and remove or translate it
  * 0: air, for if you want missing/unknown/unsupported blocks to be invisible to the web client
* `warn_missing_blocks_to_web` (true): log the type and location of untranslated blocks so you can fix them, set to false (and `blocks_to_web_override` "missing" to 0) if you don't care

Newer versions of NetCraft can be installed without upgrading the plugin, or the main page customized,
by placing the saving files in the plugin's data directory: craft.html (main page), craft.js, craft.html.mem.
If not given, WebSandboxMC's embedded version of NetCraft will be served up instead.

If the plugin folder contains a file named textures.zip, then it will be sent as a custom texture pack
to the client. This archive must contain a `terrain.png` with a texture atlas. For details on
texture pack compatibility, see [NetCraft#textures](https://github.com/satoshinm/NetCraft#textures).

## Commands

* `/websandbox`: show help
* `/websandbox list [verbose]`: list all web users connected
* `/websandbox tp [<user>]`: teleport to given web username, or web spawn location
* `/websandbox kick <user>`: disconnect given web username
* `/websandbox auth [<user>]`: generates an authentication token to allow the player to authenticate over the web as themselves instead of anonymously

## Compatibility

WebSandboxMC uses the [Bukkit API from Spigot](https://hub.spigotmc.org/javadocs/bukkit/) with the aim of maximizing
server compatibility. Known compatible server software:

* [Glowstone](https://www.glowstone.net): for a fully open source end-to-end gameplay experience
* [SpigotMC](https://www.spigotmc.org)

## License

MIT
