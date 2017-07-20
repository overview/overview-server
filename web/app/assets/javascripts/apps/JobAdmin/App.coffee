define [
  'underscore',
  'jquery',
  'backbone'
], (_, $, Backbone) ->
  RefreshInterval = 5000 # ms

  class GenericView extends Backbone.View
    template: _.template('''
      <tr data-id="<%- model.id %>">
        <td class="email"><%- model.ownerEmail || '[none]' %></td>
        <td class="progress"><progress value="<%- model.progress %>" title="<%- model.progressDescription %>"></progress></td>
        <td class="document-set-id"><%- model.documentSetId %></td>
        <td class="document-set-title"><%- model.documentSetTitle %></td>
        <td class="delete"><button class="btn btn-danger delete" data-delete-url="<%- model.deleteUrl %>">Delete</button></td>
      </tr>
    ''')

    initialize: ->
      @listenTo(@collection, 'add', @onAdd)
      @listenTo(@collection, 'remove', @onRemove)
      @listenTo(@collection, 'change', @onChange)

    onAdd: (model) ->
      @$el.find('tbody').append(@template(model: model.toJSON()))

    onRemove: (model) ->
      @$el.find("tr[data-id='#{model.id}']").remove()

    onChange: (model) ->
      $tr = @$el.find("tr[data-id='#{model.id}']")
      $tr.find('progress')
        .prop('value', model.get('progress'))
        .attr('title', model.get('progressDescription'))

  class App extends Backbone.View
    events:
      'click button.delete': '_onClickDelete'

    initialize: ->
      @importJobs = new Backbone.Collection([])
      @treeJobs = new Backbone.Collection([])

      @importJobsView = new GenericView(collection: @importJobs, el: @$('#import-jobs'))
      @treeJobsView = new GenericView(collection: @treeJobs, el: @$('#tree-jobs'))

      @fetch()

    fetch: ->
      Backbone.ajax
        url: '/admin/jobs.json'
        success: (data) =>
          @importJobs.set(data.importJobs)
          @treeJobs.set(data.trees)
          window.setTimeout((=> @fetch()), RefreshInterval)
        error: (xhr) =>
          console.warn("Server error during fetch", xhr)
          window.setTimeout((=> @fetch()), RefreshInterval)

    _onClickDelete: (ev) ->
      ev.preventDefault()
      $button = $(ev.currentTarget)
      url = $button.attr('data-delete-url')
      $button
        .prop('disabled', true)
        .html('<i class="icon icon-spinner icon-spin"></i>')
      Backbone.ajax
        type: 'DELETE'
        url: url
