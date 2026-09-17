#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== Running CementTrack Unit Test Suite ==="
cd /data/data/com.termux/files/home/bhpos

mkdir -p build/test-classes build/app-classes
kotlinc -cp "test-libs/coroutines.jar" \
  app/src/main/java/com/example/bhpos/domain/model/Models.kt \
  app/src/main/java/com/example/bhpos/data/repository/CementStockRepository.kt \
  app/src/main/java/com/example/bhpos/data/repository/CementStockRepositoryImpl.kt \
  app/src/main/java/com/example/bhpos/printer/EscPosSlipGenerator.kt \
  -d build/app-classes

kotlinc -cp "test-libs/junit.jar:test-libs/hamcrest.jar:test-libs/coroutines.jar:build/app-classes" \
  app/src/test/java/com/example/bhpos/CementStockRepositoryTest.kt \
  -d build/test-classes

java -cp "build/test-classes:build/app-classes:test-libs/junit.jar:test-libs/hamcrest.jar:test-libs/coroutines.jar:/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar" \
  org.junit.runner.JUnitCore com.example.bhpos.CementStockRepositoryTest

echo "=== All Tests Passed! ==="

