/**
 * RetrieveDocumentsSetSpec.scala
 * 
 * Unit tests for document set retrieval
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

import java.io.File
import java.sql.Connection
import scala.Array.canBuildFrom
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import anorm.SQL
import anorm.SqlParser.{flatten, long}
import org.overviewproject.clustering.{BuildDocTree, UnigramDocumentVectorGenerator, Lexer}
import org.overviewproject.http.{ AsyncHttpRequest, BulkHttpRetriever, DocRetrievalError, DocumentAtURL }
import org.overviewproject.test.DbSpecification
import org.overviewproject.util.WorkerActorSystem


class RetrieveDocumentSetSpec extends DbSpecification {
  
  step(setupDb)
/*
  def findNodeDocuments(implicit c: Connection) : Seq[(Long, Long)] = {
    SQL("SELECT node_id, document_id FROM node_document").
     as(long("node_id") ~ long("document_id") map(flatten) *)
  }
*/
  "DocumentSetIndexer" should {

    // turn every doc in the test directory into a file:// URL
    def makeDocURLs = {
      val filenames =  new File("worker/src/test/resources/docs").listFiles.sorted
      filenames.map(fname => DocumentAtURL("file://" + fname.getAbsolutePath))
    }
    
    // Retrieve a list of documents, returning the document vector generator and the list of retrieval errors
    def retrieveDocs(docURLs:Seq[DocumentAtURL]) : Pair[UnigramDocumentVectorGenerator, Seq[DocRetrievalError]] = {
      var result = Seq[DocRetrievalError]() 
      
      val vectorGen = new UnigramDocumentVectorGenerator      
      def processDocument(doc: DocumentAtURL, text:String) : Boolean = {
        vectorGen.addDocument(docURLs.indexOf(doc), Lexer.makeTerms(text))
        true
      }      

      val timeOut = Duration("500ms")  // ms. We're reading from files here so 500ms should be plenty
      val bulkHttpRetriever = new BulkHttpRetriever[DocumentAtURL](new AsyncHttpRequest)
      
      WorkerActorSystem.withActorSystem { implicit context =>
        // Successful retrieval. Check the generated tree (will change if test file set changes)
        val retrievalDone = bulkHttpRetriever.retrieve(docURLs, (doc,text) => processDocument(doc, text)) 
        result = Await.result(retrievalDone.future, timeOut)
      }
      
      (vectorGen, result)
    }

    "retrieve a local document set" in {
      val docURLs = makeDocURLs
      val (vectorGen, retrievalErrors) = retrieveDocs(docURLs)

      retrievalErrors.size must beEqualTo(0) // everything should be retrieved
    }

    "return a list of documents that cannot be retrieved" in {
      val docURLs = makeDocURLs
      val errURL = DocumentAtURL("file:///xyzzy")   // file doesn't exist, so this document should end up in the retrievalErrors
      val docURLsWithErr = docURLs :+ errURL

      val (vectorGen, retrievalErrors) = retrieveDocs(docURLsWithErr)

    	retrievalErrors.size must beEqualTo(1) // exactly one doc should be fail
    	retrievalErrors.head.documentUrl must beEqualTo(errURL.textURL)
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
