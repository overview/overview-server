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

  /** Allows any user with access to the given DocumentSet ID. */
  def userOwningDocumentSet(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.isAllowedDocumentSet(id)
  }

  /** Allows any user with access to the given Document ID. */
  def userOwningDocument(id: Long) = new Authority {
    def apply(user: OverviewUser) = user.isAllowedDocument(id)
  }
}
