package com.overviewdocs.util

import java.util.Locale

object SupportedLanguages {
  // Supported languages, iso639-1 mapped to iso639-2 format
  // If we want to have different language support for stop-word removal and OCR
  // a more sophisticated approach will be required.
  private val iso639Codes = Map(
    "ar" -> "ara",
    "ca" -> "cat",
    "de" -> "deu",
    "en" -> "eng",
    "es" -> "spa",
    "fr" -> "fra",
    "sv" -> "swe",
    "nl" -> "nld",
    "it" -> "ita",
    "pt" -> "por",
    "ru" -> "rus"
  )

  val languageCodes = iso639Codes.keySet
  
  val languages : Set[SupportedLanguage] = languageCodes.map(SupportedLanguage)

  val defaultLanguage : SupportedLanguage = SupportedLanguage("en")

  /** A list of languages, sorted by their display names in callerLocale */
  def languagesSortedInCallerLocale(callerLocale: Locale) : Seq[SupportedLanguage] = {
    languages.toIndexedSeq.sortBy((lang) => lang.displayLanguage(callerLocale))
  }
  
  /** @returns the iso639-2 language code, if the language is supported */
  def asIso639_2(iso639_1Code: String): Option[String] = iso639Codes.get(iso639_1Code)
}
