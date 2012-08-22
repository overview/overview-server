/***
 * WorkerActorSystem.scala
 * Singleton Akka actor system object. One per process, managing all actors.
 * 
 * Overview Project,June 2012
 * 
 * @author Jonas Karlsson
 */

package overview

import akka.actor._

object WorkerActorSystem {
  private lazy val context = ActorSystem("WorkerActorSystem") 
  def apply():ActorSystem = context
}