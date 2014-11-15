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