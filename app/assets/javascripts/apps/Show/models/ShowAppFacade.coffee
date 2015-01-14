define [
], () ->
  class ShowAppFacade
    constructor: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.tags, a Tags' if !options.tags

      @state = options.state
      @tags = options.tags

    resetDocumentListParams: -> @state.resetDocumentListParams()
    getTag: (cid) -> @tags.get(cid)
