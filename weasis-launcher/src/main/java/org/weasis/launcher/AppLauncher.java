/*******************************************************************************
 * Copyright (C) 2009-2018 Weasis Team and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.util.concurrent.TimeUnit;

public class AppLauncher extends WeasisLauncher implements Singleton.SingletonApp {

    public AppLauncher(ConfigData configData) {
        super(configData);
    }

    public static void main(String[] argv) throws Exception {
        String id = System.getProperty("app.preferences.id", "org.weasis.viewer").replace('/', '.');
        ConfigData configData = new ConfigData(argv);
        id += "." + configData.getSourceID();
        if (!Singleton.invoke(id, argv)) {
            AppLauncher instance = new AppLauncher(configData);
            Singleton.start(instance, id);
            instance.launch(Type.NATIVE);
        }
    }

    @Override
    public void newActivation(ConfigData data) {
        waitWhenStarted();
        if (mTracker != null) {
            executeCommands(data.getArguments(), null);
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
    public boolean canStartNewActivation(ConfigData data) {
        boolean sameUser = configData.isPropertyValueSimilar(data, P_WEASIS_USER);
        boolean sameVersion = configData.isPropertyValueSimilar(data, P_WEASIS_VERSION);
        return sameUser && sameVersion;
    }

    @Override
    protected void stopSingletonServer() {
        Singleton.stop();
    }
}
