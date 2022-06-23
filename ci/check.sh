#!/bin/bash -x

set -eou pipefail

./gradlew --no-daemon clean

GRADLE_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Djava.io.tmpdir=/tmp" \
 ./gradlew check --no-daemon --refresh-dependencies --stacktrace
