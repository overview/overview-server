package org.overviewproject.jobs.models

case class Delete(documentSetId: Long, waitForJobRemoval: Boolean = false)

