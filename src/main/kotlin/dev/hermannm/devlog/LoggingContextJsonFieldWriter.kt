package dev.hermannm.devlog

import com.fasterxml.jackson.core.JsonGenerator
import net.logstash.logback.composite.loggingevent.mdc.MdcEntryWriter

/**
 * Writes logging context fields as JSON when using `logstash-logback-encoder`. We need this in
 * order to include object fields as raw JSON instead of escaped strings on the log output (see
 * [LoggingContext.JSON_FIELD_VALUE_PREFIX]).
 *
 * To use it, configure `logback.xml` under `src/main/resources` as follows:
 * ```xml
 * <?xml version="1.0" encoding="UTF-8"?>
 * <configuration>
 *   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *       <mdcEntryWriter class="dev.hermannm.devlog.LoggingContextJsonFieldWriter"/>
 *     </encoder>
 *   </appender>
 *
 *   <root level="INFO">
 *     <appender-ref ref="STDOUT"/>
 *   </root>
 * </configuration>
 * ```
 *
 * This requires that you have added `ch.qos.logback:logback-classic` and
 * `net.logstash.logback:logstash-logback-encoder` as dependencies.
 */
class LoggingContextJsonFieldWriter : MdcEntryWriter {
  /** @return true if we handled the entry, false otherwise. */
  override fun writeMdcEntry(
      generator: JsonGenerator,
      fieldName: String,
      mdcKey: String,
      value: String
  ): Boolean {
    if (value.startsWith(LoggingContext.JSON_FIELD_VALUE_PREFIX)) {
      val offset = LoggingContext.JSON_FIELD_VALUE_PREFIX.length

      // We only want to handle the entry if it's not blank after "json: "
      if (value.length > offset) {
        generator.writeFieldName(fieldName)
        generator.writeRawValue(value, offset, value.length - offset)
        return true
      }
    }

    return false
  }
}
