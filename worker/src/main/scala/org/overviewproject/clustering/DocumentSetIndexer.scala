/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import java.sql.Connection
import org.squeryl.PrimitiveTypeMode.using
import org.squeryl.Session
import org.overviewproject.database.{ DeprecatedDatabase, DB }
import org.overviewproject.persistence.{ DocumentWriter, NodeWriter }
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.util.{ DocumentConsumer, Logger }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.{ Clustering, Done, Saving }
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress }
import org.overviewproject.nlp.WeightedLexer
import org.overviewproject.nlp.BigramDocumentVectorGenerator
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.StopWordSet

// Home for all indexing and clustering options. 
// Instantiate this to pass down call chain, plus it contains our defaults
// Ignore suppliedStopWords
// multiply weight of any word matching emphasizedWordsRegex
class DocumentSetIndexerOptions(
    val lang: String = "en",
    val suppliedStopWords: Option[String] = None,
    val emphasizedWordsRegex: Map[String, TermWeight] = Map.empty) {
  // Tokenization

  // Bigram detection. Use different sets for "small" and otherwise
  val minDocsToKeepTerm = 3
  val minBigramOccurrences = 5
  val smallDocsetSize = 200
  val minDocsToKeepTermSmall = 2
  val minBigramOccurrencesSmall = 3

}

object DocumentSetIndexerOptions {
  // Converts "important words" options string into a map of regex->weight
  // splits on runs of spaces, fixed weight
  private def makeEmphasizedWords(s: Option[String]): Map[String, TermWeight] = {
    if (s.isEmpty) {
      Map[String, TermWeight]()
    } else {
      val extraWeight: TermWeight = 5
      "[ \t\n\r\u00A0]+".r.replaceAllIn(s.get, " ").split(' ').filter(!_.isEmpty).map(w => (w, extraWeight)).toMap
    }
  }

  def apply(lang: String, suppliedStopWords: Option[String], importantWords: Option[String]): DocumentSetIndexerOptions = {
    val options = new DocumentSetIndexerOptions(lang, suppliedStopWords, makeEmphasizedWords(importantWords))
    options
  }
}

class DocumentSetIndexer(nodeWriter: NodeWriter, options: DocumentSetIndexerOptions, progAbort: ProgressAbortFn) extends DocumentConsumer {

  // --- private ---
  val t0 = System.nanoTime()
  private var fractionFetched = 0
  private val fetchingFraction = 0.5 // what percent done do we say when we're all done fetching docs?
  private val savingFraction = 0.98

  private val smallDocsetSize = 200 // when docset is smaller than this, reduce minimum count thresholds for keeping terms

  private val lexer = new WeightedLexer(StopWordSet(options.lang, options.suppliedStopWords), options.emphasizedWordsRegex)
  private val vectorGen = new BigramDocumentVectorGenerator

  // When we get the document text back, we feed the text to the vector generator
  def processDocument(documentId: Long, text: String): Unit = {
    vectorGen.addDocumentWithWeightedTerms(documentId, lexer.makeTerms(text))
  }

  private def addDocumentDescriptions(docVecs: DocumentSetVectors)(implicit c: Connection) {
    DeprecatedDatabase.inTransaction {
      for ((docId, vec) <- docVecs) {
        DocumentWriter.updateDescription(docId, SuggestedTags.suggestedTagsForDocument(vec, docVecs))
      }
    }
  }

  def productionComplete() {
    Logger.logElapsedTime("Retrieved " + vectorGen.numDocs, t0)

    if (!progAbort(Progress(fetchingFraction, Clustering))) {
      val t1 = System.nanoTime()

      // keep more of the terms in the tail if the docset is small
      if (vectorGen.numDocs < options.smallDocsetSize) {
        vectorGen.minDocsToKeepTerm = options.minDocsToKeepTermSmall
        vectorGen.minBigramOccurrences = options.minBigramOccurrencesSmall
      } else {
        vectorGen.minDocsToKeepTerm = options.minDocsToKeepTerm
        vectorGen.minBigramOccurrences = options.minBigramOccurrences
      }

      val docVecs = vectorGen.documentVectors()
      val docTree = BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction))

      Logger.logElapsedTime("Clustered documents", t1)

      // Save tree to database
      if (!progAbort(Progress(savingFraction, Saving))) {
        val t2 = System.nanoTime()

        DB.withConnection { implicit connection => addDocumentDescriptions(docVecs) }
        DeprecatedDatabase.inTransaction {
          implicit val connection = DeprecatedDatabase.currentConnection
          nodeWriter.write(docTree)
        }

        Logger.logElapsedTime("Saved DocumentSet to DB", t2)
        progAbort(Progress(1, Done))
      }
    }
  }
}
