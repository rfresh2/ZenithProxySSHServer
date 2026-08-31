package dev.zenith.ssh.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import dev.zenith.ssh.module.SSHServerModule;

import java.net.InetAddress;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static dev.zenith.ssh.SSHPlugin.PLUGIN_CONFIG;

public class SSHCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("ssh")
            .category(CommandCategory.MANAGE)
            .description("""
                Configures the SSH server.
                
                **Usage:**
                
                in a terminal: `ssh ssh://<username>@<host>:<port>`
                
                * `<username>` -> can be anything, or the host's OS user for public key authentication
                * `<host>` -> IP address of the server. If connecting from the same PC, use `127.0.0.1` or `localhost`
                * `<port>` -> ssh server port
                
                Full example: `ssh ssh://z@127.0.0.1:8022`
                """)
            .usageLines(
                "on/off",
                "port <port>",
                "bind <local/public>",
                "bind <address>",
                "password on/off",
                "password set <password>",
                "password rateLimiter on/off",
                "password rateLimiter requestsPerMinute <requestCount>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("ssh").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                PLUGIN_CONFIG.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Server " + toggleStrCaps(PLUGIN_CONFIG.enabled));
                MODULE.get(SSHServerModule.class).disable();
                MODULE.get(SSHServerModule.class).syncEnabledFromConfig();
            }))
            .then(literal("port").then(argument("port", integer(1, 65535)).executes(c -> {
                PLUGIN_CONFIG.port = getInteger(c, "toggle");
                c.getSource().getEmbed()
                    .title("Port Set");
                restartSshServer();
            })))
            .then(literal("bind")
                .then(argument("type", enumStrings("local", "public")).executes(c -> {
                    var type = getString(c, "type");
                    if (type.equalsIgnoreCase("local")) {
                        PLUGIN_CONFIG.bindAddress = "127.0.0.1";
                    } else {
                        PLUGIN_CONFIG.bindAddress = "0.0.0.0";
                    }
                    c.getSource().getEmbed()
                        .title("Bind Address Set");
                    restartSshServer();
                }))
                .then(argument("addr", wordWithChars()).executes(c -> {
                    var addr = getString(c, "addr");
                    try {
                        InetAddress.getByName(addr);
                    } catch (Exception e) {
                        c.getSource().getEmbed()
                            .title("Invalid Address");
                        return ERROR;
                    }
                    PLUGIN_CONFIG.bindAddress = addr;
                    c.getSource().getEmbed()
                        .title("Bind Address Set");
                    restartSshServer();
                    return OK;
                })))
            .then(literal("password")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.passwordAuthEnabled = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("Password Auth " + toggleStrCaps(PLUGIN_CONFIG.passwordAuthEnabled));
                }))
                .then(literal("set").then(argument("password", wordWithChars()).executes(c -> {
                    PLUGIN_CONFIG.password = getString(c, "password");
                    c.getSource().getEmbed()
                        .title("Password Set");
                    c.getSource().setSensitiveInput(true);
                })))
                .then(literal("rateLimiter")
                    .then(argument("toggle", toggle()).executes(c -> {
                        PLUGIN_CONFIG.rateLimiter = getToggle(c, "toggle");
                        c.getSource().getEmbed()
                            .title("Rate Limiter " + toggleStrCaps(PLUGIN_CONFIG.rateLimiter));
                    }))
                    .then(literal("requestsPerMinute").then(argument("requestsPerMinute", integer(1, 10000)).executes(c -> {
                        PLUGIN_CONFIG.rateLimitLoginsPerMinute = getInteger(c, "requestsPerMinute");
                        c.getSource().getEmbed()
                            .title("Rate Limiter Requests Per Minute Set");
                    })))
                )
            );
    }

    @Override
    public void defaultHandler(CommandContext ctx) {
        ctx.getEmbed()
            .addField("SSH Server", toggleStr(PLUGIN_CONFIG.enabled))
            .addField("Port", PLUGIN_CONFIG.port)
            .addField("Bind Address", PLUGIN_CONFIG.bindAddress + (PLUGIN_CONFIG.bindAddress.equals("0.0.0.0")
                ? " (public)"
                : PLUGIN_CONFIG.bindAddress.equals("127.0.0.1")
                    ? " (local)"
                    : ""
            ))
            .addField("Password Auth", toggleStr(PLUGIN_CONFIG.passwordAuthEnabled))
            .primaryColor();
        if (PLUGIN_CONFIG.passwordAuthEnabled) {
            ctx.getEmbed()
                .addField("Password", PLUGIN_CONFIG.password)
                .addField("Password Rate Limiter", toggleStr(PLUGIN_CONFIG.rateLimiter))
                .addField("Password Rate Limiter Requests Per Minute", PLUGIN_CONFIG.rateLimitLoginsPerMinute);
        }
    }

    private void restartSshServer() {
        MODULE.get(SSHServerModule.class).disable();
        MODULE.get(SSHServerModule.class).enable();
    }
}
