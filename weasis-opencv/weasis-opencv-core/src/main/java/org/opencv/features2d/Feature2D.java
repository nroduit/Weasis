
//
// This file is auto-generated. Please don't modify it!
//
package org.opencv.features2d;

import java.util.ArrayList;
import org.opencv.core.Algorithm;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;

// C++: class Feature2D
//javadoc: Feature2D
public class Feature2D extends Algorithm {

    protected Feature2D(long addr) { super(addr); }


    //
    // C++:  bool empty()
    //

    //javadoc: Feature2D::empty()
    public  boolean empty()
    {
        
        boolean retVal = empty_0(nativeObj);
        
        return retVal;
    }


    //
    // C++:  int defaultNorm()
    //

    //javadoc: Feature2D::defaultNorm()
    public  int defaultNorm()
    {
        
        int retVal = defaultNorm_0(nativeObj);
        
        return retVal;
    }


    //
    // C++:  int descriptorSize()
    //

    //javadoc: Feature2D::descriptorSize()
    public  int descriptorSize()
    {
        
        int retVal = descriptorSize_0(nativeObj);
        
        return retVal;
    }


    //
    // C++:  int descriptorType()
    //

    //javadoc: Feature2D::descriptorType()
    public  int descriptorType()
    {
        
        int retVal = descriptorType_0(nativeObj);
        
        return retVal;
    }


    //
    // C++:  void compute(Mat image, vector_KeyPoint& keypoints, Mat& descriptors)
    //

    //javadoc: Feature2D::compute(image, keypoints, descriptors)
    public  void compute(Mat image, MatOfKeyPoint keypoints, Mat descriptors)
    {
        Mat keypoints_mat = keypoints;
        compute_0(nativeObj, image.nativeObj, keypoints_mat.nativeObj, descriptors.nativeObj);
        
        return;
    }


    //
    // C++:  void detect(Mat image, vector_KeyPoint& keypoints, Mat mask = Mat())
    //

    //javadoc: Feature2D::detect(image, keypoints, mask)
    public  void detect(Mat image, MatOfKeyPoint keypoints, Mat mask)
    {
        Mat keypoints_mat = keypoints;
        detect_0(nativeObj, image.nativeObj, keypoints_mat.nativeObj, mask.nativeObj);
        
        return;
    }

    //javadoc: Feature2D::detect(image, keypoints)
    public  void detect(Mat image, MatOfKeyPoint keypoints)
    {
        Mat keypoints_mat = keypoints;
        detect_1(nativeObj, image.nativeObj, keypoints_mat.nativeObj);
        
        return;
    }


    //
    // C++:  void detectAndCompute(Mat image, Mat mask, vector_KeyPoint& keypoints, Mat& descriptors, bool useProvidedKeypoints = false)
    //

    //javadoc: Feature2D::detectAndCompute(image, mask, keypoints, descriptors, useProvidedKeypoints)
    public  void detectAndCompute(Mat image, Mat mask, MatOfKeyPoint keypoints, Mat descriptors, boolean useProvidedKeypoints)
    {
        Mat keypoints_mat = keypoints;
        detectAndCompute_0(nativeObj, image.nativeObj, mask.nativeObj, keypoints_mat.nativeObj, descriptors.nativeObj, useProvidedKeypoints);
        
        return;
    }

    //javadoc: Feature2D::detectAndCompute(image, mask, keypoints, descriptors)
    public  void detectAndCompute(Mat image, Mat mask, MatOfKeyPoint keypoints, Mat descriptors)
    {
        Mat keypoints_mat = keypoints;
        detectAndCompute_1(nativeObj, image.nativeObj, mask.nativeObj, keypoints_mat.nativeObj, descriptors.nativeObj);
        
        return;
    }


    @Override
    protected void finalize() throws Throwable {
        delete(nativeObj);
    }



    // C++:  bool empty()
    private static native boolean empty_0(long nativeObj);

    // C++:  int defaultNorm()
    private static native int defaultNorm_0(long nativeObj);

    // C++:  int descriptorSize()
    private static native int descriptorSize_0(long nativeObj);

    // C++:  int descriptorType()
    private static native int descriptorType_0(long nativeObj);

    // C++:  void compute(Mat image, vector_KeyPoint& keypoints, Mat& descriptors)
    private static native void compute_0(long nativeObj, long image_nativeObj, long keypoints_mat_nativeObj, long descriptors_nativeObj);

    // C++:  void detect(Mat image, vector_KeyPoint& keypoints, Mat mask = Mat())
    private static native void detect_0(long nativeObj, long image_nativeObj, long keypoints_mat_nativeObj, long mask_nativeObj);
    private static native void detect_1(long nativeObj, long image_nativeObj, long keypoints_mat_nativeObj);

    // C++:  void detectAndCompute(Mat image, Mat mask, vector_KeyPoint& keypoints, Mat& descriptors, bool useProvidedKeypoints = false)
    private static native void detectAndCompute_0(long nativeObj, long image_nativeObj, long mask_nativeObj, long keypoints_mat_nativeObj, long descriptors_nativeObj, boolean useProvidedKeypoints);
    private static native void detectAndCompute_1(long nativeObj, long image_nativeObj, long mask_nativeObj, long keypoints_mat_nativeObj, long descriptors_nativeObj);

    // native support for java finalize()
    private static native void delete(long nativeObj);

}
