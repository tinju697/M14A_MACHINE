package com.mes.m14a.listeners;

import com.mes.m14a.reporting.TestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestListener implements ITestListener {

    private static final Logger log = LogManager.getLogger(TestListener.class);

    @Override public void onTestStart(ITestResult r) {
        TestContext.reset();
        log.info(">> START: {}", r.getMethod().getMethodName());
    }
    @Override public void onTestSuccess(ITestResult r) {
        capture(r);
        log.info("<< PASS : {}", r.getMethod().getMethodName());
    }
    @Override public void onTestFailure(ITestResult r) {
        capture(r);
        log.error("<< FAIL : {} - {}",
                r.getMethod().getMethodName(),
                r.getThrowable() != null ? r.getThrowable().getMessage() : "");
    }
    @Override public void onTestSkipped(ITestResult r) {
        capture(r);
        log.warn("<< SKIP : {}", r.getMethod().getMethodName());
    }
    @Override public void onStart(ITestContext c) {
        log.info("===== Starting suite: {} =====", c.getName());
    }
    @Override public void onFinish(ITestContext c) {
        log.info("===== Finished suite: {} ({} passed / {} failed / {} skipped) =====",
                c.getName(),
                c.getPassedTests().size(),
                c.getFailedTests().size(),
                c.getSkippedTests().size());
    }

    /** Copies the ThreadLocal capture onto the test result so the Excel report can read it. */
    private static void capture(ITestResult r) {
        r.setAttribute("urls",      String.join("\n",       TestContext.urls()));
        r.setAttribute("requests",  String.join("\n---\n", TestContext.requests()));
        r.setAttribute("responses", String.join("\n---\n", TestContext.responses()));
        r.setAttribute("tokens",    String.join("\n",       TestContext.tokens()));
        r.setAttribute("expected",  nz(TestContext.expected()));
        r.setAttribute("actual",    nz(TestContext.actual()));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
