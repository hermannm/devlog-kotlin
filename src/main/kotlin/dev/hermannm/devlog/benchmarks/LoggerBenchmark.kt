@file:Suppress("unused") // Used by benchmark runner

package dev.hermannm.devlog.benchmarks

import dev.hermannm.devlog.Logger
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.CompilerControl
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

open class LoggerBenchmark {
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  fun log(state: BenchmarkState) {
    state.log!!.info("Test")
  }
}

@State(Scope.Benchmark)
open class BenchmarkState {
  var log: Logger? = null
  // We disable System.out in our tests, as that's not the part of the logs we want to benchmark
  private var originalStdout: PrintStream? = null

  @Setup
  fun setup() {
    log = Logger {}

    originalStdout = System.out
    System.setOut(PrintStream(NoOpOutputStream()))
  }

  @TearDown
  fun teardown() {
    System.setOut(originalStdout)
  }
}

class NoOpOutputStream : OutputStream() {
  override fun write(b: Int) {}

  override fun write(b: ByteArray, off: Int, len: Int) {}

  override fun write(b: ByteArray) {}

  override fun flush() {}

  override fun close() {}
}
