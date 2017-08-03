package models

import com.overviewdocs.query.Field
import com.overviewdocs.searchindex.SearchWarning

/** Something about a Selection's results the user should know. */
sealed trait SelectionWarning
object SelectionWarning {
  /** The search index did not return an accurate set of documents. */
  case class SearchIndexWarning(warning: SearchWarning) extends SelectionWarning

  /** The given regex could not be parsed. */
  case class RegexSyntaxError(pattern: String, errorEnglish: String, index: Int) extends SelectionWarning

  /** Overview doesn't support nested regexes. */
  case class NestedRegexIgnored(pattern: String) extends SelectionWarning

  /** The query specified a metadata field that does not exist. */
  case class MissingField(fieldName: String, validFieldNames: Seq[String]) extends SelectionWarning
}
