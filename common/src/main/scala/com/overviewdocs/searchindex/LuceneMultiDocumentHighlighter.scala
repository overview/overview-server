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
  def highlightFieldAsSnippets(field: String, query: Query, docId: Int): Array[Snippet] = {
    val map = highlightFieldsAsObjects(Array(field), query, Array(docId), Array(MaxPassages))
    val results: Array[Object] = map.get(field)
    results
      .flatMap(_.asInstanceOf[Array[Snippet]])
      .filter(_.highlights.nonEmpty)
  }
}

object LuceneMultiDocumentHighlighter {
  val passageFormatter = new PassageFormatter {
    /** Return an Array[Snippet] */
    override def format(passages: Array[Passage], content: String) = {
      val ret = ArrayBuffer.empty[Snippet]

      for (passage <- passages) {
        val highlights = ArrayBuffer.empty[Highlight]
        passage.getMatchStarts.zip(passage.getMatchEnds).map { case ((start: Int, end: Int)) =>
          if (start != end) {
            highlights.lastOption match {
              case Some(highlight) if highlight.end >= start => {
                // According to Lucene's DefaultPassageFormatter.java:
                // "it's possible to have overlapping terms"
                highlights(highlights.length - 1) = Highlight(highlight.begin, end)
              }
              case _ => highlights.append(Highlight(start, end))
            }
          }
        }

        ret.append(Snippet(passage.getStartOffset, passage.getEndOffset, highlights))
      }

      ret.toArray
    }
  }
}
