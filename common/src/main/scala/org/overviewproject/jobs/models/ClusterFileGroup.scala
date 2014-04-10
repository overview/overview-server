package org.overviewproject.jobs.models

case class ClusterFileGroup(
    fileGroupId: Long,
    name: String,
    lang: String,
    stopWords: String,
    importantWords: String)
