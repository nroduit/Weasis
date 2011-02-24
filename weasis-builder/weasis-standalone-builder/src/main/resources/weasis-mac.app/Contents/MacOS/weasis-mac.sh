#!/bin/bash

wdirpath=`pwd`
execpath="$0"

if [ "$wdirpath" = "/" ] ; then
	fullpath="$execpath"
else
	fullpath="$wdirpath/$execpath"
fi

#echo "$fullpath"

macospath=`dirname $fullpath`
contentspath=`dirname $macospath`
resourcespath="$contentspath/Resources"
basepath="$contentspath"
for i in {1..2} ; do
	basepath=`dirname $basepath`
done
#echo "$basepath"

# JAVA_HOME is often not in env

if [ -z "$JAVA_HOME" ] ; then
	export JAVA_HOME='/System/Library/Frameworks/JavaVM.framework/Home/'
fi

# launch

$JAVA_HOME/bin/java -Xms64m -Xmx512m -Xdock:name=Weasis -Xdock:icon="$resourcespath/logo-button.icns" -Dapple.laf.useScreenMenuBar=true -Dgosh.args="-sc telnetd -p 17179 start" -Djava.ext.dirs="" -Dweasis.codebase.url="file://$basepath/weasis" -classpath "$basepath/weasis/bin/weasis-launcher.jar:$basepath/weasis/bin/felix.jar:$basepath/weasis/bin/substance.jar" org.weasis.launcher.WeasisLauncher \$dicom:get -l "$basepath/DICOM"
