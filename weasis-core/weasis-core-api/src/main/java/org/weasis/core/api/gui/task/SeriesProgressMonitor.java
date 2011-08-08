package org.weasis.core.api.gui.task;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;

public class SeriesProgressMonitor extends FilterInputStream {
    private final Series series;
    private int nread = 0;
    private int size = 0;

    public SeriesProgressMonitor(final Series series, InputStream in) {
        super(in);
        if (series == null)
            throw new IllegalArgumentException("Series cannot be null!"); //$NON-NLS-1$
        this.series = series;
        try {
            size = in.available();
        } catch (IOException ioe) {
            size = 0;
        }
    }

    private boolean isLoadingSeriesCanceled() {
        SeriesImporter loader = series.getSeriesLoader();
        return (loader == null || loader.isStopped());
    }

    private void updateSeriesProgression(double addSize) {
        series.setFileSize(series.getFileSize() + addSize);
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
                if (thumb != null) {
                    thumb.repaint();
                }
            }
        });
    }

    @Override
    public int read() throws IOException {
        if (isLoadingSeriesCanceled()) {
            InterruptedIOException exc = new InterruptedIOException("progress"); //$NON-NLS-1$
            exc.bytesTransferred = nread;
            series.setFileSize(series.getFileSize() - nread);
            nread = 0;
            throw exc;
        }
        int c = in.read();
        if (c >= 0) {
            nread++;
            updateSeriesProgression(1.0);
        }

        return c;
    }

    @Override
    public int read(byte b[]) throws IOException {
        if (isLoadingSeriesCanceled()) {
            InterruptedIOException exc = new InterruptedIOException("progress"); //$NON-NLS-1$
            exc.bytesTransferred = nread;
            series.setFileSize(series.getFileSize() - nread);
            nread = 0;
            throw exc;
        }
        int nr = in.read(b);
        if (nr > 0) {
            nread += nr;
            updateSeriesProgression(nr);
        }
        return nr;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (isLoadingSeriesCanceled()) {
            InterruptedIOException exc = new InterruptedIOException("progress"); //$NON-NLS-1$
            exc.bytesTransferred = nread;
            series.setFileSize(series.getFileSize() - nread);
            nread = 0;
            throw exc;
        }
        int nr = in.read(b, off, len);
        if (nr > 0) {
            nread += nr;
            updateSeriesProgression(nr);
        }
        return nr;
    }

    @Override
    public long skip(long n) throws IOException {
        long nr = in.skip(n);
        if (nr > 0) {
            nread += nr;
            updateSeriesProgression(nr);
        }
        return nr;
    }

    @Override
    public synchronized void reset() throws IOException {
        in.reset();
        nread = size - in.available();
        updateSeriesProgression(nread);
    }
}
