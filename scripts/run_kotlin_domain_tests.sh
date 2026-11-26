#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
JAVA_BIN="$JAVA_HOME/bin/java"
KOTLIN_CLI_DIR="${PICO_HOME:-/Volumes/Extra/pico-sdk}/kotlin-cli"

if [ ! -d "$KOTLIN_CLI_DIR" ]; then
  echo "ERROR: Kotlin CLI jars not found at $KOTLIN_CLI_DIR" >&2
  exit 1
fi

BUILD_DIR="$ROOT_DIR/.kotlin-domain-test-build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/main" "$BUILD_DIR/test"

echo "[1/3] Compiling Kotlin domain model & spatial engines..."
"$JAVA_BIN" -cp "$KOTLIN_CLI_DIR/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib \
  -jvm-target 17 \
  -classpath "$KOTLIN_CLI_DIR/kotlin-stdlib-2.1.20.jar:$KOTLIN_CLI_DIR/kotlinx-coroutines-core-jvm-1.9.0.jar" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/model/InkDeskModels.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/domain/MortiseAssemblyEngine.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/domain/SpatialAnchorEngine.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/spatial/SpatialPanelLayoutEngine.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/spatial/ExplodedModelController.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/spatial/SpatialAudioEngine.kt" \
  "$ROOT_DIR/app/src/main/java/com/elidot/inkdesk/spatial/SpatialSessionStore.kt" \
  -d "$BUILD_DIR/main"

echo "[2/3] Compiling JUnit 4 spatial test suite..."
"$JAVA_BIN" -cp "$KOTLIN_CLI_DIR/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib \
  -jvm-target 17 \
  -classpath "$BUILD_DIR/main:$KOTLIN_CLI_DIR/kotlin-stdlib-2.1.20.jar:$KOTLIN_CLI_DIR/junit-4.13.2.jar:$KOTLIN_CLI_DIR/hamcrest-core-1.3.jar" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/domain/MortiseAssemblyEngineTest.kt" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/domain/SpatialAnchorEngineTest.kt" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/spatial/SpatialPanelLayoutEngineTest.kt" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/spatial/ExplodedModelControllerTest.kt" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/spatial/SpatialAudioEngineTest.kt" \
  "$ROOT_DIR/app/src/test/java/com/elidot/inkdesk/spatial/SpatialSessionStoreTest.kt" \
  -d "$BUILD_DIR/test"

echo "[3/3] Executing JUnit 4 tests on JDK 17..."
"$JAVA_BIN" -cp "$BUILD_DIR/main:$BUILD_DIR/test:$KOTLIN_CLI_DIR/kotlin-stdlib-2.1.20.jar:$KOTLIN_CLI_DIR/junit-4.13.2.jar:$KOTLIN_CLI_DIR/hamcrest-core-1.3.jar" \
  org.junit.runner.JUnitCore \
  com.elidot.inkdesk.domain.MortiseAssemblyEngineTest \
  com.elidot.inkdesk.domain.SpatialAnchorEngineTest \
  com.elidot.inkdesk.spatial.SpatialPanelLayoutEngineTest \
  com.elidot.inkdesk.spatial.ExplodedModelControllerTest \
  com.elidot.inkdesk.spatial.SpatialAudioEngineTest \
  com.elidot.inkdesk.spatial.SpatialSessionStoreTest

rm -rf "$BUILD_DIR"
