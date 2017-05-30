package com.overviewdocs

import com.overviewdocs.clustering.Runner
import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.tables.Trees

class TreeWorker extends Runnable with HasBlockingDatabase {
  override def run: Unit = {
    val pollingInterval = 500 //milliseconds

    while (true) {
      handleTrees
      Thread.sleep(pollingInterval)
    }
  }

  import database.api._

  private lazy val pendingTreesCompiled = Compiled {
    Trees.filter(_.progress =!= 1.0)
  }

  private def handleTrees: Unit = {
    blockingDatabase.seq(pendingTreesCompiled)
      .foreach { tree => new Runner(tree).runBlocking }
  }
}
