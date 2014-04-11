#!/bin/sh
MYSELF=`which "$0" 2>/dev/null`
if [ "$?" -gt 0 ]; then
	MYSELF="./$0"
fi

if [ -e $HOME/.ngsutilsjrc ]; then
	. $HOME/.ngsutilsjrc
fi
JAVABIN=`which java`
if [ "${JAVA_HOME}" != "" ]; then
    JAVABIN="$JAVA_HOME/bin/java"
fi
exec "${JAVABIN}" ${JAVA_OPTS} -jar $0 "$@"
exit 1