package com.overviewdocs.documentcloud

import play.api.libs.json.JsValue
import scala.collection.mutable

import com.overviewdocs.util.Textify

case class IdList(
  rows: Seq[IdListRow]
) {
  def nDocuments: Int = rows.length
  def nPages: Int = rows.foldLeft(0)((s, r) => s + r.nPages)
}

object IdList {
  /** Turn an IdList into a String.
    *
    * We use ASCII control characters that we know Textify will never produce.
    *
    * `\u001f` is "Unit Separator" and `\u001e` is "Record Separator". (The
    * people who invented ASCII were really on to something....)
    */
  def encode(idList: IdList): String = {
    idList.rows
      .map { r =>
        s"${r.documentCloudId}\u001f${r.title}\u001f${r.nPages}\u001f${r.fullTextUrl}\u001f${r.pageTextUrlTemplate}\u001f${r.access}"
      }
      .mkString("\u001e")
  }

  /** Turns a String into an IdList.
    *
    * See encode() for details of the algorithm.
    *
    * If the string is not a valid IdList, returns `None`.
    */
  def decode(idsString: String): Option[IdList] = {
    val buf = mutable.ArrayBuffer[IdListRow]()

    for (record <- idsString.split("\u001e")) {
      val values = record.split("\u001f")
      if (values.length != 6) return None
      val nPages: Int = try {
        values(2).toInt
      } catch {
        case _: NumberFormatException => return None
      }
      buf.+=(IdListRow(values(0), values(1), nPages, values(3), values(4), values(5)))
    }

    Some(IdList(buf))
  }

  /** Gets an IdList and total number of documents from a DocumentCloud
    * `/api/search.json` result.
    *
    * If the result does not contain the required fields, returns `None`.
    */
  def parseDocumentCloudSearchResult(json: JsValue): Option[(IdList,Int)] = {
    json.asOpt[ParseResult](idListReads).map(r => (IdList(r.idListRows), r.total))
  }

  private case class ParseResult(idListRows: Seq[IdListRow], total: Int)

  implicit private val idListRowReads = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json._
    (
      (__ \ "id").read[String] and
      (__ \ "title").read[String].map(Textify.apply _) and
      (__ \ "pages").read[Int] and
      (__ \ "resources" \ "text").read[String] and
      (__ \ "resources" \ "page" \ "text").read[String] and
      (__ \ "access").read[String]
    )(IdListRow)
  }

  implicit private val idListReads = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json._
    (
      (__ \ "documents").read[Seq[IdListRow]] and
      (__ \ "total").read[Int]
    )(ParseResult)
  }
}
