package com.mes.m14a.server;

import com.mes.m14a.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

/**
 * Launches the FitMesWpfServer WPF window and waits for the HttpListener inside it
 * to start accepting TCP connections on the configured port.
 *
 * The WPF app is NOT headless: an operator must type the Port and Password into the
 * window and click "Start Server". This manager opens the window, brings it to the
 * foreground, polls the port, and prints a reminder every 10 seconds so the test
 * console is loud enough to notice.
 */
public final class ServerManager {

    private static final Logger log = LogManager.getLogger(ServerManager.class);
    private static Process serverProcess;

    private ServerManager() {}

    public static synchronized void start() {
        String host    = ConfigReader.get("server.host", "localhost");
        int    port    = Integer.parseInt(ConfigReader.get("server.port", "5000"));
        int    timeout = Integer.parseInt(ConfigReader.get("server.startupTimeoutSec", "120"));

        // Direct-testing path: run an in-process stub instead of the WPF server.
        if (Boolean.parseBoolean(ConfigReader.get("server.useStub", "false"))) {
            log.info("server.useStub=true - starting in-process StubEapServer (no FitMesWpfServer needed)");
            StubEapServer.start(host, port);
            Runtime.getRuntime().addShutdownHook(
                    new Thread(StubEapServer::stop, "stub-eap-shutdown"));
            return;
        }

        if (isPortOpen(host, port)) {
            log.info("FitMesWpfServer already listening on {}:{} - reusing", host, port);
            return;
        }

        boolean autoLaunch = Boolean.parseBoolean(ConfigReader.get("server.autoStart", "true"));
        if (autoLaunch) {
            launch();
            bringWindowToFront();
        } else {
            log.info("server.autoStart=false - please start FitMesWpfServer.exe manually");
        }

        printActionRequiredBanner(host, port);
        if (!waitForPort(host, port, Duration.ofSeconds(timeout))) {
            stop();
            throw new IllegalStateException(
                    "FitMesWpfServer did not start listening on " + host + ":" + port +
                    " within " + timeout + "s. Did you click 'Start Server' in the WPF UI " +
                    "(and set Port=" + port + ")?");
        }
        log.info("FitMesWpfServer is accepting connections on {}:{}", host, port);

        Runtime.getRuntime().addShutdownHook(
                new Thread(ServerManager::stop, "fitmes-server-shutdown"));
    }

    public static synchronized void stop() {
        StubEapServer.stop();
        if (serverProcess == null) {
            return;
        }
        log.info("Stopping FitMesWpfServer (pid={})", serverProcess.pid());
        try {
            serverProcess.destroy();
            if (!serverProcess.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("FitMesWpfServer did not exit gracefully - forcing");
                serverProcess.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            serverProcess.destroyForcibly();
        } finally {
            serverProcess = null;
        }
    }

    // ---------- helpers ----------

    private static void launch() {
        String exe = ConfigReader.get("server.exePath");
        String wd  = ConfigReader.get("server.workingDir");
        File exeFile = new File(exe);
        if (!exeFile.isFile()) {
            throw new IllegalStateException("server.exePath does not exist: " + exe);
        }
        ProcessBuilder pb = new ProcessBuilder(exe).directory(new File(wd));
        try {
            log.info("Launching FitMesWpfServer window: {}", exe);
            serverProcess = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch FitMesWpfServer", e);
        }
    }

    /**
     * Try to bring the FitMesWpfServer window to the foreground so the operator
     * isn't looking at Eclipse wondering why nothing happened. Best-effort only;
     * failures are logged and ignored.
     */
    private static void bringWindowToFront() {
        try {
            Thread.sleep(1500);
            new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Add-Type -AssemblyName Microsoft.VisualBasic; " +
                    "Start-Sleep -Milliseconds 500; " +
                    "try { [Microsoft.VisualBasic.Interaction]::AppActivate('FitMesWpfServer') } catch {}")
                    .inheritIO().start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Could not bring server window to front: {}", e.getMessage());
        }
    }

    private static void printActionRequiredBanner(String host, int port) {
        log.info("");
        log.info("============================================================");
        log.info("  WAITING FOR FitMesWpfServer to start on {}:{}", host, port);
        log.info("  ACTION REQUIRED in the WPF window:");
        log.info("    1. Find the 'FitMesWpfServer' window (Alt+Tab if hidden)");
        log.info("    2. Set Port    = {}", port);
        log.info("    3. Set Password = whatever you configured (config: server.* / creds.*)");
        log.info("    4. Click 'Start Server'");
        log.info("============================================================");
        log.info("");
    }

    private static boolean waitForPort(String host, int port, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Instant lastReminder = Instant.now();
        while (Instant.now().isBefore(deadline)) {
            if (isPortOpen(host, port)) return true;

            if (Duration.between(lastReminder, Instant.now()).getSeconds() >= 10) {
                long remaining = Duration.between(Instant.now(), deadline).getSeconds();
                log.info("Still waiting for {}:{}  (~{}s left) " +
                        "- click 'Start Server' in the FitMesWpfServer window",
                        host, port, remaining);
                lastReminder = Instant.now();
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
