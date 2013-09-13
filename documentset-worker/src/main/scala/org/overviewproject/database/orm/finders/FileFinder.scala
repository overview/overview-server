package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.File

object FileFinder extends FinderById[File](Schema.files) 