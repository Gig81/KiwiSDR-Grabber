#!/usr/bin/env bash
# Copia il repository (montato in sola lettura su /src) in /work, esegue Gradle e
# riporta APK/AAB e report dei test in /out. Le cartelle build/ e .gradle/ dell'host
# non vengono mai toccate: host Windows e container non condividono cache.
set -euo pipefail

rsync -a --delete \
    --exclude '/build/' --exclude '*/build/' --exclude '/.gradle/' --exclude '/.kotlin/' \
    --exclude '/.idea/' --exclude '/local.properties' \
    /src/ /work/
sed -i 's/\r$//' /work/gradlew

cd /work
status=0
bash ./gradlew --no-daemon "$@" || status=$?

if [ -d /out ]; then
    rm -rf /out/apk /out/bundle /out/reports
    mkdir -p /out
    [ -d androidApp/build/outputs/apk ] && cp -r androidApp/build/outputs/apk /out/apk
    [ -d androidApp/build/outputs/bundle ] && cp -r androidApp/build/outputs/bundle /out/bundle
    mkdir -p /out/reports
    for dir in shared/build/reports androidApp/build/reports; do
        [ -d "$dir" ] && cp -r "$dir" "/out/reports/$(dirname "$dir" | cut -d/ -f1)"
    done
fi
exit $status
