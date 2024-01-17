#!/bin/sh
MYSELF=`which "$0" 2>/dev/null`
if [ "$?" -gt 0 ]; then
	MYSELF="./$0"
fi

if [ -e $(dirname $0)/.ngsutilsjrc ]; then
    . $(dirname $0)/.ngsutilsjrc
fi
if [ -e $HOME/.ngsutilsjrc ]; then
    . $HOME/.ngsutilsjrc
fi

if [ ! -t 0 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd0=F"
fi

if [ ! -t 1 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd1=F"
fi

if [ ! -t 2 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd2=F"
fi

# limit MALLOC_ARENA to keep vmem under control on bigmem systems
if [ "$MALLOC_ARENA_MAX" = "" ]; then
    export MALLOC_ARENA_MAX=4
fi

# limit parallel gc threads (systems with lots of processors)
FOUNDGC=0
case "${JAVA_OPTS}" in
  *"XX:ParallelGCThreads"*)
  FOUNDGC=1
  ;;
esac

if [ $FOUNDGC -eq 0 ]; then
    JAVA_OPTS="${JAVA_OPTS} -XX:ParallelGCThreads=2"
fi

if [ "$TMPDIR" != "" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Djava.io.tmpdir=${TMPDIR}"
fi

JAVABIN=`which java`
if [ "${JAVA_HOME}" != "" ]; then
    JAVABIN="$JAVA_HOME/bin/java"
fi
exec "${JAVABIN}" ${JAVA_OPTS} -jar $0 "$@"
exit 1
