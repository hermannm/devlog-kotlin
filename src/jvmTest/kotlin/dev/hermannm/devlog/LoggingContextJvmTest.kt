package dev.hermannm.devlog

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainExactly
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private val log = getLogger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LoggingContextJvmTest {
  @Test
  fun `withLoggingContextMap merges given map with existing fields`() {
    withLoggingContext(field("existingField", "value")) {
      LoggingContext shouldContainExactly mapOf("existingField" to "value")

      withLoggingContextMap(
          mapOf("fieldMap1" to "value", "fieldMap2" to "value"),
      ) {
        LoggingContext shouldContainExactly
            mapOf(
                "existingField" to "value",
                "fieldMap1" to "value",
                "fieldMap2" to "value",
            )
      }

      // Previous fields should be restored after
      LoggingContext shouldContainExactly mapOf("existingField" to "value")
    }
  }

  /**
   * [inheritLoggingContext] wraps an [ExecutorService], forwarding calls to the wrapped executor.
   * We want to verify that all these methods forward appropriately, so we make a test case for each
   * executor method, and use this as a [MethodSource] on our executor tests to run each test on
   * every executor method.
   */
  class ExecutorTestCase(
      private val name: String,
      /**
       * `invokeAll` and `invokeAny` on [ExecutorService] block the calling thread. This affects how
       * we run our tests, so we set this flag to true for those cases.
       */
      val isBlocking: Boolean = false,
      val runTask: (ExecutorService, () -> Unit) -> Unit,
  ) {
    override fun toString() = name
  }

  val executorTestCases =
      listOf(
          ExecutorTestCase("execute") { executor, task -> executor.execute(Runnable(task)) },
          ExecutorTestCase("submit with Callable") { executor, task ->
            executor.submit(Callable(task))
          },
          ExecutorTestCase("submit with Runnable") { executor, task ->
            executor.submit(Runnable(task))
          },
          ExecutorTestCase("submit with Runnable and result") { executor, task ->
            executor.submit(Runnable(task), "Result")
          },
          ExecutorTestCase("invokeAll", isBlocking = true) { executor, task ->
            executor.invokeAll(listOf(Callable(task)))
          },
          ExecutorTestCase("invokeAll with timeout", isBlocking = true) { executor, task ->
            executor.invokeAll(listOf(Callable(task)), 1, TimeUnit.MINUTES)
          },
          ExecutorTestCase("invokeAny", isBlocking = true) { executor, task ->
            executor.invokeAny(listOf(Callable(task)))
          },
          ExecutorTestCase("invokeAny with timeout", isBlocking = true) { executor, task ->
            executor.invokeAny(listOf(Callable(task)), 1, TimeUnit.MINUTES)
          },
      )

  @ParameterizedTest
  @MethodSource("getExecutorTestCases")
  fun `ExecutorService with inheritLoggingContext allows passing logging context between threads`(
      test: ExecutorTestCase
  ) {
    val executor = Executors.newSingleThreadExecutor().inheritLoggingContext()
    val lock = ReentrantLock()
    val latch = CountDownLatch(1) // Used to wait for the child thread to complete its log

    val output = captureLogOutput {
      // Aquire a lock around the outer withLoggingContext in the parent thread, to test that
      // the logging context works in the child thread even when the outer context has exited.
      // Only relevant if the executor method is non-blocking (see ExecutorTestCase.isBlocking).
      lock.conditionallyLock(!test.isBlocking) {
        withLoggingContext(field("fieldFromParentThread", "value")) {
          test.runTask(executor) {
            // Acquire the lock here in the child thread - this will block until the outer
            // logging context has exited
            lock.conditionallyLock(!test.isBlocking) { log.error { "Test" } }
            latch.countDown()
          }
        }
      }

      latch.await() // Waits until child thread calls countDown()
    }

    output.contextFields shouldContainExactly
        mapOf("fieldFromParentThread" to JsonPrimitive("value"))
  }

  /**
   * In [ExecutorServiceWithInheritedLoggingContext], we only call [withLoggingContextInternal] if
   * there are fields in the logging context. Otherwise, we just forward the tasks directly - we
   * want to test that that works.
   */
  @ParameterizedTest
  @MethodSource("getExecutorTestCases")
  fun `ExecutorService with inheritLoggingContext works when there are no fields in the context`(
      test: ExecutorTestCase
  ) {
    val executor = Executors.newSingleThreadExecutor().inheritLoggingContext()

    // Verify that there are no fields in parent thread context
    LoggingContext.shouldBeEmpty()

    val latch = CountDownLatch(1) // Used to wait for the child thread to complete its log
    val executed = AtomicBoolean(false)

    test.runTask(executor) {
      // Verify that there are no fields in child thread context
      LoggingContext.shouldBeEmpty()
      executed.set(true)
      latch.countDown()
    }

    latch.await()
    executed.get().shouldBeTrue()
  }

  @Test
  fun `ExecutorService with inheritLoggingContext does not affect parent thread context`() {
    val executor = Executors.newSingleThreadExecutor().inheritLoggingContext()

    // Used for synchronization points between the two threads in our test
    val barrier = CyclicBarrier(2)

    val parentField = field("parentField", "value")
    val childField = field("childField", "value")

    withLoggingContext(parentField) {
      LoggingContext shouldContainExactly mapOf("parentField" to "value")

      executor.execute {
        LoggingContext shouldContainExactly mapOf("parentField" to "value")

        withLoggingContext(childField) {
          LoggingContext shouldContainExactly
              mapOf("parentField" to "value", "childField" to "value")

          // 1st synchronization point: The parent thread will reach this after we've added to the
          // logging context here in the child thread, so we can verify that the parent's context is
          // unchanged
          barrier.await()
          // 2nd synchronization point: We want to keep this thread running while the parent thread
          // tests its logging context fields, to keep the child thread context alive
          barrier.await()
        }
      }

      barrier.await() // 1st synchronization point
      LoggingContext shouldContainExactly mapOf("parentField" to "value")
      barrier.await() // 2nd synchronization point
    }
  }
}
