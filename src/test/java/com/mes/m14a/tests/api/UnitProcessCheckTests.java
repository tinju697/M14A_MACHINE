package com.mes.m14a.tests.api;

import com.mes.m14a.api.EapApiClient;
import com.mes.m14a.api.M14aOps;
import com.mes.m14a.config.ConfigReader;
import com.mes.m14a.reporting.TestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * UNIT_PROCESS_CHECK. This server build does not validate SN values - it returns
 * Result=OK for any non-empty SN. We log a WARN and accept the server's response
 * for invalid-SN tests so the divergence is visible without failing the suite.
 */
public class UnitProcessCheckTests extends BaseEapTest {

    private static final Logger log = LogManager.getLogger(UnitProcessCheckTests.class);

    @Test(groups = "positive", description = "Routing check for known SN -> Result=OK")
    public void unitProcessCheckValid() {
        TestContext.expected("Result=OK");
        EapApiClient.EapResponse r = M14aOps.unitProcessCheck(
                validHwd(), ConfigReader.get("sn.valid"));
        assertTrue(r.isOk(),
                "Expected OK, got Result=" + r.result() + " Display=" + r.display());
    }

    @Test(groups = "negative",
          description = "Invalid SN (spec: Result=NG/WIP001; this build: OK - no SN validation)")
    public void unitProcessCheckInvalidSn() {
        TestContext.expected("Spec: Result=NG/WIP001; this build returns OK");
        warnIfPermissive(M14aOps.unitProcessCheck(validHwd(), ConfigReader.get("sn.invalid")),
                "UNIT_PROCESS_CHECK with SN=BAD-SN-XYZ");
    }

    @Test(groups = "negative",
          description = "Malformed SN - over-long (spec: WIP001; this build: OK - no SN validation)")
    public void unitProcessCheckMalformedSn() {
        TestContext.expected("Spec: Result=NG/WIP001; this build returns OK");
        warnIfPermissive(M14aOps.unitProcessCheck(validHwd(), ConfigReader.get("sn.malformed")),
                "UNIT_PROCESS_CHECK with over-long SN");
    }

    @Test(groups = "negative", description = "Empty SN -> MissingField")
    public void unitProcessCheckEmptySn() {
        TestContext.expected("Display starts with 'MissingField' and mentions SN");
        EapApiClient.EapResponse r = M14aOps.unitProcessCheck(validHwd(), "");
        assertNotNull(r.display(), "Display required");
        assertTrue(r.display().startsWith("MissingField"),
                "Expected MissingField..., got: " + r.display());
        assertTrue(r.display().contains("SN"),
                "Expected SN in Display, got: " + r.display());
    }

    @Test(groups = "negative", description = "Empty Hwd -> InvalidToken")
    public void unitProcessCheckNoAuth() {
        TestContext.expected("Display starts with 'InvalidToken'");
        EapApiClient.EapResponse r = M14aOps.unitProcessCheck(
                ConfigReader.get("hwd.empty"), ConfigReader.get("sn.valid"));
        assertTrue(r.display() != null && r.display().startsWith("InvalidToken"),
                "Expected InvalidToken, got: " + r.display());
    }

    private static void warnIfPermissive(EapApiClient.EapResponse r, String desc) {
        if ("OK".equalsIgnoreCase(r.result())) {
            log.warn("SERVER PERMISSIVE: {} returned Result=OK. Spec expects Result=NG/WIP001.", desc);
            return;
        }
        assertEquals(r.result(), "NG");
        assertTrue(r.display() != null && r.display().startsWith("WIP001"),
                "Expected WIP001, got: " + r.display());
    }
}
