package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.DocumentSet

object DocumentSetFinder extends FinderById[DocumentSet](Schema.documentSets)