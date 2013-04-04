#!/bin/bash
# This script attempts to find an existing installation of Java that meets a minimum version
# requirement on a Linux machine.  If it is successful, it will export a JAVA_HOME environment
# variable that can be used by another calling script.
#
# To specify the required version, set the REQUIRED_VERSION to the major version required, 
# e.g. 1.3, but not 1.3.1.
REQUIRED_TEXT_VERSION=1.6

# Transform the required version string into a number that can be used in comparisons
REQUIRED_VERSION=`echo $REQUIRED_TEXT_VERSION | sed -e 's;\.;0;g'`
# Check JAVA_HOME directory to see if Java version is adequate
if [ $JAVA_HOME ]
then
	JAVA_EXE=$JAVA_HOME/bin/java
	VERSION=`$JAVA_EXE -version 2>&1 | head -1`
	VERSION=`echo $VERSION | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`
	VERSION=`echo $VERSION | awk '{ print substr($1, 1, 3); }' | sed -e 's;\.;0;g'`
	if [ $VERSION ]
	then
		if [ $VERSION -ge $REQUIRED_VERSION ]
		then
			JAVA_HOME=`echo $JAVA_EXE | awk '{ print substr($1, 1, length($1)-9); }'`
		else
			JAVA_HOME=
		fi
	else
		JAVA_HOME=
	fi
fi

# If the existing JAVA_HOME directory is adequate, then leave it alone
# otherwise, use 'locate' to search for other possible java candidates and
# check their versions.
if [ $JAVA_HOME ]
then
	:
else
	for JAVA_EXE in `locate bin/java | grep java$ | xargs echo`
	do
		if [ $JAVA_HOME ] 
		then
			:
		else
			VERSION=`$JAVA_EXE -version 2>&1 | head -1`
			VERSION=`echo $VERSION | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`
			VERSION=`echo $VERSION | awk '{ print substr($1, 1, 3); }' | sed -e 's;\.;0;g'`
			if [ $VERSION ]
			then
				if [ $VERSION -ge $REQUIRED_VERSION ]
				then
					JAVA_HOME=`echo $JAVA_EXE | awk '{ print substr($1, 1, length($1)-9); }'`
				fi
			fi
		fi
	done
fi

# Get additional weasis arguments
userParameters=()
for var in "$@"
do
if  [[ $var == \$* ]]
then
    userParameters+=("$var")
fi
done
echo user arguments: ${userParameters[@]}

# If the correct Java version is detected, then export the JAVA_HOME environment variable
if [ $JAVA_HOME ]
then
	export JAVA_HOME
	echo Java Home: $JAVA_HOME/bin/java
	curPath=$(dirname "`readlink -f "$0"`")
	echo Weasis launcher directory: $curPath
	$JAVA_HOME/bin/java -Xms64m -Xmx512m -Dgosh.args="-sc telnetd -p 17179 start" -Dweasis.portable.dir="$curPath" -classpath "$curPath/weasis/weasis-launcher.jar:$curPath/weasis/felix.jar:$curPath/weasis/substance.jar" org.weasis.launcher.WeasisLauncher \$dicom:get --portable ${userParameters[@]}
else echo 'Weasis requires Java Runtime '$REQUIRED_TEXT_VERSION' or higher, please install it'
fi
