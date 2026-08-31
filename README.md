# ZenithProxy SSH Server

Runs an SSH server, allowing you to connect a terminal to a ZenithProxy instance.

**Requires ZenithProxy >=3.5.9**

Commands and logs appear exactly as if you ran ZenithProxy from a terminal.

This plugin can be useful if the ZenithProxy app is running headlessly.

For example, if it is run by a systemd service, docker container, etc


## Commands

* `ssh on/off` - Default: ON
* `ssh port <port>` - Default: 8022
  * if you have multiple ZenithProxy instances with this plugin, you will have to assign each a unique port
* `ssh bind <local/public>` - Default: public
  * local = no-one on the public internet can connect
* `ssh bind <address>` - advanced version of above bind
* `ssh password on/off` - toggles if password authentication is enabled
* `ssh password set <password>` - Default: auto-generated, see log or command output


## Usage

in a terminal: `ssh ssh://<username>@<host>:<port>`

* `<username>` -> can be anything, or the host's OS user to be able to use public key authentication
* `<host>` -> IP address of the server. If connecting from the same PC, use `localhost`
* `<port>` -> ssh server port

Full example: `ssh ssh://z@127.0.0.1:8022`

### Password Authentication

It will then prompt you to enter the password. Type it in and press enter. 

SSH will not reveal the password text as you type for security reasons

### Public Key Authentication

The host's SSH `authorized_keys` file must contain the public key of the client connecting.

on linux this would be `~/.ssh/authorized_keys`

If a matching key is found, it will log you in immediately. Otherwise, it will fall back to password authentication
