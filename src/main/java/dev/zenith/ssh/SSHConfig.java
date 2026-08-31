package dev.zenith.ssh;

import java.util.UUID;

public class SSHConfig {
    public boolean enabled = true;
    public int port = 8022;
    public String bindAddress = "127.0.0.1";
    public String password = UUID.randomUUID().toString();
}
