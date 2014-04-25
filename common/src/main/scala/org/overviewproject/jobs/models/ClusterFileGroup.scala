package org.overviewproject.jobs.models

case class ClusterFileGroup(
    documentSetId: Long,
    fileGroupId: Long,
    name: String,
    lang: String,
    stopWords: String,
    importantWords: String)
