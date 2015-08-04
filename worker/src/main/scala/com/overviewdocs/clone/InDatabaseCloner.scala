package com.overviewdocs.clone

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.util.Logger

trait InDatabaseCloner extends HasBlockingDatabase {
  protected val logger: Logger = Logger.forClass(getClass)
  protected val DocumentSetIdMask: Long = 0x00000000FFFFFFFFL
}
