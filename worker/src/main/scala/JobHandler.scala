
import scala.collection.JavaConversions._
import scala.io.Source

import com.avaje.ebean.Ebean;



import models.{DocumentSet,DocumentSetCreationJob}
import models.DocumentSetCreationJob.JobState

object JobHandler {
  def main(args: Array[String]) {
    
    while (true) {
      Thread.sleep(500)
      val submittedJobs = DocumentSetCreationJob.find.where.eq("state", JobState.Submitted).findList.toSeq
      
      for (j <- submittedJobs) {
    	println(j.query)
        println(j.state)
        j.setState(JobState.InProgress);
        
        j.save
        val documentSet = new DocumentSet
        documentSet.setQuery(j.query)
        documentSet.save
        println("documentSet: " + documentSet.query)
        
        val indexer = new DocumentSetIndexer(documentSet)
        indexer.createDocuments
        indexer.indexDocuments
        
        j.setState(JobState.Complete)
        j.save
      }
      
    }

  }


}
