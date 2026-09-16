#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Running CementTrack Unit Test Suite ==="
cd /data/data/com.termux/files/home/bhpos

java -cp "build/test-classes:test-libs/junit.jar:test-libs/hamcrest.jar:test-libs/coroutines.jar:/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar" \
  org.junit.runner.JUnitCore com.example.bhpos.CementStockRepositoryTest

echo "=== All Tests Passed! ==="
