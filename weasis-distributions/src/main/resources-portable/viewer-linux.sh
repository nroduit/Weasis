#!/bin/bash
# This script attempts to find an existing installation of Java that meets a minimum version
# requirement on a Linux machine.  If it is successful, it will export a JAVA_HOME environment
# variable that can be used by another calling script.
#
# Rewritten by Abel 'Akronix' Serrano Juste <akronix5@gmail.com>
#
# To specify the required version, set the REQUIRED_VERSION to the major version required, 
# e.g. 1.3, but not 1.3.1. Also, always set decimal value: e.g. 9.0, but not 9 or 9.
REQUIRED_TEXT_VERSION=1.8

# Transform the required version string into a number that can be used in comparisons
REQUIRED_VERSION_INTEGER=`echo $REQUIRED_TEXT_VERSION | sed -e 's/\./0/'`

# Aux functions:
die ( ) {
	echo
	echo -e "ERROR: $*"
	exit 1
}

# First, determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
	if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
		# IBM's JDK on AIX uses strange locations for the executables
		JAVACMD="$JAVA_HOME/jre/sh/java"
	else
		JAVACMD="$JAVA_HOME/bin/java"
	fi
	if [ ! -x "$JAVACMD" ] ; then
		die "JAVA_HOME is set to an invalid directory: $JAVA_HOME
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
	fi
else
	which java >/dev/null 2>&1 || die "JAVA_HOME is not set and no 'java' command could be found in your PATH.
Please, tell your administrator to install Java >=$REQUIRED_TEXT_VERSION.
Also, make sure it's accesible from the PATH or set the JAVA_HOME variable to match the location of your Java installation."
	JAVACMD="java"
fi

# Then, get the installed version
INSTALLED_VERSION=`$JAVACMD -version 2>&1 | awk '/version [0-9]*/ {print $3;}'`
echo "Found java $INSTALLED_VERSION"

# Remove double quotes, replace first dot by 0 and remove the rest of the version string.
INSTALLED_VERSION_INTEGER=`echo $INSTALLED_VERSION | sed -e 's/"//' -e 's/\./0/' -e 's/\..*//'`

if (( INSTALLED_VERSION_INTEGER < REQUIRED_VERSION_INTEGER ))
then
	die "Your version of java is too low to run Weasis.\nPlease update to $REQUIRED_TEXT_VERSION or higher"
fi

# Get additional weasis arguments
userParameters=()
for var in "$@"
do
if [[ $var == \$* ]]
then
  userParameters+=("$var")
fi
done
echo user arguments: ${userParameters[@]}

# If the correct Java version is detected, launch weasis from current path
curPath=$(dirname "`readlink -f "$0"`")
echo "Weasis launcher directory: $curPath"
$JAVACMD -Xms64m -Xmx512m -Dgosh.args="-sc telnetd -p 17179 start" -Dweasis.portable.dir="$curPath" -classpath "$curPath/weasis/weasis-launcher.jar:$curPath/weasis/felix.jar:$curPath/weasis/substance.jar" org.weasis.launcher.WeasisLauncher \$dicom:get --portable ${userParameters[@]}
