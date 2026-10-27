#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# GRADLE_OPTS is intentionally word-split, as with Gradle's official wrapper.
# shellcheck disable=SC2086
exec java ${GRADLE_OPTS:-} -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
