package com.overviewdocs.models

import java.io.StringReader
import java.util.Date
import java.text.Normalizer
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer // It's what ElasticSearch uses
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute // Built in to ElasticSearch
import play.api.libs.json.JsObject
import scala.collection.mutable.ArrayBuffer

/** A complete Document.
  *
  * The `text` field can make this rather large. If you don't need the `text`
  * field, use a DocumentInfo object instead.
  */
case class Document(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String],
  override val createdAt: Date,
  val fileId: Option[Long],
  val pageId: Option[Long],
  override val displayMethod: DocumentDisplayMethod.Value,
  override val isFromOcr: Boolean,
  override val metadataJson: JsObject,
  override val text: String
) extends DocumentHeader {
  def toDocumentInfo: DocumentInfo = DocumentInfo(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    pageNumber,
    keywords,
    createdAt,
    displayMethod,
    isFromOcr,
    fileId.isDefined
  )

  override def viewUrl: Option[String] = {
    url
      .orElse(fileId.map(_ => s"/documents/${id}.pdf"))
  }

  /** Text, normalized as NFKC. */
  private def normalizedText: String = Normalizer.normalize(text, Normalizer.Form.NFKC)

  /** Returns NFKC-normalized tokens split according to Unicode tr29. */
  override def tokens: Seq[String] = {
    // If we start paying attention to Asian languages, switch this to use
    // ICUTokenizer, which is in a separate package. We're mimicking our
    // ElasticSearch behavior here.
    //
    // https://www.elastic.co/guide/en/elasticsearch/guide/current/icu-tokenizer.html
    val tokenizer = new ICUTokenizer()
    tokenizer.setReader(new StringReader(normalizedText))
    var charTermAttribute = tokenizer.addAttribute(classOf[CharTermAttribute])

    val ret = new ArrayBuffer[String]

    // Docs say reset. http://lucene.apache.org/core/4_8_0/core/index.html?org/apache/lucene/analysis/TokenStream.html
    tokenizer.reset()
    while (tokenizer.incrementToken()) {
      ret.append(charTermAttribute.toString)
    }
    tokenizer.end()
    tokenizer.close()

    ret
  }
}
