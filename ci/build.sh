#!/bin/bash -x

set -eou pipefail

GRADLE_OPTS="-Duser.name=jenkins -Djava.io.tmpdir=/tmp -Dgradle.user.home=/tmp/geode/session/build-gradle-cache" \
 ./gradlew --no-daemon --refresh-dependencies --stacktrace clean build
