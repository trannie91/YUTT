#!/bin/sh
# Standard Gradle Wrapper Script
APP_HOME=`cd "`dirname "$0"`" > /dev/null && pwd`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    fi
fi

exec java "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
