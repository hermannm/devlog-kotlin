@file:JvmName("LoggerJvm")
@file:Suppress(
    // We want `getLogger()` to be inline, so that `lookupClass()` is called in the caller's scope
    "NOTHING_TO_INLINE",
    // The `expect` declaration of the `PlatformLogger` interface is annotated with `@PublishedApi`,
    // so we can use it in inline methods. Kotlin warns us that the `actual` definition should have
    // the same annotations, but here we use a `typealias`, which doesn't work with `@PublishedApi`.
    // But this is fine here, because we typealias `PlatformLogger` to the public SLF4J Logger,
    // which works like `@PublishedApi`.
    "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT",
)

package dev.hermannm.devlog

import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory as Slf4jLoggerFactory

internal actual typealias PlatformLogger = org.slf4j.Logger

public actual inline fun getLogger(): Logger {
  // `MethodHandles.lookup().lookupClass()` returns the calling class. Since this function is
  // inline, that will actually return the class that called `getLogger`, so we can use it to get
  // the name of the caller. When called at file scope, the calling class will be the synthetic `Kt`
  // class that Kotlin generates for the file, so we can use the file name in that case.
  //
  // This is the pattern that SLF4J recommends for instantiating loggers in a generic manner:
  // https://www.slf4j.org/faq.html#declaration_pattern
  return getLogger(javaClass = MethodHandles.lookup().lookupClass())
}

@PublishedApi
internal fun getLogger(javaClass: Class<*>): Logger {
  val name = normalizeLoggerName(javaClass.name)
  return Logger(Slf4jLoggerFactory.getLogger(name))
}

public actual fun getLogger(forClass: KClass<*>): Logger {
  val name = normalizeLoggerName(forClass.qualifiedName)
  return Logger(Slf4jLoggerFactory.getLogger(name))
}

public actual fun getLogger(name: String): Logger {
  return Logger(Slf4jLoggerFactory.getLogger(name))
}
