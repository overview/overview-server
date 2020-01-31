package com.overviewdocs.util

import java.util.Locale

object SupportedLanguages {
  // Supported languages, in ISO639-2 format
  val languageCodes = Set(
    "ar",
    "ca",
    "cs",
    "de",
    "en",
    "es",
    "fr",
    "sv",
    "nl",
    "no",
    "it",
    "pt",
    "ro",
    "ru"
  )
  
  val languages : Set[SupportedLanguage] = languageCodes.map(SupportedLanguage)

  val defaultLanguage : SupportedLanguage = SupportedLanguage("en")

  /** A list of languages, sorted by their display names in callerLocale */
  def languagesSortedInCallerLocale(callerLocale: Locale) : Seq[SupportedLanguage] = {
    languages.toIndexedSeq.sortBy((lang) => lang.displayLanguage(callerLocale))
  }
}
