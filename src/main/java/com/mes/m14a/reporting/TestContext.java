package com.mes.m14a.reporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-test ThreadLocal capture of EAP request/response bodies plus the
 * expected and actual outcome strings. Populated automatically by
 * {@link com.mes.m14a.api.EapApiClient}; consumed by the TestNG listeners
 * to enrich the Excel report.
 */
public final class TestContext {

    private static final ThreadLocal<List<String>> REQS   = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<String>> RESPS  = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<String>> TOKENS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<String>> URLS   = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<String> EXPECTED = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTUAL   = new ThreadLocal<>();

    private TestContext() {}

    public static void recordRequest(String body)  { REQS.get().add(body); }
    public static void recordResponse(String body) { RESPS.get().add(body); }
    public static void recordToken(String hwd)     { TOKENS.get().add(hwd == null || hwd.isEmpty() ? "(empty)" : hwd); }
    public static void recordUrl(String url)       { URLS.get().add(url == null ? "" : url); }

    public static void expected(String text) { EXPECTED.set(text); }
    public static void actual(String text)   { ACTUAL.set(text); }

    public static List<String> requests()  { return REQS.get(); }
    public static List<String> responses() { return RESPS.get(); }
    public static List<String> tokens()    { return TOKENS.get(); }
    public static List<String> urls()      { return URLS.get(); }
    public static String expected()        { return EXPECTED.get(); }
    public static String actual()          { return ACTUAL.get(); }

    public static void reset() {
        REQS.remove();
        RESPS.remove();
        TOKENS.remove();
        URLS.remove();
        EXPECTED.remove();
        ACTUAL.remove();
    }
}
