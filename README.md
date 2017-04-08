# WebSandboxMC

A web-based interface providing an interactive 3D preview or glimpse of your Glowstone server, for use with [NetCraft](https://github.com/satoshinm/NetCraft)

## Features
Currently supports:

* Exposes a section of your server to web users (location and dimensions configurable)
* Web users can place/break blocks, and see server block changes in realtime
* Web users can send/receive chat, and other users can see their chat messages
* TODO: missing features

## Compilation
* Install [Maven 3](http://maven.apache.org/download.html)
* Check out this repo and: `mvn clean install`

## Usage
* Copy target/WebSandboxMC-0.1.jar to the `plugins` folder of your [Glowstone](https://www.glowstone.net) server (note: may/not work with other Bukkit-compatible servers, TODO: test)
* Visit http://localhost:4081/index.html
