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
import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence.{ DocumentWriter, NodeWriter }
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.util.{ DocumentConsumer, Logger }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.{ Clustering, Done, Saving }
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress }
import org.overviewproject.nlp.Lexer
import org.overviewproject.nlp.BigramDocumentVectorGenerator
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.StopWordSet


class DocumentSetIndexer(nodeWriter: NodeWriter, lang: String, progAbort: ProgressAbortFn) extends DocumentConsumer {

  // --- private ---
  val t0 = System.nanoTime()
  private var fractionFetched = 0
  private val fetchingFraction = 0.5 // what percent done do we say when we're all done fetching docs?
  private val savingFraction = 0.98

  private val smallDocsetSize = 200   // when docset is smaller than this, reduce minimum count thresholds for keeping terms

  private val vectorGen = new BigramDocumentVectorGenerator
  
  // When we get the document text back, we feed the text to the vector generator
  def processDocument(documentId: Long, text: String): Unit = {
    vectorGen.addDocument(documentId, Lexer.makeTerms(text, StopWordSet(lang)))
  }

  private def addDocumentDescriptions(docVecs: DocumentSetVectors)(implicit c: Connection) {
    Database.inTransaction {
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
      if (vectorGen.numDocs < smallDocsetSize) {
        vectorGen.minDocsToKeepTerm = 2
        vectorGen.minBigramOccurrences = 3
      }
        
      val docVecs = vectorGen.documentVectors()
      val docTree = BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction))
      
      Logger.logElapsedTime("Clustered documents", t1)
    
      // Save tree to database
      if (!progAbort(Progress(savingFraction, Saving))) {
        val t2 = System.nanoTime()
        
        DB.withConnection { implicit connection => addDocumentDescriptions(docVecs) }
        Database.inTransaction {
          implicit val connection = Database.currentConnection
          nodeWriter.write(docTree)
        }
        
        Logger.logElapsedTime("Saved DocumentSet to DB", t2)
        progAbort(Progress(1, Done))
      }
    }
  }
}
