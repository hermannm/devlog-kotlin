package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.Event
import dev.hermannm.devlog.testutils.EventType
import dev.hermannm.devlog.testutils.TestCase
import dev.hermannm.devlog.testutils.captureLogOutput
import dev.hermannm.devlog.testutils.parameterizedTest
import dev.hermannm.devlog.testutils.shouldBeEmpty
import dev.hermannm.devlog.testutils.shouldContainExactly
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainExactly
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val log = getLogger()

internal class LoggingContextJvmTest {
  @Test
  fun `nested logging context restores previous context fields on exit`() {
    val event1 = Event(id = 1001, type = EventType.ORDER_PLACED)
    val event2 = Event(id = 1002, type = EventType.ORDER_UPDATED)

    withLoggingContext(
        field("event", event1),
        field("stringField", "parentValue"),
        field("parentOnlyField", "value1"),
        field("fieldThatIsStringInParentButJsonInChild", "stringValue"),
    ) {
      val parentContext =
          mapOf(
              "event${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to """{"id":1001,"type":"ORDER_PLACED"}""",
              "stringField" to "parentValue",
              "parentOnlyField" to "value1",
              "fieldThatIsStringInParentButJsonInChild" to "stringValue",
          )
      LoggingContext shouldContainExactly parentContext

      withLoggingContext(
          field("event", event2),
          field("stringField", "childValue"),
          field("childOnlyField", "value2"),
          rawJsonField("fieldThatIsStringInParentButJsonInChild", """{"test":true}"""),
      ) {
        LoggingContext shouldContainExactly
            mapOf(
                "event${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to
                    """{"id":1002,"type":"ORDER_UPDATED"}""",
                "stringField" to "childValue",
                "parentOnlyField" to "value1",
                "childOnlyField" to "value2",
                "fieldThatIsStringInParentButJsonInChild${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to
                    """{"test":true}""",
            )
      }

      LoggingContext shouldContainExactly parentContext
    }
  }

  @Test
  fun `ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS has expected value`() {
    // Since we use LoggingContextJsonFieldWriter in tests, we expect this to be set
    ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS.shouldBeTrue()
  }

  /** We use JVM synchronization primitives here, hence we place it under jvmTest. */
  @Test
  fun `getLoggingContext allows passing logging context between threads`() {
    val event = Event(id = 1001, type = EventType.ORDER_PLACED)

    val lock = ReentrantLock()
    // Used to wait for the child thread to complete its log
    val latch = CountDownLatch(1)

    val output = captureLogOutput {
      // Aquire a lock around the outer withLoggingContext in the parent thread, to test that
      // the logging context works in the child thread even when the outer context has exited
      lock.withLock {
        withLoggingContext(field("event", event)) {
          // Get the parent logging context (the one we just entered)
          val loggingContext = getLoggingContext()

          thread {
            // Acquire the lock here in the child thread - this will block until the outer
            // logging context has exited
            lock.withLock {
              // Use the parent logging context here in the child thread
              withLoggingContext(loggingContext) { log.error { "Test" } }
              latch.countDown()
            }
          }
        }
      }

      latch.await() // Waits until completed
    }

    output.contextFields shouldContainExactly
        mapOf(
            "event" to
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(1001),
                        "type" to JsonPrimitive("ORDER_PLACED"),
                    ),
                ),
        )
  }

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
   * executor method, and run [parameterizedTest] in our executor tests to run each test on every
   * executor method.
   */
  class ExecutorTestCase(
      override val name: String,
      /**
       * `invokeAll` and `invokeAny` on [ExecutorService] block the calling thread. This affects how
       * we run our tests, so we set this flag to true for those cases.
       */
      val isBlocking: Boolean = false,
      val runTask: (ExecutorService, () -> Unit) -> Unit,
  ) : TestCase

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

  @Test
  fun `ExecutorService with inheritLoggingContext allows passing logging context between threads`() {
    parameterizedTest(executorTestCases) { test ->
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
  }

  /**
   * In [ExecutorServiceWithInheritedLoggingContext], we only call [withLoggingContextInternal] if
   * there are fields in the logging context. Otherwise, we just forward the tasks directly - we
   * want to test that that works.
   */
  @Test
  fun `ExecutorService with inheritLoggingContext works when there are no fields in the context`() {
    parameterizedTest(executorTestCases) { test ->
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

/**
 * Acquires the lock around the given block if the given condition is true - otherwise, just calls
 * the block directly.
 */
private inline fun Lock.conditionallyLock(condition: Boolean, block: () -> Unit) {
  if (condition) {
    this.withLock(block)
  } else {
    block()
  }
}
