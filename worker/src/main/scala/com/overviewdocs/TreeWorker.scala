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

  private def handleTrees: Unit = {
    import database.api._
    blockingDatabase.seq(Trees.filter(_.progress =!= 1.0))
      .foreach { tree => new Runner(tree).runBlocking }
  }
}
