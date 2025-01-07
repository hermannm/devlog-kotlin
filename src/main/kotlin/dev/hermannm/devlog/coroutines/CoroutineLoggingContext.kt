package dev.hermannm.devlog.coroutines

import dev.hermannm.devlog.JsonLogField
import dev.hermannm.devlog.LOGGING_CONTEXT_JSON_KEY_SUFFIX
import dev.hermannm.devlog.LogField
import dev.hermannm.devlog.Logger
import dev.hermannm.devlog.LoggingContext
import dev.hermannm.devlog.LoggingContextJsonFieldWriter
import dev.hermannm.devlog.withLoggingContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import org.slf4j.MDC

/**
 * **Note:** To use this, you must have `kotlinx-coroutines-core` added as a dependency.
 *
 * The logging context consists of [log fields][LogField] that are attached to every log in a scope.
 * The default [withLoggingContext] function uses a thread-local, which doesn't play well with
 * Kotlin coroutines. This function lets you bridge that gap, by returning a [CoroutineContext] that
 * you can pass to [launch][kotlinx.coroutines.launch]/[async][kotlinx.coroutines.async] to inherit
 * logging context fields in spawned coroutines.
 *
 * You can pass extra log fields as a vararg to add them to the coroutine context (use
 * [field][dev.hermannm.devlog.field] to construct them). If you want to add fields to the current
 * coroutine's context, you can use [withCoroutineLoggingContext].
 *
 * The implementation uses [MDC] from SLF4J. So if you use `MDC` directly, those fields will also be
 * included. The coroutine context takes care of setting/resetting the correct logging context for
 * the thread when a coroutine suspends/resumes.
 *
 * One consequence of using `MDC` is that it only supports String values by default. To encode
 * object values as actual JSON (not escaped strings), you can use [LoggingContextJsonFieldWriter]
 * with Logback.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.coroutines.coroutineLoggingContext
 * import dev.hermannm.devlog.coroutines.withCoroutineLoggingContext
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import kotlinx.coroutines.launch
 *
 * private val log = getLogger {}
 *
 * internal class OrderService(
 *     private val orderRepository: OrderRepository,
 *     private val statisticsService: StatisticsService,
 * ) {
 *   suspend fun updateOrder(order: Order) {
 *     // In this scenario, we want to store the given order in the database, and update statistics
 *     // for the order in a separate statistics service. Since these are separate from each other,
 *     // we launch a coroutine for each of them. But we also want the order ID to be included on
 *     // the logs in each coroutine.
 *     //
 *     // So we use `withCoroutineLoggingContext` here in the parent coroutine, and call
 *     // `coroutineLoggingContext` when spawning child coroutines, so that they inherit log fields
 *     // from their parent.
 *     withCoroutineLoggingContext(field("orderId", order.id)) {
 *       launch(coroutineLoggingContext()) {
 *         log.debug { "Updating order in database" }
 *         orderRepository.update(order)
 *       }
 *
 *       launch(coroutineLoggingContext()) {
 *         log.debug { "Updating order statistics" }
 *         statisticsService.orderUpdated(order)
 *       }
 *     }
 *   }
 * }
 * ```
 */
public fun coroutineLoggingContext(vararg newFields: LogField): CoroutineContext {
  CoroutineDispatcher
  return coroutineLoggingContextInternal(newFields)
}

/**
 * Internal implementation for [coroutineLoggingContext] and [withCoroutineLoggingContext], taking
 * an array to avoid redundant array copies of varargs.
 */
internal fun coroutineLoggingContextInternal(newFields: Array<out LogField>): CoroutineContext {
  // We get the field map from the current logging context, and merge that with the new fields.
  var contextFields = LoggingContext.getFieldMap()

  if (contextFields.isNullOrEmpty()) {
    if (newFields.isEmpty()) {
      // If there are no fields in the current context, and no new fields, then we don't want to
      // allocate a map
      return MDCCoroutineContext(null)
    }

    contextFields = HashMap<String, String?>(newFields.size)
  }

  for (field in newFields) {
    contextFields[field.keyForLoggingContext] = field.value

    /**
     * If we get a [JsonLogField] whose key matches a non-JSON field in the context, then we want to
     * overwrite "key" with "key (json)" (adding [LOGGING_CONTEXT_JSON_KEY_SUFFIX] to identify the
     * JSON value). But since "key (json)" does not match "key", setting the key in the map will not
     * overwrite the previous field, so we have to manually remove it here.
     */
    if (field.key != field.keyForLoggingContext) {
      contextFields.remove(field.key)
    }
  }

  return MDCCoroutineContext(contextFields)
}

