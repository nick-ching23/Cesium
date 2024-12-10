#!/usr/bin/env bash

# This script compiles a Cesium program and then runs it.
# Usage: ./run_cesium.sh <path-to-source.ces> <ProgramName>
#
# Requirements:
# - Maven and Java installed and accessible
# - ASM dependency is already resolved via Maven
# - The Cesium project with its pom.xml is in the current directory
#
# Example:
# ./run_cesium.sh src/main/resources/Reactivity.ces MyProgram

CESIUM_FILE=$1
PROGRAM_NAME=$2

if [ -z "$CESIUM_FILE" ] || [ -z "$PROGRAM_NAME" ]; then
    echo "Usage: $0 <path-to-ces-file> <program-name>"
    exit 1
fi

echo "Compiling project with Maven..."
mvn clean package
if [ $? -ne 0 ]; then
    echo "Maven build failed. Ensure Maven and Java are installed and check pom.xml."
    exit 1
fi

echo "Compiling Cesium source ($CESIUM_FILE) to Java bytecode..."
java -cp "target/Cesium-1.0-SNAPSHOT.jar:$HOME/.m2/repository/org/ow2/asm/asm/9.5/asm-9.5.jar:." \
     org.cesium.Main "$CESIUM_FILE" "$PROGRAM_NAME"
if [ $? -ne 0 ]; then
    echo "Cesium compilation failed."
    exit 1
fi

echo "Running generated program $PROGRAM_NAME..."
java -cp "target/Cesium-1.0-SNAPSHOT.jar:." "$PROGRAM_NAME"