package views.json.DocumentList

import java.util.UUID
import play.api.libs.json.{JsString,JsValue,Json,Writes}
import play.twirl.api.{Html,HtmlFormat}
import scala.collection.immutable

import models.pagination.Page
import models.{Selection,SelectionWarning}
import views.json.api.selectionWarnings
import com.overviewdocs.models.{DocumentHeader,File2,PdfNote,PdfNoteCollection}
import com.overviewdocs.searchindex.{Highlight,Snippet}

object show {
  private implicit def pdfNoteWrites: Writes[PdfNote] = Json.writes[PdfNote]

  private def fileToJson(documentSetId: Long, file2: File2) : JsValue = {
    Json.obj(
      "id" -> file2.id,
      "filename" -> file2.filename,
      "url" -> controllers.routes.DocumentSetFileController.show(documentSetId, file2.id).url
    )
  }

  private def documentToJson(document: DocumentHeader, thumbnailUrl: Option[String], nodeIds: Seq[Long], tagIds: Seq[Long], snippets: Seq[Snippet], maybeRootFile2: Option[File2]) : JsValue = {
    Json.obj(
      "id" -> document.id,
      "documentSetId" -> document.documentSetId.toString,
      "title" -> document.title,
      "page_number" -> document.pageNumber,
      "url" -> document.viewUrl,
      "metadata" -> document.metadataJson,
      "pdfNotes" -> document.pdfNotes.pdfNotes,
      "nodeids" -> nodeIds,
      "tagids" -> tagIds,
      "snippet" -> snippetsToHtml(snippets, document.text),
      "rootFile" -> maybeRootFile2.map(file => fileToJson(document.documentSetId, file)),
      "thumbnailUrl" -> thumbnailUrl,
      "isFromOcr" -> document.isFromOcr,
    )
  }

  def snippetsToHtml(snippets: Seq[Snippet], documentText: String): String = {
    val tokens: Seq[Snippet.Token] = Snippet.concatTokenCollections(snippets.map(_.tokenize(documentText)))
    val htmls: Seq[Html] = tokens.map(_ match {
      case Snippet.TextToken(text) => HtmlFormat.escape(text)
      case Snippet.HighlightToken(text) => HtmlFormat.fill(immutable.Seq(
        HtmlFormat.raw("<em>"),
        HtmlFormat.escape(text),
        HtmlFormat.raw("</em>")
      ))
      case Snippet.ElisionToken => HtmlFormat.raw("…")
    })
    HtmlFormat.fill(htmls.to[immutable.Seq]).body
  }

  def apply(selection: Selection, documents: Page[(DocumentHeader,Option[String],Seq[Long],Seq[Long],Seq[Snippet],Option[File2])]) = {
    Json.obj(
      "selection_id" -> selection.id.toString,
      "warnings" -> selectionWarnings(selection.warnings),
      "total_items" -> documents.pageInfo.total,
      "documents" -> documents.items.map(t => documentToJson(t._1, t._2, t._3, t._4, t._5, t._6)).toSeq
    )
  }
}
