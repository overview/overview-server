/***
 * JobHandler.scala
 * 
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

import com.jolbox.bonecp._

import database.{DatabaseConfiguration, DataSource, DB}
import overview.clustering._
import overview.util.Logger
import overview.util.Progress._
import persistence._
import persistence.DocumentSetCreationJobState._

object JobHandler {
 
  // Run a single job
  def handleSingleJob(j:PersistentDocumentSetCreationJob) : Unit = {
    j.state = InProgress
    DB.withConnection { implicit connection =>
      j.update
    }
    val documentSetId = j.documentSetId

    val documentWriter = new DocumentWriter(documentSetId)
    val nodeWriter = new NodeWriter(documentSetId)
    def progFn(prog:Progress) = { 
      println("PROGRESS: " + prog.percent + "% done. " + prog.status + ", " + (if (prog.hasError) "ERROR" else "OK")) ; false 
    }
    val query = DB.withConnection { implicit connection => 
      DocumentSetLoader.loadQuery(j.documentSetId).get
    } 
   
    val indexer = new DocumentSetIndexer(new DocumentCloudSource(query), nodeWriter, documentWriter, progFn)

    Logger.info("Indexing query: " + query)
    val tree = indexer.BuildTree()

    j.state = Complete
    DB.withConnection { implicit connection =>
      j.update
    }
  }
    
  // Run each job currently listed in the database
  def scanForJobs : Unit  = {
  
    val submittedJobs: Seq[PersistentDocumentSetCreationJob] = DB.withConnection { implicit connection =>
      PersistentDocumentSetCreationJob.findAllSubmitted
    }
  
    for (j <- submittedJobs) {
      handleSingleJob(j)
    }
  }
  
  def main(args: Array[String]) {

    val pollingInterval = 500 //milliseconds

    val config = new DatabaseConfiguration()
	  val dataSource = new DataSource(config)
	
    DB.connect(dataSource)
    
    while (true) {
      scanForJobs
      Thread.sleep(pollingInterval)
    }
  }
  
}
