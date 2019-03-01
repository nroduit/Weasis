#!/bin/bash
# Launcher for Mac
#
# Initial script by Nicolas Roduit

# Aux functions:
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
set $(/bin/ls -l "$FOO")
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

# Resolve binary path
fullpath=$(real_path "$0")
macospath=$(dirname "$fullpath")
echo "Launcher Path: $fullpath"
echo "Arguments: $@"

# Get additional weasis arguments
binaryCmd="Weasis"
for var in "$@"
do
if [[ $var == "weasis-dicomizer://"* ]]; then
binaryCmd="Dicomizer"
fi
done

"$macospath/$binaryCmd" $@

