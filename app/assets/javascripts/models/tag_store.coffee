observable = require('models/observable').observable
ColorTable = require('models/color_table').ColorTable

class TagStore
  observable(this)

  constructor: () ->
    @color_table = new ColorTable()
    @tags = []
    @_last_unsaved_id = 0

  create_tag: (name) ->
    id = @_last_unsaved_id -= 1
    this.add({ id: id, name: name, count: 0, color: @color_table.get(name) })

  _calculate_positions: () ->
    @tags.sort((a, b) -> a.name.toLocaleLowerCase().localeCompare(b.name.toLocaleLowerCase()))
    t.position = i for t, i in @tags
    undefined

  add: (tag) ->
    @tags.push(tag)
    this._calculate_positions()

    this._notify('tag-added', tag)
    tag

  remove: (tag) ->
    position = @tags.indexOf(tag)
    throw 'tagNotFound' if position == -1

    @tags.splice(position, 1)
    t.position = i for t, i in @tags

    this._notify('tag-removed', tag)
    tag

  change: (tag, map) ->
    old_tagid = tag.id

    for k, v of map
      if !v?
        tag[k] = undefined
      else
        tag[k] = JSON.parse(JSON.stringify(v))

    this._calculate_positions()

    this._notify('tag-id-changed', old_tagid, tag) if old_tagid != tag.id
    this._notify('tag-changed', tag)
    tag

  find_tag_by_name: (name) ->
    _.find(@tags, (v) -> v.name == name)

  find_tag_by_id: (id) ->
    ret = _.find(@tags, (v) -> v.id == id)
    throw 'tagNotFound' if !ret
    ret

exports = require.make_export_object('models/tag_store')
exports.TagStore = TagStore
