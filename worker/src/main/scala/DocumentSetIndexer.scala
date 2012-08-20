/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 * 
 * Overview Project, created August 2012
 * 
 * @author Jonathan Stray
 * 
 */

package overview.clustering 

import models._
import overview.clustering.ClusterTypes._
import overview.http._
import overview.logging._
import persistence.{DocumentWriter, NodeWriter}
import database.DB

import akka.dispatch.{Future,Promise}



 class DocumentSetIndexer(sourceDocList : Traversable[DCDocumentAtURL],
                          nodeWriter : NodeWriter, 
                          documentWriter : DocumentWriter) {

  private def printElapsedTime(op:String, t0 : Long) {
    val t1 = System.nanoTime()
    Logger.info(op + ", time: " + ("%.2f" format (t1 - t0)/1e9) + " seconds")
  }

  private val vectorGen = new DocumentVectorGenerator
  
  // When we get the document text back, we add the document to the database and feed the text to the vector generator
  private def processDocument(doc: DCDocumentAtURL, text:String) : Unit = {
    val documentId = DB.withConnection { 
      implicit connection => documentWriter.write(doc.title, doc.textURL, doc.viewURL)
    }
    vectorGen.addDocument(documentId, Lexer.makeTerms(text))          
  }
  
  def BuildTree() = {
    val t0 = System.nanoTime()
    
 
    val retrievalDone = BulkHttpRetriever[DCDocumentAtURL]( sourceDocList,
                                                            (doc,text) => processDocument(doc, text) ) 
    
   // When the docsetRetriever finishes, compute vectors and complete the promise
    retrievalDone onComplete {
      case Left(error) => 
        Logger.warn("Document set retrieval error: " + error)
        
      case Right(docsNotFetched) => 
        Logger.info("Document set retrieval succeded, with " + docsNotFetched.length + " not fetched")
        
        val docVecs = vectorGen.documentVectors()
        printElapsedTime("Retrieved and indexed " + docVecs.size + " documents", t0)        

        val t1 = System.nanoTime()
        val docTree = BuildDocTree(docVecs)
        printElapsedTime("Clustered documents", t1)
        
        val t2 = System.nanoTime()
        DB.withTransaction { implicit connection =>
         nodeWriter.write(docTree)
        }
        
        printElapsedTime("Saved DocumentSet to DB", t2)
    }
  } 
}