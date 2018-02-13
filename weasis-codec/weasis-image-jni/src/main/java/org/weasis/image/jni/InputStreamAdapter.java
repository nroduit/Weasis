/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.image.jni;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.ImageInputStream;

public final class InputStreamAdapter extends InputStream {

    final ImageInputStream stream;

    public InputStreamAdapter(ImageInputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException();
        }
        this.stream = stream;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        stream.mark();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() throws IOException {
        return stream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
    }

    @Override
    public synchronized void reset() throws IOException {
        stream.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return stream.skipBytes(n);
    }

    public ImageInputStream getWrappedStream() {
        return stream;
    }
}
