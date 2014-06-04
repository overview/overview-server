package controllers.auth

import models.OverviewUser

object Authorities {
  /** Allows any user. */
  def anyUser = new Authority {
    def apply(user: OverviewUser) = true
  }

  /** Allows only admin users. */
  def adminUser = new Authority {
    def apply(user: OverviewUser) = user.isAdministrator
  }

  /** Allows any user who is owner of the given DocumentSet ID. */
  def userOwningDocumentSet(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.ownsDocumentSet(id)
  }
  
  /** Allows any user who is owner of the DocumentSet associated with the given Tree ID */
  def userOwningTree(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.isAllowedTree(id)
  }

  /** Allows any user who is owner of the given DocumentSet ID and Tree ID  */
  def userOwningDocumentSetAndTree(documentSetId: Long, treeId: Long) = new Authority {
    def apply(user: OverviewUser) = user.ownsDocumentSet(documentSetId) && user.isAllowedTree(treeId)
  }
  
  /** Allows any user who is a viewer of the given DocumentSet ID. */
  def userViewingDocumentSet(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.canViewDocumentSet(id)
  }

  /** Allows any user with any role for the given Document ID. */
  def userOwningDocument(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.isAllowedDocument(id)
  }

  def userOwningJob(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.isAllowedJob(id)
  }
}
