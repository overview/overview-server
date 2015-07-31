package org.overviewproject.jobhandler.filegroup

import org.specs2.specification.Scope
import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.TaskWorkerTask

class RoundRobinTaskQueueSpec extends Specification {

  "RoundRobinTaskQueue" should {

    "add and remove tasks" in new TaskContext {

      taskQueue.isEmpty must beTrue

      taskQueue.addTasks(Seq(task))
      taskQueue.isEmpty must beFalse

      taskQueue.dequeue must be equalTo (task)

      taskQueue.isEmpty must beTrue
    }

    "add a single task" in new TaskContext {
      taskQueue.addTask(task)

      taskQueue.dequeue must be equalTo (task)
    }

    "dequeue all tasks matching a predicate" in new MultipleTasksContext {
      taskQueue.addTasks(tasks)

      val evenTasks = taskQueue.removeAll(t => (t.documentSetId % 2) == 0)
      val remainder = taskQueue.removeAll(_ => true)

      evenTasks must be equalTo tasks.filter(t => (t.documentSetId % 2) == 0)
      remainder must be equalTo tasks.filter(t => (t.documentSetId % 2) == 1)

      taskQueue.isEmpty must beTrue
    }

    "dequeue tasks in round robin order, based on document set id" in new MultipleDocumentSetsContext {
      taskQueue.addTasks(tasks1)
      taskQueue.addTasks(tasks2)
      taskQueue.addTasks(tasks3)

      taskQueue.dequeue must be equalTo tasks1.head
      taskQueue.dequeue must be equalTo tasks2.head
      taskQueue.dequeue must be equalTo tasks3.head
      taskQueue.dequeue must be equalTo tasks1(1)
    }
  }

  trait TaskContext extends Scope {
    val documentSetId = 1l
    val fileGroupId = 2l

    val taskQueue = new RoundRobinTaskQueue
    val task = TestTask(documentSetId, fileGroupId, 1)
  }

  trait MultipleTasksContext extends TaskContext {
    val tasks = Seq.tabulate(10)(TestTask(_, fileGroupId, 1))
  }

  trait MultipleDocumentSetsContext extends TaskContext {
    val docSet1 = 1l
    val docSet2 = 2l
    val docSet3 = 3l

    val tasks1 = Seq.tabulate(3)(TestTask(docSet1, fileGroupId, _))
    val tasks2 = Seq.tabulate(3)(TestTask(docSet2, fileGroupId, _))
    val tasks3 = Seq.tabulate(3)(TestTask(docSet3, fileGroupId, _))
  }
  case class TestTask(documentSetId: Long, fileGroupId: Long, taskId: Int) extends TaskWorkerTask
}