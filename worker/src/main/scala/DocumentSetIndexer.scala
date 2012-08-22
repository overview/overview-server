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
import overview.util.Logger
import overview.util.Progress._
import persistence.{DocumentWriter, NodeWriter}
import database.DB

import akka.actor._
import akka.dispatch.{Future,Promise,Await}
import akka.util.Timeout

 class DocumentSetIndexer(sourceDocList : Traversable[DCDocumentAtURL],
                          nodeWriter : NodeWriter, 
                          documentWriter : DocumentWriter,
                          progAbort : ProgressAbortFn ) {

  private var percentFetched = 0
  private val fetchingPercent = 90.0   // what percent done do we say when we're all done fetching docs?
  
  private def logElapsedTime(op:String, t0 : Long) {
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
    progAbort(Progress(vectorGen.numDocs * fetchingPercent / sourceDocList.size, "Retrieving documents"))
  }
  
  def BuildTree() : Unit = {
    
    val t0 = System.nanoTime()
 
    // Retrieve all that stuff!
    val retrievalDone = BulkHttpRetriever[DCDocumentAtURL]( sourceDocList,
                                                            (doc,text) => processDocument(doc, text) ) 
    
    // Now, wait on this thread until all docs are in 
    val docsNotFetched = Await.result(retrievalDone, Timeout.never.duration) 
    logElapsedTime("Retrieved" + vectorGen.numDocs + " documents, with " + docsNotFetched.length + " not fetched", t0)        
    
    // Cluster (build the tree)
    progAbort(Progress(fetchingPercent, "Clustering documents"))        
    val t1 = System.nanoTime()
    val docVecs = vectorGen.documentVectors()
    val docTree = BuildDocTree(docVecs)  // TODO progress while clustering
    logElapsedTime("Clustered documents", t1)
        
    // Save tree to database
    progAbort(Progress(99, "Saving document tree"))
    val t2 = System.nanoTime()
    DB.withTransaction { implicit connection =>
     nodeWriter.write(docTree)
    }
    logElapsedTime("Saved DocumentSet to DB", t2)
    
    progAbort(Progress(100, "Done"))
  } 
}