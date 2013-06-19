define [
  'underscore'
  '../collections/TagStoreProxy'
  '../views/TagList'
  'i18n'
], (_, TagStoreProxy, TagListView, i18n) ->

  t = (key, args...) -> i18n("views.DocumentSet.show.tag_list.#{key}", args...)

  template = _.template("""
    <div class="modal fade">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h3><%- t('header') %></h3>
      </div>
      <div class="modal-body"></div>
      <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
      </div>
    </div>
  """)

  # Opens a dialog showing the tags in the tag store.
  #
  # This dialog allows edits. It will be closed when the user clicks "close".
  (tagStore, cache) ->
    tagToCount = (tag) ->
      root = cache.on_demand_tree.get_root()
      tagCounts = root?.tagcounts
      tagCounts?[tag.id] || 0

    tagStoreProxy = new TagStoreProxy(tagStore)

    view = new TagListView({
      collection: tagStoreProxy.collection,
      tagToCount: tagToCount
      exportUrl: window.location.pathname + "/tags.csv" # TODO routing in JS
    })

    view.on 'add', (attrs) ->
      tag = cache.add_tag(attrs)

      cache.create_tag(tag, {
        beforeReceive: tagStoreProxy.setChangeOptions({ interacting: true })
      }).done ->
        tagStoreProxy.setChangeOptions({})

    view.on 'update', (model, attrs) ->
      tag = tagStoreProxy.unmap(model)
      tagStoreProxy.setChangeOptions({ interacting: true })
      cache.update_tag(tag, attrs)
      tagStoreProxy.setChangeOptions({})

    view.on 'remove', (model) ->
      tag = tagStoreProxy.unmap(model)
      cache.delete_tag(tag)

    $dialog = $(template({ t: t }))
    $dialog.find('.modal-body').append(view.el)

    $('body').append($dialog)
    $dialog.modal()
    $dialog.on 'hidden', ->
      tagStoreProxy.destroy()
      view.remove()
      $dialog.remove()
