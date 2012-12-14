package org.dcm4che2.imageioimpl.plugins.dcm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.List;

import org.dcm4che2.data.DateRange;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.SpecificCharacterSet;
import org.dcm4che2.data.VR;
import org.dcm4che2.util.TagUtils;

/**
 * Indicates that a dicom element was skipped.  Writes back out as a zero length item.
 * 
 * @author bwallace
 *
 */
public class SkippedDicomElement implements DicomElement {

	private static final long serialVersionUID = -1L;
	
	protected static final int TO_STRING_MAX_VAL_LEN = 64;
    protected transient int tag;    
    protected transient VR vr;
    protected int realLength;
    protected long streamPosn;
    protected transient boolean bigEndian;
    protected volatile byte[] data = new byte[0];

    public SkippedDicomElement(int tag, VR vr, boolean bigEndian, int realLength, long streamPosn) {
        this.tag = tag;
        this.vr = vr;
        this.bigEndian = bigEndian;
        this.realLength = realLength;
        this.streamPosn = streamPosn;
    }

    public int hashCode() {
        return tag;
    }

    public int getRealLength() {
    	return realLength;    	
    }
    
    public long getStreamPosition() {
    	return streamPosn;
    }
    
    public final boolean bigEndian() {
        return bigEndian;
    }

    public final int tag() {
        return tag;
    }

    public final VR vr() {
        return vr;
    }
    
    /** Checks to see if the given DICOM object has any skipped elementss
     * and combined them into a list.  
     * @param ds
     * @param ret is null initially and a list is created as required
     * @return
     */
    public static List<SkippedDicomElement> checkHasSkippedElement(DicomObject ds, List<SkippedDicomElement> ret) {
    	Iterator<DicomElement> it = ds.iterator();
    	while(it.hasNext()) {
    		DicomElement de = it.next();
    		if( de instanceof SkippedDicomElement ) {
    			if( ret==null ) ret = new ArrayList<SkippedDicomElement>();
    			ret.add((SkippedDicomElement) de);
    		} else if( de.hasDicomObjects()) {
    			int n = de.countItems();
    			for(int i=0; i<n; i++) {
    				ret = checkHasSkippedElement(de.getDicomObject(i),ret);
    			}
    		}
    	}
    	return ret;
    }
    
    /** Reads the original data - returns the new position in the stream
     * so that subsequent items maybe read.
     */
    public long readOriginalData(InputStream is, long position) throws IOException {
    	byte[] origData = new byte[getRealLength()];
    	long skip = getStreamPosition()-position;
    	if( skip<0 ) {
    		throw new IOException("Already past position "+getStreamPosition());
    	}
    	long actualSkip = is.skip(skip);
    	if( actualSkip!=skip ) {
    		throw new IOException("Unable to skip "+skip+" bytes, actual "+actualSkip);
    	}
    	int n=0;
    	while( n<getRealLength() ) {
    		int cnt = is.read(origData,n,getRealLength()-n);
    		if( cnt==-1 ) break;
    		n+=cnt;
    	}
   		this.data = origData;
    	return position+skip+n;
    }
    
    @Override
    public String toString() {
        return toStringBuffer(null, TO_STRING_MAX_VAL_LEN).toString();
    }

    public StringBuffer toStringBuffer(StringBuffer sb, int maxValLen) {
        if (sb == null)
            sb = new StringBuffer();
        TagUtils.toStringBuffer(tag, sb);
        sb.append(' ');
        sb.append(vr);
        sb.append(" #");
        sb.append(length());
        sb.append(" [");
        sb.append("]");
        return sb;
    }

    public DicomElement bigEndian(boolean bigEndian) {
        if (this.bigEndian == bigEndian)
            return this;
        this.bigEndian = bigEndian;
        return this;
    }

	public DicomObject addDicomObject(DicomObject item) {
		throw new UnsupportedOperationException();
	}

	public DicomObject addDicomObject(int index, DicomObject item) {
		throw new UnsupportedOperationException();
	}

	public byte[] addFragment(byte[] b) {
		throw new UnsupportedOperationException();
	}

	public byte[] addFragment(int index, byte[] b) {
		throw new UnsupportedOperationException();
	}

	public int countItems() {
		return 0;
	}

	public DicomElement filterItems(DicomObject filter) {
		throw new UnsupportedOperationException();
	}

	public byte[] getBytes() {
		return data;
	}

	public Date getDate(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public DateRange getDateRange(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public Date[] getDates(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public DicomObject getDicomObject() {
		throw new UnsupportedOperationException();
	}

	public DicomObject getDicomObject(int index) {
		throw new UnsupportedOperationException();
	}

	public double getDouble(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public double[] getDoubles(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public float getFloat(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public float[] getFloats(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public byte[] getFragment(int index) {
		throw new UnsupportedOperationException();
	}

	public int getInt(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public int[] getInts(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public Pattern getPattern(SpecificCharacterSet cs, boolean ignoreCase,
			boolean cache) {
		throw new UnsupportedOperationException();
	}

	public short[] getShorts(boolean cache) {
		throw new UnsupportedOperationException();
	}

	public String getString(SpecificCharacterSet cs, boolean cache) {
		throw new UnsupportedOperationException();
	}

	public String[] getStrings(SpecificCharacterSet cs, boolean cache) {
		throw new UnsupportedOperationException();
	}

	public String getValueAsString(SpecificCharacterSet cs, int truncate) {
		throw new UnsupportedOperationException();
	}

	public boolean hasDicomObjects() {
		return false;
	}

	public boolean hasFragments() {
		return false;
	}

	public boolean hasItems() {
		return false;
	}

	public boolean isEmpty() {
		return true;
	}

	public int length() {
		return data.length;
	}

	public DicomObject removeDicomObject(int index) {
		throw new UnsupportedOperationException();
	}

	public boolean removeDicomObject(DicomObject item) {
		throw new UnsupportedOperationException();
	}

	public byte[] removeFragment(int index) {
		throw new UnsupportedOperationException();
	}

	public boolean removeFragment(byte[] b) {
		throw new UnsupportedOperationException();
	}

	public DicomObject setDicomObject(int index, DicomObject item) {
		throw new UnsupportedOperationException();
	}

	public byte[] setFragment(int index, byte[] b) {
		throw new UnsupportedOperationException();
	}

	public DicomElement share() {
		throw new UnsupportedOperationException();
	}

	public int vm(SpecificCharacterSet cs) {
		return data.length>0 ? 1 : 0;
	}

}
