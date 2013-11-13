package org.overviewproject.util

import java.util.Locale

object SupportedLanguages {
  val languageCodes = Set(
    "ar",
    "de",
    "en",
    "es",
    "fr",
    "sv",
    "nl",
    "it", 
    "ru"
  )

  val languages : Set[SupportedLanguage] = languageCodes.map(SupportedLanguage.apply)

  val defaultLanguage : SupportedLanguage = SupportedLanguage("en")

  /** A list of languages, sorted by their display names in callerLocale */
  def languagesSortedInCallerLocale(callerLocale: Locale) : Seq[SupportedLanguage] = {
    languages.toIndexedSeq.sortBy((lang) => lang.displayLanguage(callerLocale))
  }
}
