#!/bin/sh
if [ -e ~/.wcg/wcg.pid ]; then
    PID=`cat ~/.wcg/wcg.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    echo "stopping"
    while [ $STATUS -eq 0 ]; do
        kill `cat ~/.wcg/wcg.pid` > /dev/null
        sleep 5
        ps -p $PID > /dev/null
        STATUS=$?
    done
    rm -f ~/.wcg/wcg.pid
    echo "Wcg server stopped"
fi

