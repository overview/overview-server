package org.overviewproject.jobhandler


/**
 * Contains classes that process files that have been uploaded to a 
 * [[FileGroup]]. Uploads are split into pages with their text extracted, 
 * then converted to [[org.overviewproject.tree.orm.Document]]s, which are clustered into trees by the
 * [[JobHandler]] process.
 * 
 * When all files in a [[FileGroup]] have been uploaded, the `Overview server` 
 * sends a message to the [[FileGroupJobManager]]. The [[FileGroupJobManager]] splits the processing, first 
 * creating a job in the [[FileGroupJobQueue]], and then passing the result to the [[ClusteringQueue]].
 * 
 * Conceptually, each queue is responsible for a certain type of task, potentially interacting with one or more
 * worker processes that complete the task.
 * 
 * @todo [[FileGroupJobManager]] and the queues should not run in the same process (or instance) as  the
 *  [[DocumentSetHandler]]s. [[FileGroupTaskWorker]]s should also run in a separate JVM on a remote instance.
 *  
 * @todo [[FileGroupJobManager]] should receive akka messages instead of listening to the message broker.
 * 
 * @todo Convert [[ClusteringJobQueue]] to a real job queue, instead of a proxy that sends jobs to the clustering 
 * worker via the database.
 * 
 * 
 */
package object filegroup {

}