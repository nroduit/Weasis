
//
// This file is auto-generated. Please don't modify it!
//
package org.opencv.features2d;



// C++: class BFMatcher
//javadoc: BFMatcher
public class BFMatcher extends DescriptorMatcher {

    protected BFMatcher(long addr) { super(addr); }


    //
    // C++:   BFMatcher(int normType = NORM_L2, bool crossCheck = false)
    //

    //javadoc: BFMatcher::BFMatcher(normType, crossCheck)
    public   BFMatcher(int normType, boolean crossCheck)
    {
        
        super( BFMatcher_0(normType, crossCheck) );
        
        return;
    }

    //javadoc: BFMatcher::BFMatcher()
    public   BFMatcher()
    {
        
        super( BFMatcher_1() );
        
        return;
    }


    @Override
    protected void finalize() throws Throwable {
        delete(nativeObj);
    }



    // C++:   BFMatcher(int normType = NORM_L2, bool crossCheck = false)
    private static native long BFMatcher_0(int normType, boolean crossCheck);
    private static native long BFMatcher_1();

    // native support for java finalize()
    private static native void delete(long nativeObj);

}
