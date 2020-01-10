/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Objects;

public class ClosableURLConnection implements AutoCloseable {

    private final URLConnection urlConnection;

    public ClosableURLConnection(URLConnection urlConnection) {
        this.urlConnection = Objects.requireNonNull(urlConnection);
    }

    @Override
    public void close() {
        if (urlConnection instanceof HttpURLConnection) {
            ((HttpURLConnection) urlConnection).disconnect();
        }
    }

    public InputStream getInputStream() throws IOException {
        return urlConnection.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return urlConnection.getOutputStream();
    }

    public URLConnection getUrlConnection() {
        return urlConnection;
    }
}
