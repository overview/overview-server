/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 *
 * Overview, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering

import com.overviewdocs.persistence.{ DocumentWriter, NodeWriter }
import com.overviewdocs.util.{ DocumentConsumer, Logger }
import com.overviewdocs.util.DocumentSetCreationJobStateDescription.{ Clustering, Done, Saving }
import com.overviewdocs.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress }
import com.overviewdocs.nlp.WeightedLexer
import com.overviewdocs.nlp.BigramDocumentVectorGenerator
import com.overviewdocs.nlp.DocumentVectorTypes._
import com.overviewdocs.nlp.StopWordSet

// Home for all indexing and clustering options.
// Instantiate this to pass down call chain, plus it contains our defaults
// Ignore suppliedStopWords
// multiply weight of any word matching emphasizedWordsRegex
class DocumentSetIndexerOptions(
    val lang: String = "en",
    val suppliedStopWords: String = "",
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
  private def makeEmphasizedWords(s: String): Map[String, TermWeight] = {
    val extraWeight: TermWeight = 5
    "[ \t\n\r\u00A0]+".r.replaceAllIn(s, " ").split(' ').filter(!_.isEmpty).map(w => (w, extraWeight)).toMap
  }

  def apply(lang: String, suppliedStopWords: String, importantWords: String): DocumentSetIndexerOptions = {
    val options = new DocumentSetIndexerOptions(lang, suppliedStopWords, makeEmphasizedWords(importantWords))
    options
  }
}

class DocumentSetIndexer(
  nodeWriter: NodeWriter,
  options: DocumentSetIndexerOptions,
  progAbort: ProgressAbortFn
) extends DocumentConsumer {
  private val logger = Logger.forClass(getClass)
  private val t0 = System.nanoTime()
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

  private def addDocumentDescriptions(docVecs: DocumentSetVectors) {
    for ((docId, vec) <- docVecs) {
      DocumentWriter.updateDescription(docId, SuggestedTags.suggestedTagsForDocument(vec, docVecs))
    }
  }

  def productionComplete() {
    logger.logElapsedTime("Retrieved {} documents", t0, vectorGen.numDocs)

    if (!progAbort(Progress(fetchingFraction, Clustering))) {
      val (docVecs, docTree) = logger.logExecutionTime("Clustered documents") {
        // keep more of the terms in the tail if the docset is small
        if (vectorGen.numDocs < options.smallDocsetSize) {
          vectorGen.minDocsToKeepTerm = options.minDocsToKeepTermSmall
          vectorGen.minBigramOccurrences = options.minBigramOccurrencesSmall
        } else {
          vectorGen.minDocsToKeepTerm = options.minDocsToKeepTerm
          vectorGen.minBigramOccurrences = options.minBigramOccurrences
        }

        val docVecs = vectorGen.documentVectors()
        (docVecs, BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction)))
      }

      // Save tree to database
      if (!progAbort(Progress(savingFraction, Saving))) {
        logger.logExecutionTime("Saved DocumentSet to database") {
          addDocumentDescriptions(docVecs)
          nodeWriter.write(docTree)
        }
        progAbort(Progress(1, Done))
      }
    }
  }
}
