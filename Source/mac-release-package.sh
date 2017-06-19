#!/bin/bash
VERSION=$1
if [ -x ${VERSION} ];
then
	echo VERSION not defined
	exit 1
fi
PACKAGE=wcg-client-${VERSION}
echo PACKAGE="${PACKAGE}"
CHANGELOG=wcg-client-${VERSION}.changelog.txt
OBFUSCATE=$2
MACVERSION=$3
if [ -x ${MACVERSION} ];
then
MACVERSION=${VERSION}
fi
echo MACVERSION="${MACVERSION}"

FILES="changelogs conf html lib resource contrib"
FILES="${FILES} wcg.exe wcgservice.exe"
FILES="${FILES} 3RD-PARTY-LICENSES.txt AUTHORS.txt LICENSE.txt"
FILES="${FILES} DEVELOPERS-GUIDE.md OPERATORS-GUIDE.md README.md README.txt USERS-GUIDE.md"
FILES="${FILES} mint.bat mint.sh run.bat run.sh run-tor.sh run-desktop.sh start.sh stop.sh compact.sh compact.bat sign.sh"
FILES="${FILES} wcg.policy wcgdesktop.policy WCG_Wallet.url Dockerfile"

echo compile
./compile.sh
rm -rf html/doc/*
rm -rf wcg
rm -rf ${PACKAGE}.jar
rm -rf ${PACKAGE}.exe
rm -rf ${PACKAGE}.zip
mkdir -p wcg/
mkdir -p wcg/logs
mkdir -p wcg/addons/src

if [ "${OBFUSCATE}" = "obfuscate" ]; 
then
echo obfuscate
~/proguard/proguard5.2.1/bin/proguard.sh @wcg.pro
mv ../wcg.map ../wcg.map.${VERSION}
else
FILES="${FILES} classes src COPYING.txt"
FILES="${FILES} compile.sh javadoc.sh jar.sh package.sh"
FILES="${FILES} win-compile.sh win-javadoc.sh win-package.sh"
echo javadoc
./javadoc.sh
fi
echo copy resources
cp installer/lib/JavaExe.exe wcg.exe
cp installer/lib/JavaExe.exe wcgservice.exe
cp -a ${FILES} wcg
cp -a logs/placeholder.txt wcg/logs
echo gzip
for f in `find wcg/html -name *.gz`
do
	rm -f "$f"
done
for f in `find wcg/html -name *.html -o -name *.js -o -name *.css -o -name *.json  -o -name *.ttf -o -name *.svg -o -name *.otf`
do
	gzip -9c "$f" > "$f".gz
done
cd wcg
echo generate jar files
../jar.sh

echo package installer Jar
../installer/build-installer.sh ../${PACKAGE}
cd -
rm -rf wcg

JAVA_HOME="$(/usr/libexec/java_home)"

echo bundle a dmg file	
"${JAVA_HOME}"/bin/javapackager -deploy -outdir . -outfile wcg-client -name wcg-installer -width 34 -height 43 -native dmg -srcfiles ${PACKAGE}.jar -appclass com.izforge.izpack.installer.bootstrap.Installer -v -Bmac.category=Business -Bmac.CFBundleIdentifier=org.wcg.client.installer -Bmac.CFBundleName=Wcg-Installer -Bmac.CFBundleVersion=${MACVERSION} -BappVersion=${MACVERSION} -Bicon=installer/AppIcon.icns > installer/javapackager.log 2>&1
