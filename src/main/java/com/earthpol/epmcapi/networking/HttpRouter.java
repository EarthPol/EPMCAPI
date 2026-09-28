package com.earthpol.epmcapi.networking;

import com.earthpol.epmcapi.exception.HttpResponseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/** Transport only: route callbacks own validation and data access. */
public class HttpRouter implements HttpHandler {
    public record Request(String body) {}

    public record Settings(int maxBodyBytes, long requestTimeoutMillis, boolean cors,
                           List<String> origins, boolean credentials, boolean compression, int compressionMinBytes) {
        public Settings {
            if (maxBodyBytes < 1 || maxBodyBytes > 16_777_216 || requestTimeoutMillis < 1 || compressionMinBytes < 0)
                throw new IllegalArgumentException("Invalid HTTP limits");
            origins = List.copyOf(origins);
            if (credentials && origins.contains("*"))
                throw new IllegalArgumentException("Credentialed CORS requires explicit allowed origins");
        }
    }

    private final Map<String, Map<String, Function<Request, String>>> routes = new LinkedHashMap<>();
    private final Settings settings;
    private final ScheduledExecutorService deadlines;
    private final Logger logger;

    public HttpRouter(Settings settings, ScheduledExecutorService deadlines, Logger logger) {
        this.settings = settings;
        this.deadlines = deadlines;
        this.logger = logger;
    }

    protected final void register(String path, String method, Function<Request, String> action) {
        routes.computeIfAbsent(path, ignored -> new LinkedHashMap<>()).put(method, action);
    }

    public final Set<String> paths() {
        return Set.copyOf(routes.keySet());
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        var deadline = deadlines.schedule(exchange::close, settings.requestTimeoutMillis(), TimeUnit.MILLISECONDS);
        try (exchange) {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().set("X-Request-ID", requestId);
            addCorsHeaders(exchange);
            String path = exchange.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            Map<String, Function<Request, String>> methods = routes.get(path);
            int status = 200;
            String response;
            try {
                if (methods == null) throw new HttpResponseException(404, "Endpoint not found");
                String allowed = String.join(", ", methods.keySet()) + ", OPTIONS";
                exchange.getResponseHeaders().set("Allow", allowed);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", allowed);
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                var action = methods.get(exchange.getRequestMethod());
                if (action == null) throw new HttpResponseException(405, "Method not allowed");
                String rawQuery = exchange.getRequestURI().getRawQuery();
                if (rawQuery != null && !rawQuery.isEmpty())
                    throw new HttpResponseException(400, "URL query parameters are not supported; use POST with a JSON body");
                String body = "";
                if ("POST".equals(exchange.getRequestMethod())) {
                    String type = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (type != null && !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                        throw new HttpResponseException(415, "Content-Type must be application/json");
                    byte[] bytes = exchange.getRequestBody().readNBytes(settings.maxBodyBytes() + 1);
                    if (bytes.length > settings.maxBodyBytes()) throw new HttpResponseException(413, "Request body too large");
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
                response = action.apply(new Request(body));
            } catch (HttpResponseException e) {
                status = e.status();
                response = error(status, e.getMessage(), requestId);
            } catch (JsonParseException e) {
                status = 400;
                response = error(status, "Invalid JSON body", requestId);
            } catch (Exception | LinkageError e) {
                status = 500;
                // Do not expose request bodies, database details, or credentials in logs/responses.
                logger.log(Level.WARNING, "API request {0} failed ({1})", new Object[]{requestId, e.getClass().getSimpleName()});
                response = error(status, "Internal server error", requestId);
            }
            if (status == 503 || status == 429) exchange.getResponseHeaders().set("Retry-After", "1");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            if (settings.compression()) {
                exchange.getResponseHeaders().add("Vary", "Accept-Encoding");
                if (bytes.length >= settings.compressionMinBytes() && acceptsGzip(exchange.getRequestHeaders().getFirst("Accept-Encoding"))) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    try (GZIPOutputStream gzip = new GZIPOutputStream(output)) { gzip.write(bytes); }
                    bytes = output.toByteArray();
                    exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                }
            }
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            deadline.cancel(false);
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        if (!settings.cors()) return;
        exchange.getResponseHeaders().add("Vary", "Origin");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return;
        String allowed = settings.origins().contains("*") ? "*" : settings.origins().contains(origin) ? origin : null;
        if (allowed == null) return;
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowed);
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        if (settings.credentials()) exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    private static String error(int status, String message, String id) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        json.addProperty("code", status);
        json.addProperty("requestId", id);
        return json.toString();
    }

    static boolean acceptsGzip(String header) {
        if (header == null) return false;
        boolean wildcard = false;
        for (String entry : header.split(",")) {
            String[] parts = entry.trim().split(";");
            double quality = 1;
            for (int i = 1; i < parts.length; i++) {
                String[] parameter = parts[i].trim().split("=", 2);
                if (parameter[0].equalsIgnoreCase("q")) {
                    try { quality = parameter.length == 2 ? Double.parseDouble(parameter[1].trim()) : 0; }
                    catch (NumberFormatException e) { quality = 0; }
                }
            }
            boolean accepted = Double.isFinite(quality) && quality > 0 && quality <= 1;
            if (parts[0].trim().equalsIgnoreCase("gzip")) return accepted;
            if (parts[0].trim().equals("*")) wildcard = accepted;
        }
        return wildcard;
    }
}
