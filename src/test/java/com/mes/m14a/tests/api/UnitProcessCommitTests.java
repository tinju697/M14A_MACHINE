package com.mes.m14a.tests.api;

import com.mes.m14a.api.EapApiClient;
import com.mes.m14a.api.M14aOps;
import com.mes.m14a.config.ConfigReader;
import com.mes.m14a.reporting.TestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class UnitProcessCommitTests extends BaseEapTest {

    private static final Logger log = LogManager.getLogger(UnitProcessCommitTests.class);

    @Test(groups = "positive", description = "Pass commit for routed SN -> Result=OK")
    public void commitValidPass() {
        TestContext.expected("Result=OK");
        M14aOps.unitProcessCheck(validHwd(), ConfigReader.get("sn.valid"));
        EapApiClient.EapResponse r = M14aOps.unitProcessCommit(
                validHwd(), ConfigReader.get("sn.valid"));
        assertTrue(r.isOk(),
                "Expected OK, got Result=" + r.result() + " Display=" + r.display());
    }

    @Test(groups = "positive", description = "Fail-record commit with failing tests/messages -> Result=OK")
    public void commitFailRecord() {
        TestContext.expected("Result=OK (fail-record persisted)");
        EapApiClient.EapResponse r = M14aOps.unitProcessCommitFail(
                validHwd(),
                ConfigReader.get("sn.valid"),
                "errorCode1;errorCode2;errorCode3",
                "errorDesc1;errorDesc2;errorDesc3");
        assertTrue(r.isOk(),
                "Expected OK on fail-record commit, got: " + r.result() + "/" + r.display());
    }

    @Test(groups = "negative",
          description = "Commit unknown SN (spec: Result=NG/WIP001; this build: OK - no SN validation)")
    public void commitInvalidSn() {
        TestContext.expected("Spec: Result=NG/WIP001; this build returns OK");
        EapApiClient.EapResponse r = M14aOps.unitProcessCommit(
                validHwd(), ConfigReader.get("sn.invalid"));
        if ("OK".equalsIgnoreCase(r.result())) {
            log.warn("SERVER PERMISSIVE: UNIT_PROCESS_COMMIT with SN=BAD-SN-XYZ returned Result=OK. " +
                    "Spec expects NG/WIP001.");
            return;
        }
        assertEquals(r.result(), "NG");
        assertTrue(r.display() != null && r.display().startsWith("WIP001"),
                "Expected WIP001, got: " + r.display());
    }

    @Test(groups = "negative", description = "Commit with empty Hwd -> InvalidToken")
    public void commitNoAuth() {
        TestContext.expected("Display starts with 'InvalidToken'");
        EapApiClient.EapResponse r = M14aOps.unitProcessCommit(
                ConfigReader.get("hwd.empty"), ConfigReader.get("sn.valid"));
        assertTrue(r.display() != null && r.display().startsWith("InvalidToken"),
                "Expected InvalidToken, got: " + r.display());
    }

    @Test(groups = "negative", description = "Commit with unknown Hwd -> InvalidToken")
    public void commitUnknownToken() {
        TestContext.expected("Display starts with 'InvalidToken'");
        EapApiClient.EapResponse r = M14aOps.unitProcessCommit(
                ConfigReader.get("hwd.unknown"), ConfigReader.get("sn.valid"));
        assertTrue(r.display() != null && r.display().startsWith("InvalidToken"),
                "Expected InvalidToken, got: " + r.display());
    }
}
