/***
 * JobHandler.scala
 * 
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */


import com.jolbox.bonecp._

import database.{DatabaseConfiguration, DataSource, DB}
import persistence._
import persistence.DocumentSetCreationJobState._

import overview.logging._
import overview.clustering._

object JobHandler {
  def main(args: Array[String]) {

    val config = new DatabaseConfiguration()
	  val dataSource = new DataSource(config)
	
    DB.connect(dataSource)
    
	  while (true) {
      Thread.sleep(500) 
      

      val submittedJobs: Seq[PersistentDocumentSetCreationJob] = DB.withConnection { 
        implicit connection =>
          PersistentDocumentSetCreationJob.findAllSubmitted
      }

      for (j <- submittedJobs) {
        val documentSetId = j.documentSetId

        val documentWriter = new DocumentWriter(documentSetId)
        val nodeWriter = new NodeWriter(documentSetId)
        Logger.error("fix indexer")
//        val indexer = 
//          new DocumentSetIndexer(new DocumentCloudSource(j.query), nodeWriter, documentWriter)
//        
//        val tree = indexer.BuildTree()

        j.state = Complete
        DB.withConnection { implicit connection =>
          j.update
        }
      }
      
    }
  }
}
