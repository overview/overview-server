/**
 * RetrieveDocumentsSetSpec.scala
 * 
 * Unit tests for document set retrieval
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

import akka.dispatch.{Future,Promise,Await}
import akka.util.Timeout
import anorm._
import anorm.SqlParser._
import java.io.File
import java.sql.Connection
import org.specs2.mutable.Specification
import org.specs2.specification._
import scala.io.Source

import overview.clustering._
import overview.http._
import overview.http.BulkHttpRetriever._
import helpers.DbSpecification
import helpers.DbSetup._
import persistence._

class RetrieveDocumentSetSpec extends DbSpecification {
  
  step(setupDb)

  private def failInsert = { throw new Exception("failed insert") }
  
  def insertDocumentSet(query: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document_set (query) 
        VALUES('NodeWriterSpec')
      """).executeInsert().getOrElse(failInsert)
  }
  
  def findNodeDocuments(implicit c: Connection) : Seq[(Long, Long)] = {
    SQL("SELECT node_id, document_id FROM node_document").
     as(long("node_id") ~ long("document_id") map(flatten) *)
  }

  "DocumentSetIndexer" should {

    "retrieve a local document set" in {
            
      // turn every doc in the test directory into a file:// URL
      val filenames =  new File("worker/src/test/resources/docs").listFiles.sorted
      val docURLs = filenames.map(fname => DocumentAtURL("file://" + fname.getAbsolutePath))

      val vectorGen = new DocumentVectorGenerator      
      def processDocument(doc: DocumentAtURL, text:String) : Unit = {
        vectorGen.addDocument(docURLs.indexOf(doc), Lexer.makeTerms(text))          
      }      

      val timeOut = Timeout(500)   // ms                                               
      
      // Successful retrieval. Check the generated tree (will change if test file set changes)
      val retrievalDone = BulkHttpRetriever[DocumentAtURL](docURLs,
                                                           (doc,text) => processDocument(doc, text) ) 
      val result = Await.result(retrievalDone, timeOut.duration)

      result.size must beEqualTo(0) // everything should be retrieved
      val docTree = BuildDocTree(vectorGen.documentVectors()).toString      
      docTree must beEqualTo("(0,1,2,3,4,5,6,7,8, (0,4,5, (0,4, (0), (4)), (5)), (3,7,8, (7,8, (7), (8)), (3)), (1), (2), (6))")
      
      // Failed retrieval: add a URL that doesn't exist, test that the error is returned to us
      val errURL = DocumentAtURL("file:///xyzzy")
      val docURLsWithErr = docURLs :+ errURL
      val retrievalErr = BulkHttpRetriever[DocumentAtURL](docURLsWithErr,
                                                          (doc,text) => processDocument(doc, text) ) // adds to existing vectorGen, whatevs
      val resultErr = Await.result(retrievalErr, timeOut.duration)

      resultErr.size must beEqualTo(1) // everything should be retrieved
      resultErr.head.doc must beEqualTo(errURL)                                                    
    }

/*
   "retrieve local documents, index, and store to database" in new DbTestContext {
     
      val filenames =  new File("worker/src/test/resources/docs").listFiles
      var docURLs = filenames.map(fname => new DCDocumentAtURL("title", "display URL", "file://" + fname.getAbsolutePath))      

      val documentSetId = insertDocumentSet("NodeWriterSpec")          
      val documentWriter = new DocumentWriter(documentSetId)
      val nodeWriter = new NodeWriter(documentSetId)
      val indexer = new DocumentSetIndexer(docURLs, nodeWriter, documentWriter)
      
      indexer.BuildTree
      
      // Check that nine documents were written to the database
      val docNodes = findNodeDocuments
      docNodes.toString must beEqualTo("foo!")
   }
*/ 
  }    
   
  step(shutdownDb)
}