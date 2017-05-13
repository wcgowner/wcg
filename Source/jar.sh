#!/bin/sh
java -cp classes wcg.tools.ManifestGenerator
/bin/rm -f wcg.jar
jar cfm wcg.jar resource/wcg.manifest.mf -C classes . || exit 1
/bin/rm -f wcgservice.jar
jar cfm wcgservice.jar resource/wcgservice.manifest.mf -C classes . || exit 1

echo "jar files generated successfully"
