Cache = require('models/cache').Cache
State = require('models/state').State

class World
  constructor: () ->
    @cache = new Cache()
    @state = new State()

    this._handle_tag_id_change()

  _handle_tag_id_change: () ->
    @cache.tag_store.observe('tag-id-changed', this.rewrite_tag_id.bind(this))
    
  rewrite_tag_id: (old_tagid, tag) =>
    @cache.document_store.rewrite_tag_id(old_tagid, tag.id)
    @cache.on_demand_tree.rewrite_tag_id(old_tagid, tag.id)

    index = @state.selection.tags.indexOf(old_tagid)
    if index != -1
      selection = @state.selection.replace({
        tags: @state.selection.tags.slice(0).splice(index, 1, tag.id)
      })
      @state.set('selection', selection)

exports = require.make_export_object('models/world')
exports.World = World
