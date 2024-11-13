#!/bin/bash

CESIUM_PARSER="java -cp out org.cesium.Main"

TEST_FILES=(
    "parse_arithmetic.ces"
    "parse_badOperator.ces"
    "parse_FullProgram.ces"
    "parse_missingSemiColon.ces"
    "parse_unclosedParens.ces"
)

for FILE in "${TEST_FILES[@]}"; do
    echo "Testing $FILE..."
    OUTPUT=$($CESIUM_PARSER "src/main/resources/$FILE" 2>&1)
    EXIT_CODE=$?

    echo "Output for $FILE:"
    echo "$OUTPUT"

    echo "---------------------------------"
done