package com.mes.m14a.tests.api;

import com.mes.m14a.api.EapApiClient;
import com.mes.m14a.api.M14aOps;
import com.mes.m14a.config.ConfigReader;

/**
 * Shared helpers for M14a API tests. The login Hwd is fetched lazily and cached
 * across tests in the same JVM so we don't re-authenticate for every case.
 */
abstract class BaseEapTest {

    private static volatile String cachedHwd;

    protected static String validHwd() {
        if (cachedHwd == null || cachedHwd.isEmpty()) {
            EapApiClient.EapResponse r = M14aOps.login(
                    ConfigReader.get("creds.valid.user"),
                    ConfigReader.get("creds.valid.pwd"));
            cachedHwd = r.hwd();
            if (cachedHwd == null || cachedHwd.isEmpty()) {
                throw new IllegalStateException(
                        "Could not obtain a valid Hwd token for test setup. " +
                        "Server response Display=" + r.display());
            }
        }
        return cachedHwd;
    }

    /** Used by negative tests that need to invalidate the cached session. */
    protected static void clearCachedHwd() {
        cachedHwd = null;
    }
}
