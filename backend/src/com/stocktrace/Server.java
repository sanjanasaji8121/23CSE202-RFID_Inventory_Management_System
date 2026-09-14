package com.stocktrace;

import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.*;

/**
 * Plain JDK HTTP server (no framework) exposing the REST API the frontend's
 * data.js is written to call, plus static-file serving so the whole site can
 * be opened from http://localhost:8080 instead of file:// (which avoids the
 * fetch()-from-file:// CORS headache entirely).
 *
 * Run with:  java -cp "out:lib/sqlite-jdbc.jar" com.stocktrace.Server
 * (see README.md in this folder for the full walkthrough)
 */
public class Server {

    private final Db db;
    private final Path staticRoot;

    public Server(Db db, Path staticRoot) {
        this.db = db;
        this.staticRoot = staticRoot;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String dbPath = System.getProperty("db.path", "stocktrace.db");
        Path staticRoot = Paths.get(System.getProperty("static.root", "../site"));

        Db db = new Db(dbPath);
        Server server = new Server(db, staticRoot);

        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/api/", server::handleApi);
        http.createContext("/", server::handleStatic);
        http.setExecutor(null);
        http.start();

        System.out.println("StockTrace backend running at http://localhost:" + port);
        System.out.println("Serving frontend from: " + staticRoot.toAbsolutePath());
        System.out.println("Database file: " + Paths.get(dbPath).toAbsolutePath());
    }

    // ---------- static files ----------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/login.html";
        Path file = staticRoot.resolve(path.substring(1)).normalize();

