package com.overviewdocs.searchindex

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.{IndexSearcher,Query}
import org.apache.lucene.search.uhighlight._
import scala.collection.mutable.ArrayBuffer

class LuceneMultiDocumentHighlighter(indexSearcher: IndexSearcher, analyzer: Analyzer)
extends UnifiedHighlighter(indexSearcher, analyzer)
{
  private val MaxPassages = 3

  setFormatter(LuceneMultiDocumentHighlighter.passageFormatter)

  /** Returns Snippets marking document matches.
    *
    * If no matching Snippets can be found, returns an empty Array.
    */
  def highlightFieldAsSnippets(field: String, query: Query, docId: Int): Array[Utf16Snippet] = {
    val map = highlightFieldsAsObjects(Array(field), query, Array(docId), Array(MaxPassages))
    val fieldResults: Array[Object] = map.getOrDefault(field, Array.empty)
    val docResults: Array[Utf16Snippet] = fieldResults.headOption.map(_.asInstanceOf[Array[Utf16Snippet]]).getOrElse(Array.empty)
    System.err.println(docResults.headOption.toString)
    if (docResults == null) return Array.empty // Lucene breaking my heart
    docResults
      //.filter(_.highlights.nonEmpty)
  }
}

object LuceneMultiDocumentHighlighter {
  val passageFormatter = new PassageFormatter {
    /** Return an Array[Utf16Snippet] */
    override def format(passages: Array[Passage], content: String) = {
      val ret = ArrayBuffer.empty[Utf16Snippet]

      for (passage <- passages) {
        val highlights = ArrayBuffer.empty[Utf16Highlight]
        System.err.println("passage: " + passage.getStartOffset + "," + passage.getEndOffset)
        System.err.println("match starts (" + passage.getNumMatches + "): " + passage.getMatchStarts.toSeq.mkString(","))
        System.err.println("match ends: " + passage.getMatchStarts.toSeq.mkString(","))
        passage.getMatchStarts.zip(passage.getMatchEnds).take(passage.getNumMatches).map { case ((start: Int, end: Int)) =>
          highlights.lastOption match {
            case Some(highlight) if highlight.end >= start => {
              // According to Lucene's DefaultPassageFormatter.java:
              // "it's possible to have overlapping terms"
              highlights(highlights.length - 1) = Utf16Highlight(highlight.begin, end)
            }
            case _ => highlights.append(Utf16Highlight(start, end))
          }
        }

        ret.append(Utf16Snippet(passage.getStartOffset, passage.getEndOffset, highlights.toVector))
      }

      ret.toArray
    }
  }
}
