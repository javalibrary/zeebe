#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

su jenkins -c "mvn -B -T2 -s settings.xml -Dmaven.repo.local=/tmp/maven -DskipTests clean com.mycila:license-maven-plugin:check com.coveo:fmt-maven-plugin:check install -Pspotbugs"

# duplicate maven repository for qa tests
su jenkins -c "cp -R /tmp/maven /tmp/maven-it"