/**
 * **Note:** To use this, you must have `kotlinx-coroutines-core` added as a dependency.
 *
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block]. This is a coroutine-friendly version of the thread-local-based [withLoggingContext].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * The implementation uses [MDC] from SLF4J, which only supports String values by default. To encode
 * object values as actual JSON (not escaped strings), you can use [LoggingContextJsonFieldWriter]
 * with Logback.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger {}
 *
 * suspend fun example(event: Event) {
 *   withCoroutineLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * If you have configured [LoggingContextJsonFieldWriter], the field from `withLoggingContext` will
 * then be attached to every log as follows:
 * ```json
 * { "message": "Started processing event", "event": { ... } }
 * { "message": "Finished processing event", "event": { ... } }
 * ```
 */
public suspend fun <ReturnT> withCoroutineLoggingContext(
    vararg logFields: LogField,
    block: suspend CoroutineScope.() -> ReturnT
): ReturnT {
  return kotlinx.coroutines.withContext(coroutineLoggingContextInternal(logFields), block)
}

/**
 * **Note:** To use this, you must have `kotlinx-coroutines-core` added as a dependency.
 *
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block]. This is a coroutine-friendly version of the thread-local-based [withLoggingContext].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * The implementation uses [MDC] from SLF4J, which only supports String values by default. To encode
 * object values as actual JSON (not escaped strings), you can use [LoggingContextJsonFieldWriter]
 * with Logback.
 *
 * This overload of the function takes a list instead of varargs, for when you already have a list
 * of log fields available.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger {}
 *
 * suspend fun example(event: Event) {
 *   withCoroutineLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * If you have configured [LoggingContextJsonFieldWriter], the field from `withLoggingContext` will
 * then be attached to every log as follows:
 * ```json
 * { "message": "Started processing event", "event": { ... } }
 * { "message": "Finished processing event", "event": { ... } }
 * ```
 */
public suspend fun <ReturnT> withCoroutineLoggingContext(
    logFields: List<LogField>,
    block: suspend CoroutineScope.() -> ReturnT
): ReturnT {
  return kotlinx.coroutines.withContext(
      coroutineLoggingContextInternal(logFields.toTypedArray()),
      block,
  )
}

