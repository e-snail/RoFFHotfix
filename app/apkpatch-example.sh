#!/bin/sh

./apkpatch.sh -f app-release-with-bug-fixed.apk -t app-release-with-bug.apk -o . -k sign.keystore -p roffhotfix -a roff -e roffhotfix
