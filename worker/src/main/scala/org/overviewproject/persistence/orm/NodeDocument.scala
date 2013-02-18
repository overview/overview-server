package org.overviewproject.persistence.orm

import org.squeryl.annotations.Column

case class NodeDocument(
    @Column("node_id") nodeId: Long,
    @Column("document_id") documentId: Long) 