#!/bin/sh
# Minimal POSIX launcher for the Gradle wrapper.
# Android Studio regenerates the full official wrapper on first sync;
# this shim is only a fallback for command-line use on Linux/macOS.

DIRNAME=$(cd "$(dirname "$0")" && pwd) || exit 1
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVACMD=java
else
    echo "ERROR: JAVA_HOME is not set and no 'java' command was found in PATH." >&2
    exit 1
fi

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: gradle-wrapper.jar not found." >&2
    echo "Open the project in Android Studio (or run 'gradle wrapper --gradle-version 8.7') to generate it." >&2
    exit 1
fi

exec "$JAVACMD" -Dorg.gradle.appname="gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
