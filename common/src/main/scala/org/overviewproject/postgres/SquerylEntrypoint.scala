package org.overviewproject.postgres

import org.squeryl.PrimitiveTypeMode

object SquerylEntrypoint
  extends PrimitiveTypeMode
  with SquerylPostgresTypes
  with SquerylPostgresFunctions
  with SquerylInsertSelect
