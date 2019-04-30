#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

su jenkins -c "mvn -B -T$LIMITS_CPU -s settings.xml -Dmaven.repo.local=/tmp/maven verify -P skip-unstable-ci,parallel-tests -Dzeebe.it.skip"
