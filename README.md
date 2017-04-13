# WebSandboxMC

Bukkit plugin providing a web-based interface with an interactive WebGL 3D preview or glimpse of your server

![Screenshot](screenshot.png)

Downloads: https://github.com/satoshinm/WebSandboxMC/releases/

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
* Visit http://localhost:4081/index.html in a modern browser (requires WebGL, Pointer Lock, WebSockets)
* Play the game

