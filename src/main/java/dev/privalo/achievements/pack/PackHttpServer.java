package dev.privalo.achievements.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.privalo.achievements.RadiantAchievements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Tiny built-in HTTP server that serves the generated resourcepack.zip, so the pack works on localhost and on
 * real servers without any external hosting. URL: {@code http://<address>:<port>/radiantachievements/<sha1>.zip}.
 */
public final class PackHttpServer {

    public static final String CONTEXT = "/radiantachievements/";

    private final RadiantAchievements plugin;
    private HttpServer server;
    private ExecutorService executor;
    private int boundPort;

    public PackHttpServer(RadiantAchievements plugin) {
        this.plugin = plugin;
    }

    /** Starts (or restarts) the server. @return true when listening. */
    public synchronized boolean start(String bind, int port) {
        stop();
        try {
            InetSocketAddress address = bind == null || bind.isBlank() || bind.equals("0.0.0.0")
                    ? new InetSocketAddress(port) : new InetSocketAddress(bind, port);
            server = HttpServer.create(address, 16);
            server.createContext(CONTEXT, this::handle);
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "RadiantAchievements-PackHTTP");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            boundPort = server.getAddress().getPort();
            plugin.getLogger().info("Resource pack HTTP server listening on port " + boundPort
                    + " (open this TCP port in your firewall/host panel for remote players).");
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not start the resource pack HTTP server on port " + port
                    + ". Change resource-pack.http.port or set resource-pack.host to an external URL.", ex);
            server = null;
            return false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized boolean running() {
        return server != null;
    }

    public int port() {
        return boundPort;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] zip = plugin.packGenerator().lastZip();
            String hash = plugin.packGenerator().lastHash();
            String path = exchange.getRequestURI().getPath();
            boolean known = path.equals(CONTEXT + "pack.zip") || path.equals(CONTEXT + hash + ".zip");
            if (zip.length == 0 || !known) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(zip);
            }
        }
    }
}
