package com.mes.m14a.listeners;

import com.mes.m14a.server.ServerManager;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/** Starts FitMesWpfServer once per suite, stops it when the suite finishes. */
public class ServerSuiteListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        ServerManager.start();
    }

    @Override
    public void onFinish(ISuite suite) {
        ServerManager.stop();
    }
}
