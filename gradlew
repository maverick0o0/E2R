#!/bin/sh

##############################################################################
# Gradle start up script for POSIX
##############################################################################

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Determine project directory
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"

# Check if gradle wrapper jar exists, if not download it
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -sL "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar" -o "$WRAPPER_JAR" 2>/dev/null || \
    wget -q "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar" -O "$WRAPPER_JAR"
fi

# Check jar file size (if download failed jar might be empty or small)
if [ ! -s "$WRAPPER_JAR" ] || [ "$(wc -c < "$WRAPPER_JAR")" -lt 50000 ]; then
    echo "Gradle wrapper jar download failed or incomplete!"
    echo "Attempting alternative download method..."
    curl -L "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -o /tmp/gradle.zip
    unzip -j /tmp/gradle.zip "gradle-8.5/lib/gradle-launcher-*.jar" -d "$APP_HOME/gradle/wrapper/"
    mv "$APP_HOME/gradle/wrapper/gradle-launcher-"*.jar "$WRAPPER_JAR" 2>/dev/null || true
fi

exec "$JAVACMD" -Xmx64m -Xms64m -jar "$WRAPPER_JAR" "$@"
