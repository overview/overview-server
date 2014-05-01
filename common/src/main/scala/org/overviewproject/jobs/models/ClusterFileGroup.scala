package org.overviewproject.jobs.models

case class ClusterFileGroup(
    documentSetId: Long,
    fileGroupId: Long,
    name: String,
    lang: String,
    splitDocuments: Boolean,
    stopWords: String,
    importantWords: String)
