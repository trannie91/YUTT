#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$JAR" ]; then
    exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

echo "Error: Neither gradle nor gradle-wrapper.jar was found." >&2
exit 1
