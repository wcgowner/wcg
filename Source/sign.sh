#!/bin/sh
java -cp "classes:lib/*:conf" wcg.tools.SignTransactionJSON $@
exit $?
