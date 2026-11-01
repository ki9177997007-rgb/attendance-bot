#!/bin/sh
set -eu
report=app/build/reports/androidTests
mkdir -p "$report"
trap 'adb logcat -d > "$report/device-logcat.txt"; adb shell dumpsys meminfo ru.secondmemory.assistant > "$report/final-memory.txt"' EXIT
gradle --no-daemon -PtestAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.class=ru.secondmemory.RealAnalysisTest connectedDebugAndroidTest
cp -R "$report/connected" "$report/real-qwen"
gradle --no-daemon -PtestAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.notClass=ru.secondmemory.RealAnalysisTest connectedDebugAndroidTest
