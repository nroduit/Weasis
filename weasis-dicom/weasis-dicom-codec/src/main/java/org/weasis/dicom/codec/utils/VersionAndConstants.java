/* Copyright (c) 2001-2012, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.utils;

import java.io.BufferedReader;
import java.io.InputStream; 
import java.io.InputStreamReader; 
import java.io.IOException; 


/**
 * <p>Various pre-defined constants for identifying this software.</p>
 *
 * @author	dclunie
 */
public class VersionAndConstants {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/dicom/VersionAndConstants.java,v 1.10 2012/04/04 18:42:17 dclunie Exp $";
	
	/***/
	public static final String softwareVersion = "001";	// must be [A-Z0-9_] and <= 4 chars else screws up ImplementationVersionName

	/***/
	public static final String implementationVersionName = "PIXELMEDJAVA"+softwareVersion;

	public static final String uidRoot = "1.3.6.1.4.1.5962";
	/***/
	public static final String uidQualifierForThisToolkit = "99";
	/***/
	public static final String uidQualifierForUIDGenerator = "1";
	/***/
	public static final String uidQualifierForImplementationClassUID = "2";
	/***/
	public static final String uidQualifierForInstanceCreatorUID = "3";
	/***/
	public static final String implementationClassUID = uidRoot+"."+uidQualifierForThisToolkit+"."+uidQualifierForImplementationClassUID;
	/***/
	public static final String instanceCreatorUID = uidRoot+"."+uidQualifierForThisToolkit+"."+uidQualifierForInstanceCreatorUID;

	public static final String releaseString = "General Release";
	
	/**
	 * <p>Get the date the package was built.</p>
	 *
	 * @return	 the build date
	 */
	public static String getBuildDate() {
		String buildDate = "";
		try {
			InputStream i = VersionAndConstants.class.getResourceAsStream("/BUILDDATE");	// absolute path does not always work (?)
			if (i == null) {
//System.err.println("VersionAndConstants.getBuildDate(): no absolute path ... try package relative ...");
				i = VersionAndConstants.class.getResourceAsStream("../../../BUILDDATE");	// assume package relative
			}
			buildDate = i == null ? "NOBUILDDATE" : (new BufferedReader(new InputStreamReader(i))).readLine();
//System.err.println("VersionAndConstants.getBuildDate(): = "+buildDate);
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
		}
		return buildDate;
	}
	
}
