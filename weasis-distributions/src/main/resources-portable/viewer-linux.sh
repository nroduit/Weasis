#!/bin/bash
# This script attempts to find an existing installation of Java that meets a minimum version
# requirement on a Linux machine.  If it is successful, it will export a JAVA_HOME environment
# variable that can be used by another calling script.
#
# Rewritten by Abel 'Akronix' Serrano Juste <akronix5@gmail.com>

# Specify the required JDK version.
# Only major version is checked. Minor version or any other version string info is left out.
REQUIRED_TEXT_VERSION=1.8

# JVM Options
DEFAULT_JVM_OPTIONS="-Xms64m -Xmx768m -Dgosh.args="
GOSH_ARGS="-sc telnetd -p 17179 start"

JAVA9_OPTIONS="--add-modules java.xml.bind --add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.file=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.ftp=ALL-UNNAMED --add-exports=java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens=java.desktop/javax.imageio=ALL-UNNAMED --add-opens=java.desktop/com.sun.awt=ALL-UNNAMED"

# Extract major version number for comparisons from the required version string.
# In order to do that, remove leading "1." if exists, and minor and security versions.
REQUIRED_MAJOR_VERSION=$(echo $REQUIRED_TEXT_VERSION | sed -e 's/^1\.//' -e 's/\..*//')

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
INSTALLED_VERSION=$($JAVACMD -version 2>&1 | awk '/version [0-9]*/ {print $3;}')
echo "Found java $INSTALLED_VERSION"

# Remove double quotes, remove leading "1." if it exists and remove everything apart from the major version number.
INSTALLED_MAJOR_VERSION=$(echo $INSTALLED_VERSION | sed -e 's/"//g' -e 's/^1\.//' -e 's/\..*//')

if (( INSTALLED_MAJOR_VERSION < REQUIRED_MAJOR_VERSION ))
then
	die "Your version of java is too low to run Weasis.\nPlease update to $REQUIRED_TEXT_VERSION or higher"
fi

if (( INSTALLED_MAJOR_VERSION >= 9 ))
then
	echo "add options for Java 9: $JAVA9_OPTIONS"
    DEFAULT_JVM_OPTIONS="$JAVA9_OPTIONS $DEFAULT_JVM_OPTIONS"
fi

# Get additional weasis arguments
userParameters=()
for var in "$@"
do
  userParameters+=("$var")
done
echo user arguments: ${userParameters[@]}

# If the correct Java version is detected, launch weasis from current path
curPath=$(dirname "$(readlink -f "$0")")
echo "Weasis launcher directory: $curPath"

cps="$curPath/weasis/weasis-launcher.jar:$curPath/weasis/felix.jar:$curPath/weasis/substance.jar:$curPath/weasis/google-demo.jar"

$JAVACMD $DEFAULT_JVM_OPTIONS"$GOSH_ARGS" -Dweasis.portable.dir="$curPath" -classpath "$cps" org.weasis.launcher.WeasisLauncher \$dicom:get --portable ${userParameters[@]}
