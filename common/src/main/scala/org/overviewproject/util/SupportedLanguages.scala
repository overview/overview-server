package org.overviewproject.util

object SupportedLanguages {
  val languageCodes = Set(
    "de",
    "en",
    "es",
    "fr",
    "sv"
  )

  val languages = languageCodes.map(SupportedLanguage.apply)
}
