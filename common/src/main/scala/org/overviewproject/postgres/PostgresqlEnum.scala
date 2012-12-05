package org.overviewproject.postgres

import org.squeryl.customtypes.StringField

class PostgresqlEnum(v: String, val typeName: String) extends StringField(v)
