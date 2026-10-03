#!/bin/bash
# Manual APK build for WOTD Solver (no Gradle daemon needed).
# Toolchain: android-sdk (aapt2, d8, apksigner, zipalign), kotlinc-embeddable, jdk17.
set -e
source ~/android-sdk/env.sh

PROJ=~/workspace/wotdsolver
SDK=~/android-sdk
ANDROID_JAR=$SDK/platforms/android-35/android.jar
BT=$SDK/build-tools/34.0.0
KC=$SDK/gradle-8.7/lib/kotlin-compiler-embeddable-1.9.22.jar
STDLIB=~/workspace/localrepo/org/jetbrains/kotlin/kotlin-stdlib/1.9.22/kotlin-stdlib-1.9.22.jar
TROVE=$SDK/gradle-8.7/lib/trove4j-1.0.20200330.jar
ANNOT=~/workspace/localrepo/org/jetbrains/annotations/13.0/annotations-13.0.jar
KCCP="$KC:$STDLIB:$TROVE:$ANNOT"
R8JAR=$SDK/cmdline-tools/latest/lib/r8.jar
KEYSTORE=$PROJ/wotdsolver-release.keystore

mkdir -p $PROJ/build/{classes,dex,gen,res}

echo "== 1/7 aapt2 compile =="
$BT/aapt2 compile --dir $PROJ/res -o $PROJ/build/res.zip

echo "== 2/7 aapt2 link =="
$BT/aapt2 link -o $PROJ/build/unsigned.apk -I $ANDROID_JAR \
  --manifest $PROJ/AndroidManifest.xml \
  --java $PROJ/build/gen \
  --min-sdk-version 26 --target-sdk-version 35 \
  $PROJ/build/res.zip

echo "== 3/7 javac R.java =="
find $PROJ/build/gen -name "*.java" > $PROJ/build/gen/sources.txt
javac -cp $ANDROID_JAR -d $PROJ/build/classes @$PROJ/build/gen/sources.txt

echo "== 4/7 kotlinc =="
find $PROJ/src -name "*.kt" > $PROJ/build/kt-sources.txt
java -cp "$KCCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-reflect -jvm-target 1.8 \
  -classpath "$ANDROID_JAR:$STDLIB:$PROJ/build/classes" \
  -d $PROJ/build/classes @$PROJ/build/kt-sources.txt

echo "== 5/7 d8 =="
# d8 rejects raw class dirs, so jar them first
jar cf $PROJ/build/classes.jar -C $PROJ/build/classes .
java -cp $R8JAR com.android.tools.r8.D8 \
  --min-api 26 --lib $ANDROID_JAR \
  --output $PROJ/build/dex \
  $PROJ/build/classes.jar $STDLIB

echo "== 6/7 add classes.dex =="
python3 - "$PROJ/build/unsigned.apk" "$PROJ/build/dex/classes.dex" <<'EOF'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
data = open(dex, 'rb').read()
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.writestr('classes.dex', data)
print("classes.dex added:", len(data), "bytes")
EOF

echo "== 7/7 zipalign + sign =="
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias wotdsolver \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android \
    -dname "CN=Muhammad Taqi"
  echo "generated new keystore"
fi
$BT/zipalign -p -f 4 $PROJ/build/unsigned.apk $PROJ/build/aligned.apk
$BT/apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out $PROJ/WOTD-Solver.apk $PROJ/build/aligned.apk
$BT/apksigner verify --print-certs $PROJ/WOTD-Solver.apk | head -8

echo ""
echo "== verify =="
$BT/aapt2 dump badging $PROJ/WOTD-Solver.apk | head -6
ls -la $PROJ/WOTD-Solver.apk
echo "BUILD OK"
