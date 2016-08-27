/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.au;

import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class DcmAudioStream extends FilterInputStream {
    private final FileChannel myFileChannel;
    private final long mark;

    public DcmAudioStream(FileInputStream fis, long offset) {
        super(fis);
        myFileChannel = fis.getChannel();
        mark = offset;
        try {
            reset();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark != -1) {
            myFileChannel.position(mark);
        }
    }
}