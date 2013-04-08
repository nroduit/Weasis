#!/bin/bash

function real_path {
  OIFS=$IFS
  IFS='/'
  for I in $1
  do
    # Resolve relative path punctuation.
    if [ "$I" = "." ] || [ -z "$I" ]
      then continue
    elif [ "$I" = ".." ]
      then FOO="${FOO%%/${FOO##*/}}"
           continue
      else FOO="${FOO}/${I}"
    fi

    # Dereference symbolic links.
    if [ -h "$FOO" ] && [ -x "/bin/ls" ]
      then IFS=$OIFS
           set `/bin/ls -l "$FOO"`
           while shift ;
           do
             if [ "$1" = "->" ]
               then FOO=$2
                    shift $#
                    break
             fi
           done
    fi
  done
  IFS=$OIFS
  echo "$FOO"
}

fullpath=`pwd -P``real_path "$0"`
echo "Launcher Path: $fullpath"

macospath=`dirname "$fullpath"`
contentspath=`dirname "$macospath"`
resourcespath="$contentspath/Resources"
basepath="$contentspath"
for i in {1..2} ; do
	basepath=`dirname "$basepath"`
done
echo "Base Path: $basepath"

# JAVA_HOME is often not in env
if [ -z "$JAVA_HOME" ] ; then
	if [ -e '/usr/libexec/java_home' ] ; then
		export JAVA_HOME=`/usr/libexec/java_home`
	else
		export JAVA_HOME='/System/Library/Frameworks/JavaVM.framework/Home'
	fi
fi




function jversion {
	if [ -e "$1/bin/java" ] ; then
		jversion="`$1/bin/java -version 2>&1 | head -1`"
		jversion=`expr "$jversion" : '.*\"\(.*\)'\".*`
	else
		jversion=0
	fi
}

function isJava6orBetter {
	jversion $1
	
	if [ $jversion != 0 ] ; then
		echo "Java $jversion at $1"
		IFS="."; declare -a jversion=($jversion); unset IFS
		if [ "${jversion[0]}" -gt $2 ] || [ "${jversion[0]}" -eq $2 ] && [ "${jversion[1]}" -ge $3 ] ; then
			return 1
		fi
	else
		echo "Nothing at $1/bin/java"
	fi
	
	return 0
}

for i in {1..2} ; do
	isJava6orBetter $JAVA_HOME 1 6
	
	if [ $? != 1 ] ; then
		if [ $i == 1 ] ; then
			if [ $jversion != 0 ] ; then
				echo "JAVA_HOME points to an installation of Java $jversion. Looking for other available installations..."
			else
				echo "JAVA_HOME points to an unavailable installation of Java. Looking for other available installations..."
			fi
			# JAVA_HOME is less than 1.6, whats the highest version available?
			versions="`ls -r /System/Library/Frameworks/JavaVM.framework/Versions | grep '[0-9]'`"
			declare -a versions=($versions)
			# versions array contains version numbers highest to lowest, so pick the 1st
			export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/${versions[0]}/Home"
		else
			echo "The Weasis launcher was unable to find an installation of Java 1.6 or better."
			open "$macospath/failure.app"
			exit 0
		fi
	else
		break
	fi
done

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
echo "Launching Weasis with JAVA_HOME set to $JAVA_HOME"

# launch

$JAVA_HOME/bin/java -Xms64m -Xmx512m -Xdock:name=Weasis -Xdock:icon="$resourcespath/logo-button.icns" -Dapple.laf.useScreenMenuBar=true -Dgosh.args="-sc telnetd -p 17179 start" -Dweasis.portable.dir="$basepath" -classpath "$basepath/weasis/weasis-launcher.jar:$basepath/weasis/felix.jar:$basepath/weasis/substance.jar" org.weasis.launcher.WeasisLauncher \$dicom:get --portable ${userParameters[@]}
