#!/bin/bash
# Runs both reproducers on the JDK in JAVA_HOME with the default JIT, with AVX2 only, and with C2.
set -u
JAVA="${JAVA_HOME:?Set JAVA_HOME}/bin/java"
cd "$(dirname "$0")"

"$JAVA" -version 2>&1
echo

status=0
for variant in "" "-XX:UseAVX=2" "-XX:-UseJVMCICompiler"; do
    for repro in Repro.java full-fft/FftRepro.java; do
        echo "=== $repro ${variant:-(default)}"
        # shellcheck disable=SC2086
        "$JAVA" $variant --add-modules jdk.incubator.vector "$repro" 2>&1 | grep -v '^WARNING'
        code=${PIPESTATUS[0]}
        echo "exit code: $code"
        echo
        if [ "$code" -ne 0 ]; then
            status=1
        fi
    done
done
exit $status
