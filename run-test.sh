#!/bin/bash

# Simple test runner script for Kotlin tests
# Usage: ./run-test.sh [test-class] [test-method]

if [ $# -eq 0 ]; then
    echo "Running all tests..."
    ./gradlew test
elif [ $# -eq 1 ]; then
    echo "Running test class: $1"
    ./gradlew test --tests "$1"
elif [ $# -eq 2 ]; then
    echo "Running test method: $1.$2"
    ./gradlew test --tests "$1.$2"
else
    echo "Usage: $0 [test-class] [test-method]"
    echo "Examples:"
    echo "  $0                                    # Run all tests"
    echo "  $0 KotlinExtensionsTest              # Run all tests in class"
    echo "  $0 KotlinExtensionsTest testParseCurrencyPositive  # Run specific method"
    exit 1
fi 