/**
 * Copied from
 * [`kotlinx-coroutines-slf4j`](https://github.com/Kotlin/kotlinx.coroutines/blob/fed40ad1f9942d1b16be872cc555e08f965cf881/integration/kotlinx-coroutines-slf4j/src/MDCContext.kt)
 * (with minor naming changes), because:
 * - We don't want this library to depend on coroutines directly, since we don't want to add that
 *   dependency for applications that don't use coroutines.
 * - So we make `kotlinx-coroutines-core` an optional dependency in our POM. If the user uses
 *   coroutines, they will have this dependency, and so be able to use coroutine functionality.
 * - However, the user is less likely to have added `kotlinx-coroutines-slf4j`. We could add that as
 *   an optional dependency as well, but then the user may face `ClassDefNotFoundError`s at runtime
 *   if they forget to add that dependency, when trying to load `MDCContext`.
 * - So instead, we copy the `MDCContext` implementation from `kotlinx-coroutines-slf4j` here, so
 *   that users only need to provide the `kotlinx-coroutines-core` dependency, which they will
 *   likely already have if using coroutines.
 *
 * ```text
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * ```
 *
 * License:
 * ```text
 *                                  Apache License
 *                            Version 2.0, January 2004
 *                         http://www.apache.org/licenses/
 *
 *    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
 *
 *    1. Definitions.
 *
 *       "License" shall mean the terms and conditions for use, reproduction,
 *       and distribution as defined by Sections 1 through 9 of this document.
 *
 *       "Licensor" shall mean the copyright owner or entity authorized by
 *       the copyright owner that is granting the License.
 *
 *       "Legal Entity" shall mean the union of the acting entity and all
 *       other entities that control, are controlled by, or are under common
 *       control with that entity. For the purposes of this definition,
 *       "control" means (i) the power, direct or indirect, to cause the
 *       direction or management of such entity, whether by contract or
 *       otherwise, or (ii) ownership of fifty percent (50%) or more of the
 *       outstanding shares, or (iii) beneficial ownership of such entity.
 *
 *       "You" (or "Your") shall mean an individual or Legal Entity
 *       exercising permissions granted by this License.
 *
 *       "Source" form shall mean the preferred form for making modifications,
 *       including but not limited to software source code, documentation
 *       source, and configuration files.
 *
 *       "Object" form shall mean any form resulting from mechanical
 *       transformation or translation of a Source form, including but
 *       not limited to compiled object code, generated documentation,
 *       and conversions to other media types.
 *
 *       "Work" shall mean the work of authorship, whether in Source or
 *       Object form, made available under the License, as indicated by a
 *       copyright notice that is included in or attached to the work
 *       (an example is provided in the Appendix below).
 *
 *       "Derivative Works" shall mean any work, whether in Source or Object
 *       form, that is based on (or derived from) the Work and for which the
 *       editorial revisions, annotations, elaborations, or other modifications
 *       represent, as a whole, an original work of authorship. For the purposes
 *       of this License, Derivative Works shall not include works that remain
 *       separable from, or merely link (or bind by name) to the interfaces of,
 *       the Work and Derivative Works thereof.
 *
 *       "Contribution" shall mean any work of authorship, including
 *       the original version of the Work and any modifications or additions
 *       to that Work or Derivative Works thereof, that is intentionally
 *       submitted to Licensor for inclusion in the Work by the copyright owner
 *       or by an individual or Legal Entity authorized to submit on behalf of
 *       the copyright owner. For the purposes of this definition, "submitted"
 *       means any form of electronic, verbal, or written communication sent
 *       to the Licensor or its representatives, including but not limited to
 *       communication on electronic mailing lists, source code control systems,
 *       and issue tracking systems that are managed by, or on behalf of, the
 *       Licensor for the purpose of discussing and improving the Work, but
 *       excluding communication that is conspicuously marked or otherwise
 *       designated in writing by the copyright owner as "Not a Contribution."
 *
 *       "Contributor" shall mean Licensor and any individual or Legal Entity
 *       on behalf of whom a Contribution has been received by Licensor and
 *       subsequently incorporated within the Work.
 *
 *    2. Grant of Copyright License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       copyright license to reproduce, prepare Derivative Works of,
 *       publicly display, publicly perform, sublicense, and distribute the
 *       Work and such Derivative Works in Source or Object form.
 *
 *    3. Grant of Patent License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       (except as stated in this section) patent license to make, have made,
 *       use, offer to sell, sell, import, and otherwise transfer the Work,
 *       where such license applies only to those patent claims licensable
 *       by such Contributor that are necessarily infringed by their
 *       Contribution(s) alone or by combination of their Contribution(s)
 *       with the Work to which such Contribution(s) was submitted. If You
 *       institute patent litigation against any entity (including a
 *       cross-claim or counterclaim in a lawsuit) alleging that the Work
 *       or a Contribution incorporated within the Work constitutes direct
 *       or contributory patent infringement, then any patent licenses
 *       granted to You under this License for that Work shall terminate
 *       as of the date such litigation is filed.
 *
 *    4. Redistribution. You may reproduce and distribute copies of the
 *       Work or Derivative Works thereof in any medium, with or without
 *       modifications, and in Source or Object form, provided that You
 *       meet the following conditions:
 *
 *       (a) You must give any other recipients of the Work or
 *           Derivative Works a copy of this License; and
 *
 *       (b) You must cause any modified files to carry prominent notices
 *           stating that You changed the files; and
 *
 *       (c) You must retain, in the Source form of any Derivative Works
 *           that You distribute, all copyright, patent, trademark, and
 *           attribution notices from the Source form of the Work,
 *           excluding those notices that do not pertain to any part of
 *           the Derivative Works; and
 *
 *       (d) If the Work includes a "NOTICE" text file as part of its
 *           distribution, then any Derivative Works that You distribute must
 *           include a readable copy of the attribution notices contained
 *           within such NOTICE file, excluding those notices that do not
 *           pertain to any part of the Derivative Works, in at least one
 *           of the following places: within a NOTICE text file distributed
 *           as part of the Derivative Works; within the Source form or
 *           documentation, if provided along with the Derivative Works; or,
 *           within a display generated by the Derivative Works, if and
 *           wherever such third-party notices normally appear. The contents
 *           of the NOTICE file are for informational purposes only and
 *           do not modify the License. You may add Your own attribution
 *           notices within Derivative Works that You distribute, alongside
 *           or as an addendum to the NOTICE text from the Work, provided
 *           that such additional attribution notices cannot be construed
 *           as modifying the License.
 *
 *       You may add Your own copyright statement to Your modifications and
 *       may provide additional or different license terms and conditions
 *       for use, reproduction, or distribution of Your modifications, or
 *       for any such Derivative Works as a whole, provided Your use,
 *       reproduction, and distribution of the Work otherwise complies with
 *       the conditions stated in this License.
 *
 *    5. Submission of Contributions. Unless You explicitly state otherwise,
 *       any Contribution intentionally submitted for inclusion in the Work
 *       by You to the Licensor shall be under the terms and conditions of
 *       this License, without any additional terms or conditions.
 *       Notwithstanding the above, nothing herein shall supersede or modify
 *       the terms of any separate license agreement you may have executed
 *       with Licensor regarding such Contributions.
 *
 *    6. Trademarks. This License does not grant permission to use the trade
 *       names, trademarks, service marks, or product names of the Licensor,
 *       except as required for reasonable and customary use in describing the
 *       origin of the Work and reproducing the content of the NOTICE file.
 *
 *    7. Disclaimer of Warranty. Unless required by applicable law or
 *       agreed to in writing, Licensor provides the Work (and each
 *       Contributor provides its Contributions) on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *       implied, including, without limitation, any warranties or conditions
 *       of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
 *       PARTICULAR PURPOSE. You are solely responsible for determining the
 *       appropriateness of using or redistributing the Work and assume any
 *       risks associated with Your exercise of permissions under this License.
 *
 *    8. Limitation of Liability. In no event and under no legal theory,
 *       whether in tort (including negligence), contract, or otherwise,
 *       unless required by applicable law (such as deliberate and grossly
 *       negligent acts) or agreed to in writing, shall any Contributor be
 *       liable to You for damages, including any direct, indirect, special,
 *       incidental, or consequential damages of any character arising as a
 *       result of this License or out of the use or inability to use the
 *       Work (including but not limited to damages for loss of goodwill,
 *       work stoppage, computer failure or malfunction, or any and all
 *       other commercial damages or losses), even if such Contributor
 *       has been advised of the possibility of such damages.
 *
 *    9. Accepting Warranty or Additional Liability. While redistributing
 *       the Work or Derivative Works thereof, You may choose to offer,
 *       and charge a fee for, acceptance of support, warranty, indemnity,
 *       or other liability obligations and/or rights consistent with this
 *       License. However, in accepting such obligations, You may act only
 *       on Your own behalf and on Your sole responsibility, not on behalf
 *       of any other Contributor, and only if You agree to indemnify,
 *       defend, and hold each Contributor harmless for any liability
 *       incurred by, or claims asserted against, such Contributor by reason
 *       of your accepting any such warranty or additional liability.
 *
 *    END OF TERMS AND CONDITIONS
 * ```
 *
 * NOTICE:
 * ```
 * kotlinx.coroutines library.
 * Copyright 2016-2024 JetBrains s.r.o and contributors
 * ```
 */
internal class MDCCoroutineContext(
    private val contextMap: Map<String, String?>?,
) : ThreadContextElement<Map<String, String?>?>, AbstractCoroutineContextElement(Key) {
  internal companion object Key : CoroutineContext.Key<MDCCoroutineContext>

  /** @return Old state, passed to [restoreThreadContext] when coroutine is suspended. */
  override fun updateThreadContext(context: CoroutineContext): Map<String, String?>? {
    val oldState = MDC.getCopyOfContextMap()
    setCurrent(contextMap)
    return oldState
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String?>?) {
    setCurrent(oldState)
  }

  private fun setCurrent(contextMap: Map<String, String?>?) {
    if (contextMap == null) {
      MDC.clear()
    } else {
      MDC.setContextMap(contextMap)
    }
  }
}
