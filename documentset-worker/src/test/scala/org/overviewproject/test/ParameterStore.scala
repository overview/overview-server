package org.overviewproject.test

import akka.agent._
import akka.testkit._
import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.specs2.matcher.MatchResult


class ParameterStore[A] extends Specification {
  import ExecutionContext.Implicits.global
  
  private val storedParameters: Agent[Queue[A]] = Agent(Queue.empty)
  
  def store(parameters: A): Unit = storedParameters send (_.enqueue(parameters))
  
  def history: Future[Queue[A]] = storedParameters.future
  
  def wasCalledWith(parameters: A) = storedParameters.future must contain(parameters).await
  def wasCalledWithMatch[B](f: A => MatchResult[B]) = storedParameters.future must contain(f).await
    
  def wasCalledNTimes(n: Int) = storedParameters.future.map(_.length) must be_==(n).await
  
  def wasLastCalledWithMatch[B](f: A => MatchResult[B]) = storedParameters.future.map(_.lastOption) must beSome(f).await
}

object ParameterStore {
  def apply[A] = new ParameterStore[A]
}