        if (!file.startsWith(staticRoot) || !Files.exists(file) || Files.isDirectory(file)) {
            respondText(ex, 404, "text/plain", "Not found: " + path);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        String contentType = URLConnection.guessContentTypeFromName(file.toString());
        if (contentType == null) contentType = "application/octet-stream";
        if (file.toString().endsWith(".css")) contentType = "text/css";
        if (file.toString().endsWith(".js")) contentType = "application/javascript";
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ---------- API routing ----------

    private record Route(String method, Pattern pattern, RouteHandler handler) {}
    private interface RouteHandler { Object handle(HttpExchange ex, Matcher m, Map<String, Object> body) throws Exception; }

    private final List<Route> routes = buildRoutes();

    private List<Route> buildRoutes() {
        List<Route> r = new ArrayList<>();
        r.add(new Route("GET", p("/api/products"), (ex, m, b) -> db.getProducts()));
        r.add(new Route("POST", p("/api/products"), (ex, m, b) -> { db.addProduct(b); return Map.of("ok", true); }));
        r.add(new Route("GET", p("/api/products/([^/]+)"), (ex, m, b) -> db.getProduct(decode(m.group(1)))));
        r.add(new Route("PATCH", p("/api/products/([^/]+)"), (ex, m, b) -> { db.updateProduct(decode(m.group(1)), b); return Map.of("ok", true); }));
        r.add(new Route("DELETE", p("/api/products/([^/]+)"), (ex, m, b) -> { db.deleteProduct(decode(m.group(1))); return Map.of("ok", true); }));
        r.add(new Route("GET", p("/api/products/([^/]+)/scans"), (ex, m, b) -> db.getScansForProduct(decode(m.group(1)))));
        r.add(new Route("POST", p("/api/products/([^/]+)/adjust"), (ex, m, b) -> {
            int delta = Json.intVal(b, "delta");
            String reason = Json.str(b, "reason");
            db.recordMovement(decode(m.group(1)), delta >= 0 ? "IN" : "OUT", Math.abs(delta), reason);
            return Map.of("ok", true);
        }));
        r.add(new Route("POST", p("/api/products/([^/]+)/reassign-tag"), (ex, m, b) -> {
            db.reassignTag(decode(m.group(1)), Json.str(b, "newTagId"));
            return Map.of("ok", true);
        }));
        r.add(new Route("GET", p("/api/categories"), (ex, m, b) -> db.getCategories()));

        r.add(new Route("GET", p("/api/scans"), (ex, m, b) -> db.getScans()));
        r.add(new Route("POST", p("/api/scans"), (ex, m, b) -> {
            db.recordMovement(Json.str(b, "tagId"), Json.str(b, "action"), Json.intVal(b, "qty"), Json.str(b, "reason"));
            return Map.of("ok", true);
        }));

        r.add(new Route("GET", p("/api/orders"), (ex, m, b) -> db.getOrders()));
        r.add(new Route("POST", p("/api/orders"), (ex, m, b) -> Map.of("id", db.addOrder(b))));
        r.add(new Route("GET", p("/api/orders/([^/]+)"), (ex, m, b) -> db.getOrder(decode(m.group(1)))));
        r.add(new Route("PATCH", p("/api/orders/([^/]+)"), (ex, m, b) -> { db.updateOrderStatus(decode(m.group(1)), Json.str(b, "status")); return Map.of("ok", true); }));

        r.add(new Route("GET", p("/api/suppliers"), (ex, m, b) -> db.getSuppliers()));
        r.add(new Route("POST", p("/api/suppliers"), (ex, m, b) -> { db.addSupplier(b); return Map.of("ok", true); }));
        r.add(new Route("GET", p("/api/suppliers/([^/]+)"), (ex, m, b) -> db.getSupplier(decode(m.group(1)))));
        r.add(new Route("PATCH", p("/api/suppliers/([^/]+)"), (ex, m, b) -> { db.updateSupplier(decode(m.group(1)), b); return Map.of("ok", true); }));
        r.add(new Route("GET", p("/api/suppliers/([^/]+)/orders"), (ex, m, b) -> db.getOrdersForSupplier(decode(m.group(1)))));

        r.add(new Route("GET", p("/api/users"), (ex, m, b) -> db.getUsers()));
        r.add(new Route("POST", p("/api/users"), (ex, m, b) -> { db.addUser(b); return Map.of("ok", true); }));

        r.add(new Route("GET", p("/api/audit"), (ex, m, b) -> db.getAuditLog()));
        r.add(new Route("GET", p("/api/settings"), (ex, m, b) -> db.getSettings()));
        r.add(new Route("PATCH", p("/api/settings"), (ex, m, b) -> { db.updateSettings(b); return Map.of("ok", true); }));

        r.add(new Route("GET", p("/api/dashboard/summary"), (ex, m, b) -> db.getDashboardSummary()));
        r.add(new Route("GET", p("/api/reports/stock-by-category"), (ex, m, b) -> db.getStockByCategory()));
        r.add(new Route("GET", p("/api/reports/movement-trend"), (ex, m, b) -> db.getMovementTrend()));
        r.add(new Route("GET", p("/api/reports/reorder-suggestions"), (ex, m, b) -> db.getReorderSuggestions()));
        return r;
    }

    private static Pattern p(String regex) { return Pattern.compile("^" + regex + "$"); }

    private void handleApi(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        // basic CORS so the frontend can also be opened separately during development
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if (method.equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }

        try {
            for (Route route : routes) {
                if (!route.method().equals(method)) continue;
                Matcher m = route.pattern().matcher(path);
                if (!m.matches()) continue;

                Map<String, Object> body = readBody(ex);
                Object result = route.handler().handle(ex, m, body);
                respondJson(ex, 200, result);
                return;
            }
            respondJson(ex, 404, Map.of("error", "No route for " + method + " " + path));
        } catch (SQLException e) {
            respondJson(ex, 500, Map.of("error", "Database error: " + e.getMessage()));
        } catch (Exception e) {
            respondJson(ex, 400, Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private Map<String, Object> readBody(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST") && !ex.getRequestMethod().equals("PATCH")) return Map.of();
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) return Map.of();
        return Json.parseObject(raw);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private void respondJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void respondText(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
