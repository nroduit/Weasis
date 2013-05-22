/* Copyright (c) 2001-2003, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.utils;

/**
 * @author	dclunie
 */
public class DicomException extends Exception {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/dicom/DicomException.java,v 1.4 2003/02/23 14:42:08 dclunie Exp $";

	/**
	 * @param	msg
	 */
	public DicomException(String msg) {
		super(msg);
	}
}


