package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.database.orm.Schema

object FileGroupFinder extends FinderById[FileGroup](Schema.fileGroups)