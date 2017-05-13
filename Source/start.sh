#!/bin/sh
if [ -e ~/.wcg/wcg.pid ]; then
    PID=`cat ~/.wcg/wcg.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    if [ $STATUS -eq 0 ]; then
        echo "Wcg server already running"
        exit 1
    fi
fi
mkdir -p ~/.wcg/
DIR=`dirname "$0"`
cd "${DIR}"
if [ -x jre/bin/java ]; then
    JAVA=./jre/bin/java
else
    JAVA=java
fi
nohup ${JAVA} -cp classes:lib/*:conf:addons/classes:addons/lib/* -Dwcg.runtime.mode=desktop wcg.Wcg > /dev/null 2>&1 &
echo $! > ~/.wcg/wcg.pid
cd - > /dev/null
