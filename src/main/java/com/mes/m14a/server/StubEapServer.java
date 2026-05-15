package com.mes.m14a.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.m14a.model.EapEnvelope;
import com.mes.m14a.model.EapPayload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process stub of the FitMesWpfServer EAP HttpListener.
 *
 * Implements the EAP envelope protocol and the response cases described in
 * the spreadsheet (API_Protocol_Reference + Error_Codes) so the framework can
 * be exercised end-to-end without the real WPF server running.
 *
 * Enable with {@code server.useStub=true} in config.properties.
 */
public final class StubEapServer {

    private static final Logger log = LogManager.getLogger(StubEapServer.class);
    private static final ObjectMapper M = new ObjectMapper();

    private static HttpServer server;
    private static final Set<String> issuedTokens = ConcurrentHashMap.newKeySet();

    private StubEapServer() {}

    public static synchronized void start(String host, int port) {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/api/v1/EapApi/", StubEapServer::handle);
            server.createContext("/api/v1/EapApi",  StubEapServer::handle);
            server.setExecutor(null);
            server.start();
            log.info("StubEapServer listening on {}:{}", host, port);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start StubEapServer on " + host + ":" + port, e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            issuedTokens.clear();
            log.info("StubEapServer stopped");
        }
    }

    // ---------- handler ----------

    private static void handle(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "Method Not Allowed");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        EapEnvelope req;
        EapPayload  payload;
        try {
            req = M.readValue(body, EapEnvelope.class);
            payload = req.serializeData == null || req.serializeData.isEmpty()
                    ? new EapPayload()
                    : M.readValue(req.serializeData, EapPayload.class);
        } catch (Exception e) {
            respond(ex, 200, error("", "QUERY_RECORD", "ParseJsonError"));
            return;
        }

        String op = payload.opCategory == null ? "" : payload.opCategory;
        switch (op) {
            case "LOGIN":                respond(ex, 200, handleLogin(req, payload));  return;
            case "BIND":                 respond(ex, 200, handleBind(req, payload));   return;
            case "UNIT_PROCESS_CHECK":   respond(ex, 200, handleCheck(req, payload));  return;
            case "UNIT_PROCESS_COMMIT":  respond(ex, 200, handleCommit(req, payload)); return;
            default:                     respond(ex, 200, error(req.hwd, req.indicator, "NoSuchCommand"));
        }
    }

    // ---------- LOGIN ----------

    private static String handleLogin(EapEnvelope env, EapPayload p) throws IOException {
        String user = str(p.opRequestInfo, "user");
        String pwd  = str(p.opRequestInfo, "pwd");

        if (pwd == null || pwd.isEmpty()) {
            return error(env.hwd, env.indicator, "pwd field is required");
        }
        if ("unauth_user".equals(user)) {
            // valid user but no permission
            p.opResponseInfo.put("Hwd", "");
            return wrap(env, p, "ID003:The login account does not have permission to operate this workstation");
        }
        boolean valid = "admin".equals(user) && "admin".equals(pwd);
        if (!valid) {
            p.opResponseInfo.put("Hwd", "");
            return wrap(env, p, "LoginFail");
        }
        String token = UUID.randomUUID().toString();
        issuedTokens.add(token);
        p.opResponseInfo.put("Hwd", token);
        return wrap(env, p, null);
    }

    // ---------- BIND ----------

    private static String handleBind(EapEnvelope env, EapPayload p) throws IOException {
        String tokenError = validateToken(env.hwd);
        if (tokenError != null) {
            return error(env.hwd, env.indicator, tokenError);
        }
        if (!p.opRequestInfo.containsKey("ServerVersion")
                || str(p.opRequestInfo, "ServerVersion") == null
                || str(p.opRequestInfo, "ServerVersion").isEmpty()) {
            return error(env.hwd, env.indicator, "Json Fields Required: OPRequestInfo.ServerVersion");
        }
        if (!isKnownStation(p)) {
            p.opResponseInfo.put("Result", "NG");
            return wrap(env, p, "AUTO001:MES station is not defined");
        }
        p.opResponseInfo.put("Result", "OK");
        return wrap(env, p, null);
    }

    // ---------- UNIT_PROCESS_CHECK ----------

    private static String handleCheck(EapEnvelope env, EapPayload p) throws IOException {
        String tokenError = validateToken(env.hwd);
        if (tokenError != null) return error(env.hwd, env.indicator, tokenError);

        if (!p.opRequestInfo.containsKey("SN")
                || str(p.opRequestInfo, "SN") == null
                || str(p.opRequestInfo, "SN").isEmpty()) {
            return error(env.hwd, env.indicator, "Json Fields Required: OPRequestInfo.SN");
        }
        if (!isKnownStation(p)) {
            p.opResponseInfo.put("Result", "NG");
            return wrap(env, p, "AUTO001:MES station is not defined");
        }
        if (!isKnownSn(str(p.opRequestInfo, "SN"))) {
            p.opResponseInfo.put("Result", "NG");
            return wrap(env, p, "WIP001:Invalid SN");
        }
        p.opResponseInfo.put("Result", "OK");
        return wrap(env, p, null);
    }

    // ---------- UNIT_PROCESS_COMMIT ----------

    private static String handleCommit(EapEnvelope env, EapPayload p) throws IOException {
        String tokenError = validateToken(env.hwd);
        if (tokenError != null) return error(env.hwd, env.indicator, tokenError);

        if (!p.opRequestInfo.containsKey("SN")
                || str(p.opRequestInfo, "SN") == null
                || str(p.opRequestInfo, "SN").isEmpty()) {
            return error(env.hwd, env.indicator, "Json Fields Required: OPRequestInfo.SN");
        }
        if (!isKnownStation(p)) {
            p.opResponseInfo.put("Result", "NG");
            return wrap(env, p, "AUTO001:MES station is not defined");
        }
        if (!isKnownSn(str(p.opRequestInfo, "SN"))) {
            p.opResponseInfo.put("Result", "NG");
            return wrap(env, p, "WIP001:Invalid SN");
        }
        p.opResponseInfo.put("Result", "OK");
        return wrap(env, p, null);
    }

    // ---------- token + data ----------

    private static String validateToken(String hwd) {
        if (hwd == null || hwd.isEmpty()) return "InvalidTokenFormat";
        if (!hwd.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return "Guid should contain 32 digits with 4 dashes (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).";
        }
        if (!issuedTokens.contains(hwd)) return "InvalidToken";
        return null;
    }

    private static boolean isKnownStation(EapPayload p) {
        return p.sectionCode != null
                && !"FAKE".equalsIgnoreCase(p.sectionCode)
                && !"".equals(p.sectionCode);
    }

    private static final Set<String> KNOWN_SNS = new HashSet<>();
    static {
        KNOWN_SNS.add("J21GYM001M100000US");
        KNOWN_SNS.add("C4723381050804852010389");
        KNOWN_SNS.add("H2C336500010VC6RX");
    }
    private static boolean isKnownSn(String sn) {
        return sn != null && KNOWN_SNS.contains(sn);
    }

    // ---------- envelope helpers ----------

    private static String wrap(EapEnvelope inEnv, EapPayload payload, String display) throws IOException {
        EapEnvelope out = new EapEnvelope(
                inEnv.hwd == null ? "" : inEnv.hwd,
                inEnv.indicator,
                M.writeValueAsString(payload));
        out.display = display;
        return M.writeValueAsString(out);
    }

    private static String error(String hwd, String indicator, String display) {
        try {
            EapPayload empty = new EapPayload();
            empty.opResponseInfo.put("Hwd", "");
            empty.opResponseInfo.put("Result", "NG");
            EapEnvelope out = new EapEnvelope(
                    hwd == null ? "" : hwd,
                    indicator == null ? "" : indicator,
                    M.writeValueAsString(empty));
            out.display = display;
            return M.writeValueAsString(out);
        } catch (IOException e) {
            return "{\"Display\":\"" + display + "\"}";
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? null : v.toString();
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
