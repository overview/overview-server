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

import com.overviewdocs.nlp.WeightedLexer
import com.overviewdocs.nlp.BigramDocumentVectorGenerator
import com.overviewdocs.nlp.DocumentVectorTypes._
import com.overviewdocs.nlp.StopWordSet

class DocumentSetIndexer(
  val lang: String,
  val suppliedStopWords: Seq[String],
  val importantWords: Seq[String]
) {
  protected val MinDocsToKeepTerm = 3
  protected val MinBigramOccurrences = 5
  protected val SmallDocsetSize = 200 // when docset is smaller than this, reduce minimum count thresholds for keeping terms
  protected val MinDocsToKeepTermSmall = 2
  protected val MinBigramOccurrencesSmall = 3

  private val emphasizedWords: Map[String, TermWeight] = importantWords.map(w => (w -> 5.toFloat)).toMap

  private val t0 = System.nanoTime()

  private val lexer = new WeightedLexer(StopWordSet(lang, suppliedStopWords), emphasizedWords)
  private val vectorGen = new BigramDocumentVectorGenerator

  // When we get the document text back, we feed the text to the vector generator
  def addDocument(documentId: Long, text: String): Unit = {
    vectorGen.addDocumentWithWeightedTerms(documentId, lexer.makeTerms(text))
  }

  def cluster(onProgress: Double => Unit): (DocumentSetVectors, DocTreeNode) = {
    // keep more of the terms in the tail if the docset is small
    if (vectorGen.numDocs < SmallDocsetSize) {
      vectorGen.minDocsToKeepTerm = MinDocsToKeepTermSmall
      vectorGen.minBigramOccurrences = MinBigramOccurrencesSmall
    } else {
      vectorGen.minDocsToKeepTerm = MinDocsToKeepTerm
      vectorGen.minBigramOccurrences = MinBigramOccurrences
    }

    val docVecs = vectorGen.documentVectors()
    val rootNode = BuildDocTree(docVecs, onProgress)
    (docVecs, rootNode)
  }
}
