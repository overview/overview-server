package org.overviewproject.clone

import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.util.Logger

trait InDatabaseCloner extends HasBlockingDatabase {
  protected val logger: Logger = Logger.forClass(getClass)
  protected val DocumentSetIdMask: Long = 0x00000000FFFFFFFFL
}
