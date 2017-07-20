define [
  'backbone'
  '../models/Document'
], (Backbone, Document) ->
  # A sorted list of Document objects.
  #
  # Instances are created and managed by a DocumentList. The DocumentList alone
  # knows the total length of the list from the server's point of view. This
  # Backbone Collection merely holds the already-fetched Document objects. When
  # `documents.length == documentList.length`, the list is fully-fetched.
  class Documents extends Backbone.Collection
    model: Document
