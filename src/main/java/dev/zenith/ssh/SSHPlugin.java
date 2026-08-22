package dev.zenith.ssh;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import dev.zenith.ssh.command.SSHCommand;
import dev.zenith.ssh.module.SSHServerModule;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

@Plugin(
    id = BuildConstants.PLUGIN_ID,
    version = BuildConstants.VERSION,
    description = "ZenithProxy SSH Server",
    url = "https://github.com/rfresh2/ZenithProxySSHServer",
    authors = {"rfresh2"},
    mcVersions = {"*"}
)
public class SSHPlugin implements ZenithProxyPlugin {
    public static ComponentLogger LOG;
    public static SSHConfig PLUGIN_CONFIG;

    @Override
    public void onLoad(final PluginAPI pluginAPI) {
        LOG = pluginAPI.getLogger();
        LOG.info("SSH Server Plugin loading...");
        PLUGIN_CONFIG = pluginAPI.registerConfig(BuildConstants.PLUGIN_ID, SSHConfig.class);
        pluginAPI.registerCommand(new SSHCommand());
        pluginAPI.registerModule(new SSHServerModule());
    }
}
