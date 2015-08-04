package views.DocumentSet

import java.util.Locale
import play.api.i18n.Messages
import play.api.libs.json.{Json,JsValue}

import com.overviewdocs.util.{SupportedLanguages,SupportedLanguage}

/** Returns [ { code: "en", name: "English" }, ... ]
  *
  * @see com.overviewdocs.util.SupportedLanguages
  **/
object _supportedLanguages {
  private[DocumentSet] def supportedLanguageToJson(language: SupportedLanguage, locale: Locale) : JsValue = {
    Json.obj(
      "code" -> language.languageCode,
      "name" -> language.displayLanguage(locale)
    )
  }

  def apply()(implicit messages: Messages) : JsValue = {
    val locale = messages.lang.toLocale
    val languages = SupportedLanguages.languagesSortedInCallerLocale(locale)
      .map(supportedLanguageToJson(_, locale))
    Json.toJson(languages)
  }
}
