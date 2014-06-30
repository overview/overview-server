package models.orm.finders

import models.orm.Schema
import org.overviewproject.tree.orm.finders.BaseTreeFinder

object TreeFinder extends BaseTreeFinder(Schema.trees)
