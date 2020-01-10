/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.launcher;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class AppLauncher extends WeasisLauncher implements Singleton.SingletonApp {

    public AppLauncher(ConfigData configData) {
        super(configData);
    }

    public static void main(String[] argv) throws Exception {
        ConfigData configData = new ConfigData(argv);
        if (!Singleton.invoke(configData)) {
            AppLauncher instance = new AppLauncher(configData);
            Singleton.start(instance, configData.getSourceID());
            instance.launch(Type.NATIVE);
        }
    }

    @Override
    public void newActivation(List<String> arguments) {
        waitWhenStarted();
        if (mTracker != null) {
            executeCommands(arguments, null);
        }
    }

    private void waitWhenStarted() {
        synchronized (this) {
            int loop = 0;
            boolean runLoop = true;
            while (runLoop && !frameworkLoaded) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                    loop++;
                    if (loop > 300) { // Let 30s max to setup Felix framework
                        runLoop = false;
                    }
                } catch (InterruptedException e) {
                    runLoop = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public boolean canStartNewActivation(Properties prop) {
        boolean sameUser = configData.isPropertyValueSimilar(P_WEASIS_USER, prop.getProperty(P_WEASIS_USER));
        boolean sameConfig = configData.isPropertyValueSimilar(P_WEASIS_CONFIG_HASH, prop.getProperty(P_WEASIS_CONFIG_HASH));
        return sameUser && sameConfig;
    }

    @Override
    protected void stopSingletonServer() {
        Singleton.stop();
    }
}
