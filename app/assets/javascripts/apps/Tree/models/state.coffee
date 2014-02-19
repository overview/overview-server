define [ 'backbone', './DocumentListParams' ], (Backbone, DocumentListParams) ->
  class State extends Backbone.Model
    defaults:
      # What we want to show in the doclist and filter tagging with
      documentListParams: DocumentListParams.all()

      # Which document is selected/viewed. `null` means all documents in the doclist
      documentId: null

      # Whether we want to show a single document, vs all documents
      #
      # If oneDocument is true and documentId is null, that means we expect
      # documentId will be set soon and we shall view it when it is (and the
      # selection is null in the meantime).
      #
      # If oneDocument is false and documentId is set, that means we _were_
      # showing a document, and we may wish to toggle back to that state later,
      # but right now we are selecting the doclist.
      oneDocumentSelected: false

      # Which tag-like is under view: { tagId: 0 } for untagged, { tagId: X }
      # for a tag, { searchResultId: X } for a search result.
      #
      # We use this to highlight the last-selected tag while navigating nodes.
      # We set it in setDocumentListParams.
      taglike: null

    # Sets new documentListParams and unsets documentId.
    #
    # Without knowledge of what is in the new document list, this is the only
    # safe way to change document lists. Otherwise, you may try to show a
    # document that isn't loaded, leading to undefined behavior.
    setDocumentListParams: (params) ->
      return if params.equals(@get('documentListParams'))

      taglike = if params.type == 'untagged'
        untagged: true
      else if params.type == 'tag'
        tagId: params.tagId
      else if params.type == 'searchResult'
        searchResultId: params.searchResultId
      else
        @get('taglike')

      @set(
        documentListParams: params
        documentId: null
        taglike: taglike
      )

    # Return a DocumentList that describes all documents that will be affected
    # by tagging.
    #
    # Returns { documents: [ -1 ] } if documentId is null and
    # oneDocumentSelected is true>
    getSelection: ->
      if @get('oneDocumentSelected')
        documentId = @get('documentId') || -1
        DocumentListParams.byDocumentId(documentId)
      else
        @get('documentListParams')
