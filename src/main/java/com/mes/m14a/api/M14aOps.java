package com.mes.m14a.api;

import com.mes.m14a.config.ConfigReader;
import com.mes.m14a.model.EapPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * High-level helpers for the 4 APIs M14a uses (per Machine_Summary sheet):
 * LOGIN, BIND, UNIT_PROCESS_CHECK, UNIT_PROCESS_COMMIT.
 *
 * Station identity (LineCode/SectionCode/StationCode) is read from config; LOGIN
 * sends blank values per the protocol reference.
 */
public final class M14aOps {

    private static final Logger log = LogManager.getLogger(M14aOps.class);

    private M14aOps() {}

    private static String  line()    { return ConfigReader.get("m14a.lineCode"); }
    private static String  section() { return ConfigReader.get("m14a.sectionCode"); }
    private static Object  station() {
        String s = ConfigReader.get("m14a.stationCode");
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return s; }
    }

    // ---------- LOGIN ----------

    public static EapApiClient.EapResponse login(String user, String pwd) {
        // LOGIN sends blank Hwd and blank line/section/station per the spec.
        EapPayload payload = new EapPayload("", "", null, OpCategory.LOGIN)
                .put("user", user)
                .put("pwd",  pwd);
        EapApiClient.EapResponse r = EapApiClient.send("", Indicator.QUERY_RECORD, payload);
        String hwd = r.hwd();
        if (hwd != null && !hwd.isEmpty()) {
            log.info("LOGIN succeeded for user='{}' -> Hwd token = {}", user, hwd);
        } else {
            log.info("LOGIN failed   for user='{}' -> Display = {}", user, r.display());
        }
        return r;
    }

    // ---------- BIND ----------

    public static EapApiClient.EapResponse bind(String hwd, String serverVersion) {
        return bind(hwd, line(), section(), station(), serverVersion);
    }

    public static EapApiClient.EapResponse bind(String hwd,
                                                String lineCode,
                                                String sectionCode,
                                                Object stationCode,
                                                String serverVersion) {
        EapPayload payload = new EapPayload(lineCode, sectionCode, stationCode, OpCategory.BIND)
                .put("ServerVersion", serverVersion);
        return EapApiClient.send(hwd, Indicator.ADD_RECORD, payload);
    }

    // ---------- UNIT_PROCESS_CHECK ----------

    public static EapApiClient.EapResponse unitProcessCheck(String hwd, String sn) {
        EapPayload payload = new EapPayload(line(), section(), station(), OpCategory.UNIT_PROCESS_CHECK)
                .put("SN", sn);
        return EapApiClient.send(hwd, Indicator.QUERY_RECORD, payload);
    }

    // ---------- UNIT_PROCESS_COMMIT ----------

    /** Pass commit. */
    public static EapApiClient.EapResponse unitProcessCommit(String hwd, String sn) {
        EapPayload payload = new EapPayload(line(), section(), station(), OpCategory.UNIT_PROCESS_COMMIT)
                .put("SN", sn);
        return EapApiClient.send(hwd, Indicator.ADD_RECORD, payload);
    }

    /** Test-station failure commit (per API_Protocol_Reference row 13). */
    public static EapApiClient.EapResponse unitProcessCommitFail(String hwd,
                                                                 String sn,
                                                                 String failingTestsSemicolon,
                                                                 String failureMessageSemicolon) {
        EapPayload payload = new EapPayload(line(), section(), station(), OpCategory.UNIT_PROCESS_COMMIT)
                .put("SN", sn)
                .put("result", "FAIL")
                .put("list_of_failing_tests", failingTestsSemicolon)
                .put("failure_message",       failureMessageSemicolon);
        return EapApiClient.send(hwd, Indicator.ADD_RECORD, payload);
    }
}
