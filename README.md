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

### mc
Configures what part of your world to expose:

* `x_center` (-85): specifies the center of the world from the web client's perspective, X coordinate
* `y_center` (78): " ", Y coordinate
* `z_center` (93): " ", Z coordinate
* `radius` (16): range out of the center to expose in each direction (cube), setting too high will slow down web client loading
* `entity` ("Sheep"): name of entity class to spawn on server for web users, set to "" to disable
* `entity_custom_names` (true): add web player names to the spawned entity's nametag if true
* `entity_disable_gravity` (true): disable gravity for the spawned entities if true
* `entity_disable_ai` (true): disable AI for the spawned living entities if true, otherwise they may move on their own
* `entity_move_sandbox` (true): constrain the web player entity's movement to within the sandbox, otherwise they can go anywhere
* `entity_die_disconnect` (false): disconnect the web player when their entity dies, otherwise they remain connected invisibly
* `debug` (false): if true, enables vast amounts of additional logging with FINEST log level

### nc
Configures the NetCraft web client:

* `y_offset` (20): height to shift the web client blocks upwards, to distinguish from the pre-generated landscape
* `allow_break_place_blocks` (true): allow web users to break/place blocks, set to false for view-only (see also `allow_signs`)
* `allow_signs` (true): allow web users to place signs (by typing backquote followed by the text)
* `allow_chatting` (true): allow web users to send chat messages to the server
* `see_chat` (true): allow web users to receive chat messages from the server
* `see_players` (true): allow web users to see other player positions
* `blocks_to_web`: map of Bukkit material names to NetCraft web client IDs -- you can add additional block types here if they don't show up correctly
 * The special material name "missing" is used for unmapped blocks, interesting values:
  * 16 (default): clouds, a solid white block you can walk through, useful as a placeholder so you know to edit spawn and remove or translate it
  * 0: air, for if you want missing/unknown/unsupported blocks to be invisible to the web client
* `warn_missing_blocks_to_web` (true): log the type and location of untranslated blocks so you can fix them, set to false (and `blocks_to_web` "missing" to 0) if you don't care

Newer versions of NetCraft can be installed without upgrading the plugin, or the main page customized,
by placing the saving files in the plugin's data directory: craft.html (main page), craft.js, craft.html.mem.
If not given, WebSandboxMC's embedded version of NetCraft will be served up instead.

## Compatibility

WebSandboxMC uses the [Bukkit API from Spigot](https://hub.spigotmc.org/javadocs/bukkit/) with the aim of maximizing
server compatibility. Known compatible server software:

* [Glowstone](https://www.glowstone.net): for a fully open source end-to-end gameplay experience
* [SpigotMC](https://www.spigotmc.org)

## License

MIT
