#!/bin/bash

if ! command -v javac &> /dev/null || ! command -v java &> /dev/null
then
    echo "Java is not installed or not in PATH. Please install Java."
    exit 1
fi

echo "Compiling Java files..."
javac -d target src/main/java/org/cesium/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# List of test files
test_files=(
    "src/main/resources/LATest_BasicStream.ces"
    "src/main/resources/LATest_Boolean.ces"
    "src/main/resources/LATest_Conditional.ces"
    "src/main/resources/LATest_InvalidSensor.ces"
    "src/main/resources/LATest_OpenQuotes.ces"
)

all_tests_passed=true

echo "Running tests..."
for test_file in "${test_files[@]}"; do
    echo "Executing test: $test_file"
    java -cp target org.cesium.Main $test_file

    if [ $? -ne 0 ]; then
        echo "Test failed for $test_file"
        all_tests_passed=false
    else
        echo "Test passed for $test_file"
    fi

    echo ""
done

if [ "$all_tests_passed" = true ]; then
    echo "All tests passed successfully!"
else
    echo "Some tests expectedly failed. Please examine the logs above to see our error handling"
fi