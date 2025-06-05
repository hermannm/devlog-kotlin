package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.getLogger

/**
 * Used in [dev.hermannm.devlog.LoggerTest] to test that the logger gets the expected name from the
 * file it's constructed in.
 */
internal val loggerInOtherFile = getLogger()
