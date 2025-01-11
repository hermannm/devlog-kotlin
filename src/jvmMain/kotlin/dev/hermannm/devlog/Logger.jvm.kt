package dev.hermannm.devlog

import org.slf4j.LoggerFactory as Slf4jLoggerFactory

public actual typealias PlatformLogger = org.slf4j.Logger

internal actual fun getPlatformLogger(name: String): PlatformLogger {
  return Slf4jLoggerFactory.getLogger(name)
}
