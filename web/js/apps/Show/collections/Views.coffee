define [
  'backbone'
  '../models/View'
], (Backbone, View) ->
  PollDelay = 1000 # ms

  class Views extends Backbone.Collection
    model: View
    modelId: (attrs) -> "#{attrs.type}-#{attrs.id}"

    comparator: (a, b) -> a.attributes.createdAt - b.attributes.createdAt

    initialize: (models, options) ->
      throw 'Must pass options.url, a String like "/documentsets/3/views"' if !options?.url
      @url = options.url

    # Poll every once in a while until all jobs are complete.
    pollUntilStable: ->
      @_pollTimeout ||= window.setTimeout((=> @_doPoll()), PollDelay)

    _doPoll: ->
      if @some((v) -> v.get('type') == 'tree' && v.get('progress') != 1.0)
        onComplete = =>
          @_pollTimeout = null
          @pollUntilStable()

        @fetch(remove: false).then(onComplete, onComplete)
      else
        @_pollTimeout = null
