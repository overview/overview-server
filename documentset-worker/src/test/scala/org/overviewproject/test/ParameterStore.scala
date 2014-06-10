package org.overviewproject.test

import akka.agent._
import akka.testkit._
import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.specs2.matcher.MatchResult

/**
 * When testing Actor behavior, Mockito can't be used to test calls to mocked out components (like database storage).
 * If the mock update is occurring on a different thread than the test, the test result may become unpredictable.
 * `ParameterStore` provides a synchronous mechanism to store and check method calls.
 * `store` is used in the test method to store the parameters in the call. The `was*` methods wait for all outstanding 
 * update requests to complete before checking the value.
 */
class ParameterStore[A] extends Specification {
  import ExecutionContext.Implicits.global
  
  private val storedParameters: Agent[Queue[A]] = Agent(Queue.empty)
  
  def store(parameters: A): Unit = storedParameters send (_.enqueue(parameters))
  
  def wasCalledWith(parameters: A) = storedParameters.future must contain(parameters).await
  def wasCalledWithMatch[B](f: A => MatchResult[B]) = storedParameters.future must contain(f).await
    
  def wasCalledNTimes(n: Int) = storedParameters.future.map(_.length) must be_==(n).await
  
  def wasLastCalledWithMatch[B](f: A => MatchResult[B]) = storedParameters.future.map(_.lastOption) must beSome(f).await
}

object ParameterStore {
  def apply[A] = new ParameterStore[A]
}
