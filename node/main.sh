#!/usr/bin/env sh

java -Dfile.encoding=UTF8 -Djava.net.preferIPv4Stack=true -jar $RHONIX_TARGET_JAR "$@"
