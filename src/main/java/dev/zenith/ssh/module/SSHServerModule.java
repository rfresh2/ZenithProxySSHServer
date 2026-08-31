package dev.zenith.ssh.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandOutputHelper;
import com.zenith.command.api.CommandSources;
import com.zenith.event.console.ConsoleLogEvent;
import com.zenith.module.api.Module;
import com.zenith.terminal.TerminalAutoCompletionWidget;
import com.zenith.terminal.TerminalCommandCompleter;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.jline.builtins.ssh.ShellFactoryImpl;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.zenith.Globals.*;
import static dev.zenith.ssh.SSHPlugin.PLUGIN_CONFIG;

public class SSHServerModule extends Module {
    private SshServer sshServer;
    private final Set<SSHClientSession> activeSessions = ConcurrentHashMap.newKeySet();

    record SSHClientSession(ServerSession connection, LineReader lineReader) {
        void close() {
            try {
                lineReader.getTerminal().close();
            } catch (Exception e) {
                TERMINAL_LOG.error("Error while closing client SSH terminal: {}", e.getMessage());
            }
            connection.close(false);
        }
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            EventConsumer.of(ConsoleLogEvent.class, this::onConsoleLog)
        );
    }

    private void onConsoleLog(ConsoleLogEvent event) {
        for (var session : activeSessions) {
            try {
                session.lineReader().printAbove(event.ansi());
            } catch (Exception e) {
                error("Error sending console log to session: {}", session.connection().getRemoteAddress(), e);
            }
        }
    }

    @Override
    public synchronized void onEnable() {
        activeSessions.clear();
        startSshServer();
    }

    @Override
    public synchronized void onDisable() {
        stopSshServer();
        activeSessions.clear();
    }

    public synchronized void startSshServer() {
        if (sshServer != null) return;
        try {
            sshServer = initializeSSHTerminal();
        } catch (Exception e) {
            error("Error initializing SSH server", e);
        }
    }

    public synchronized void stopSshServer() {
        if (sshServer == null) return;
        for (var session : activeSessions) {
            try {
                session.close();
            } catch (Exception e) {
                error("Error closing SSH client session: {}", e);
            }
        }
        try {
            sshServer.close(false);
        } catch (Exception e) {
            error("Error stopping SSH server", e);
        }
        sshServer = null;
    }

    private SshServer initializeSSHTerminal() throws Exception {
        var server = SshServer.setUpDefaultServer();
        server.setPort(PLUGIN_CONFIG.port);
        server.setHost(PLUGIN_CONFIG.bindAddress);
        server.setPasswordAuthenticator((username, password, session) -> {
            if (!PLUGIN_CONFIG.passwordAuthEnabled) return false;
            return PLUGIN_CONFIG.password.equals(password);
        });
        var keyProvider = new SimpleGeneratorHostKeyProvider();
        keyProvider.setPath(Path.of("ssh_host_key"));
        keyProvider.setAlgorithm("RSA");
        keyProvider.setOverwriteAllowed(true);
        server.setKeyPairProvider(keyProvider);
        server.setShellFactory(new ShellFactoryImpl((params) -> {
            try {
                info("Added SSH Terminal client: {}", params.getSession().getRemoteAddress());
                var clientTerminal = params.getTerminal();
                var reader = LineReaderBuilder.builder()
                    .terminal(clientTerminal)
                    .appName("ZenithProxy")
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.CASE_INSENSITIVE, true)
                    .option(LineReader.Option.INSERT_TAB, false)
                    .option(LineReader.Option.EMPTY_WORD_OPTIONS, false)
                    .completer(new TerminalCommandCompleter())
                    .build();
                new TerminalAutoCompletionWidget(reader);
                var session = new SSHClientSession(params.getSession(), reader);
                activeSessions.add(session);
                params.getSession().addCloseFutureListener((future) -> {
                    info("Removed SSH Terminal client: {}", params.getSession().getRemoteAddress());
                    activeSessions.remove(session);
                });
                var terminalThread = new Thread(() -> readSshTerminal(session), "SSH Client Session - %s".formatted(params.getSession().getRemoteAddress()));
                terminalThread.setDaemon(true);
                clientTerminal.handle(Terminal.Signal.INT, signal -> {
                    session.close();
                });
                terminalThread.start();
            } catch (Exception e) {
                TERMINAL_LOG.error("Error while initializing SSH terminal", e);
            }
        }));
        server.start();
        info("SSH server started: {}:{} with password: {}", server.getHost(), server.getPort(), PLUGIN_CONFIG.password);
        return server;
    }

    private void readSshTerminal(SSHClientSession session) {
        int eofCount = 0;
        while (true) {
            try {
                String line = session.lineReader().readLine("> ");
                if (line == null || line.isBlank()) {
                    continue;
                }
                var lc = line.toLowerCase().trim();
                if (lc.equals("exit")) {
                    session.close();
                    return;
                }
                executeTerminalCommand(line);
                eofCount = 0;
            } catch (final EndOfFileException e) {
                if (eofCount++ > 20) {
                    warn("Detected misconfigured SSH terminal input, disconnecting");
                    session.close();
                    return;
                }
            } catch (final UserInterruptException | IllegalStateException e) {
                session.close();
                return;
            } catch (final Exception e) {
                error("Error while reading terminal input", e);
                session.close();
                return;
            }
        }
    }

    private void executeTerminalCommand(final String command) {
        final var commandContext = CommandContext.create(command, CommandSources.TERMINAL);
        COMMAND.execute(commandContext);
        if (CONFIG.interactiveTerminal.logToDiscord && !commandContext.isSensitiveInput()) CommandOutputHelper.logInputToDiscord(command, CommandSources.TERMINAL, commandContext);
        var embed = commandContext.getEmbed();
        if (CONFIG.interactiveTerminal.logToDiscord && DISCORD.isRunning() && !commandContext.isSensitiveInput()) {
            CommandOutputHelper.logEmbedOutputToDiscord(embed);
            CommandOutputHelper.logMultiLineOutputToDiscord(commandContext.getMultiLineOutput());
        } else {
            CommandOutputHelper.logEmbedOutputToTerminal(embed);
            CommandOutputHelper.logMultiLineOutputToTerminal(commandContext.getMultiLineOutput());
        }
    }
}
