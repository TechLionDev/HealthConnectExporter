#!/bin/sh
DIRNAME=$(dirname "$0")
JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"
exec "${JAVA_HOME}/bin/java" -classpath "${DIRNAME}/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
