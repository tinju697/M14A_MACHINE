package com.mes.m14a.tests.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mes.m14a.api.EapApiClient;
import com.mes.m14a.api.M14aOps;
import com.mes.m14a.config.ConfigReader;
import com.mes.m14a.model.EapEnvelope;
import com.mes.m14a.reporting.JsonResource;
import com.mes.m14a.reporting.TestContext;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.testng.Assert.*;

/**
 * LOGIN test cases against the live FitMesWpfServer. Assertions match this
 * server's actual error vocabulary ("LoginFail: ...", "InvalidToken: ...").
 * The spreadsheet's more granular codes (ID003, "pwd field is required") are
 * not produced by this build, so we assert the family ("LoginFail") and rely
 * on the Display column in the report to show the exact text.
 */
public class LoginTests {

    @Test(groups = "positive",
          description = "TC_API_LOGIN_V01 - POST login.json -> 36-char GUID Hwd; saved to target/session.json")
    public void loginValid() throws Exception {
        TestContext.expected("Hwd is a 36-char GUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)");

        String loginBody = JsonResource.read("login.json");
        Response raw = EapApiClient.sendRaw(loginBody);
        assertEquals(raw.statusCode(), 200);

        ObjectMapper om = new ObjectMapper();
        EapEnvelope env = om.readValue(raw.asString(), EapEnvelope.class);
        ObjectNode payload = (ObjectNode) om.readTree(
                env.serializeData == null ? "{}" : env.serializeData);
        String hwd = payload.path("OPResponseInfo").path("Hwd").asText("");

        assertNotNull(hwd, "Hwd token missing");
        assertEquals(hwd.length(), 36, "Hwd should be a 36-char GUID, got: " + hwd);
        assertTrue(hwd.matches("[0-9a-fA-F-]{36}"), "Hwd not a GUID: " + hwd);

        String session = "{\n  \"hwd\": \"" + hwd + "\",\n" +
                "  \"user\": \"" + ConfigReader.get("creds.valid.user") + "\"\n}\n";
        Path out = JsonResource.writeUnderTarget("session.json", session);
        TestContext.actual("Hwd=" + hwd + "  (saved to " + out + ")");
    }

    @Test(groups = "negative", description = "TC_API_LOGIN_I01 - wrong password -> LoginFail")
    public void loginWrongPassword() {
        TestContext.expected("Hwd empty; Display starts with 'LoginFail'");
        EapApiClient.EapResponse r = M14aOps.login(
                ConfigReader.get("creds.wrongPwd.user"),
                ConfigReader.get("creds.wrongPwd.pwd"));
        assertEquals(r.hwd(), "", "Hwd must be empty on wrong password");
        assertNotNull(r.display(), "Display must be present");
        assertTrue(r.display().startsWith("LoginFail"),
                "Expected Display starting with 'LoginFail', got: " + r.display());
    }

    @Test(groups = "negative", description = "TC_API_LOGIN_I02 - unknown user -> LoginFail")
    public void loginUnknownUser() {
        TestContext.expected("Hwd empty; Display starts with 'LoginFail'");
        EapApiClient.EapResponse r = M14aOps.login(
                ConfigReader.get("creds.invalid.user"),
                ConfigReader.get("creds.invalid.pwd"));
        assertEquals(r.hwd(), "");
        assertTrue(r.display() != null && r.display().startsWith("LoginFail"),
                "Expected Display starting with 'LoginFail', got: " + r.display());
    }

    @Test(groups = "negative", description = "TC_API_LOGIN_I03 - empty pwd -> rejected")
    public void loginEmptyPassword() {
        TestContext.expected("Hwd empty; Display starts with 'LoginFail' (this build does not return 'pwd field is required')");
        EapApiClient.EapResponse r = M14aOps.login(
                ConfigReader.get("creds.valid.user"),
                "");
        assertEquals(r.hwd(), "");
        assertTrue(r.display() != null && r.display().startsWith("LoginFail"),
                "Expected Display starting with 'LoginFail' (server merges this with wrong-pwd), got: " + r.display());
    }

    @Test(groups = "negative", description = "TC_API_LOGIN_I04 - no-permission user -> LoginFail (this build has no per-station permissions)")
    public void loginNoStationPermission() {
        TestContext.expected("Hwd empty; Display starts with 'LoginFail' (spec says ID003; this build has no per-station permissions and falls back to LoginFail)");
        EapApiClient.EapResponse r = M14aOps.login(
                ConfigReader.get("creds.noPermission.user"),
                ConfigReader.get("creds.noPermission.pwd"));
        assertEquals(r.hwd(), "");
        assertTrue(r.display() != null && r.display().startsWith("LoginFail"),
                "Expected Display starting with 'LoginFail' (server has no permission layer), got: " + r.display());
    }
}
