#!/bin/bash
# This script attempts to find an existing installation of Java that meets a minimum version
# requirement on a Linux machine.  If it is successful, it will export a JAVA_HOME environment
# variable that can be used by another calling script.
#
# Rewritten by Abel 'Akronix' Serrano Juste <akronix5@gmail.com>
#
# To specify the required version, set the REQUIRED_VERSION to the major version required, 
# e.g. 1.3, but not 1.3.1. Also, always set decimal value: e.g. 2.0, but not 2 or 2.
REQUIRED_TEXT_VERSION=1.8

# Transform the required version string into a number that can be used in comparisons
REQUIRED_VERSION_INTEGER=`echo $REQUIRED_TEXT_VERSION | sed -e 's/\./0/'`

# First, check if java is installed and accesible in $PATH. If so, get installed version.
JAVA_EXE=`which java`
if [ $JAVA_EXE ]
then
	INSTALLED_VERSION=`java -version 2>&1 | awk '/version [0-9]*/ {print $3;}'`
	echo "Found java $INSTALLED_VERSION"
	
	# Remove double quotes, replace first dot by 0 and remove the rest of the version string.
	INSTALLED_VERSION_INTEGER=`echo $INSTALLED_VERSION | sed -e 's/"//' -e 's/\./0/' -e 's/\..*//'`
else
	echo -e "Java was not detected in your system.\nPlease, tell your administrator to install Java >=$REQUIRED_TEXT_VERSION and to make sure it's accesible from the PATH."
	exit 3;
fi

if (( INSTALLED_VERSION_INTEGER < REQUIRED_VERSION_INTEGER ))
then
	echo "Your version of java is too low to run Weasis. Please update to $REQUIRED_TEXT_VERSION or higher"
	exit 4;
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
java -Xms64m -Xmx512m -Dgosh.args="-sc telnetd -p 17179 start" -Dweasis.portable.dir="$curPath" -classpath "$curPath/weasis/weasis-launcher.jar:$curPath/weasis/felix.jar:$curPath/weasis/substance.jar" org.weasis.launcher.WeasisLauncher \$dicom:get --portable ${userParameters[@]}
