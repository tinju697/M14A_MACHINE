package com.mes.m14a.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mes.m14a.config.ConfigReader;
import com.mes.m14a.model.EapEnvelope;
import com.mes.m14a.model.EapPayload;
import com.mes.m14a.reporting.TestContext;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static io.restassured.RestAssured.given;

/**
 * Single-endpoint EAP API client.
 *
 * Every call POSTs to {@code api.baseUrl} with a JSON envelope whose SerializeData field
 * contains a JSON-encoded string of the inner payload. This client handles the
 * double-serialization so callers can work with {@link EapPayload} POJOs directly.
 */
public final class EapApiClient {

    private static final Logger log = LogManager.getLogger(EapApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private EapApiClient() {}

    /** Send a request with a fully-built envelope (used for negative tests with malformed bodies). */
    public static Response sendRaw(String rawJsonBody) {
        String url = ConfigReader.get("api.baseUrl");
        log.info("POST {} (raw body)", url);
        TestContext.recordUrl("POST " + url);
        TestContext.recordRequest(rawJsonBody);
        Response r = given().spec(spec()).body(rawJsonBody).post("");
        TestContext.recordResponse(r.asString());
        return r;
    }

    /** Send a properly-shaped request. Returns the parsed response envelope + inner payload. */
    public static EapResponse send(String hwd, Indicator indicator, EapPayload payload) {
        try {
            EapEnvelope envelope = new EapEnvelope(
                    hwd == null ? "" : hwd,
                    indicator.name(),
                    MAPPER.writeValueAsString(payload));

            String body = MAPPER.writeValueAsString(envelope);
            TestContext.recordUrl("POST " + ConfigReader.get("api.baseUrl"));
            TestContext.recordToken(hwd);
            TestContext.recordRequest(body);

            Response raw = given().spec(spec()).body(body).post("");
            String rawText = raw.asString();
            TestContext.recordResponse(rawText);

            EapEnvelope respEnv;
            EapPayload  respPayload = new EapPayload();
            try {
                respEnv = MAPPER.readValue(rawText, EapEnvelope.class);
                if (respEnv.serializeData != null && !respEnv.serializeData.isEmpty()) {
                    respPayload = MAPPER.readValue(respEnv.serializeData, EapPayload.class);
                }
            } catch (JsonProcessingException e) {
                log.warn("Could not parse response envelope/payload: {}", e.getMessage());
                respEnv = new EapEnvelope();
            }

            EapResponse r = new EapResponse(raw, respEnv, respPayload);
            TestContext.actual(formatActual(r));
            return r;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EAP request", e);
        }
    }

    private static String formatActual(EapResponse r) {
        String result  = r.result();
        String display = r.display();
        String hwd     = r.hwd();
        StringBuilder sb = new StringBuilder();
        if (result != null && !result.isEmpty())   sb.append("Result=").append(result);
        if (display != null && !display.isEmpty()) { if (sb.length() > 0) sb.append("; "); sb.append("Display=").append(display); }
        if (hwd != null && !hwd.isEmpty())         { if (sb.length() > 0) sb.append("; "); sb.append("Hwd=").append(hwd); }
        return sb.toString();
    }

    private static RequestSpecification spec() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigReader.get("api.baseUrl"))
                .setContentType(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .build();
    }

    /** Aggregated EAP response: raw Rest Assured response + parsed envelope + parsed inner payload. */
    public static final class EapResponse {
        public final Response    raw;
        public final EapEnvelope envelope;
        public final EapPayload  payload;

        EapResponse(Response raw, EapEnvelope env, EapPayload pl) {
            this.raw = raw;
            this.envelope = env;
            this.payload = pl;
        }

        public int statusCode()    { return raw.statusCode(); }
        public String display()    { return envelope == null ? null : envelope.display; }
        public String hwd()        { return payload.responseString("Hwd"); }
        public String result()     { return payload.responseString("Result"); }
        public boolean isOk()      { return "OK".equalsIgnoreCase(result()); }
    }
}
