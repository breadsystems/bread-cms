#!/usr/bin/env bash

native_image_bin="$(readlink -f "$(command -v native-image)")"
GRAALVM_HOME=${GRAALVM_HOME:-$(dirname "$(dirname $native_image_bin)")}
OUT=./scratchpad

"$GRAALVM_HOME/bin/java" \
  -agentlib:native-image-agent=config-output-dir="$OUT",config-write-period-secs=3 \
  -cp "$(clojure -Spath -M:cms:dev:tools):classes" \
  systems.bread.alpha.dev.main -f dev/main.edn -v debug
