observable = require('models/observable').observable

class TagStore
  observable(this)

  constructor: () ->
    @tags = []

  add: (tag) ->
    throw 'tagAlreadyExists' if @tags.some((v) -> v.name == tag.name)
    @tags.push(tag)
    @tags.sort((a, b) -> a.name.toLocaleLowerCase().localeCompare(b.name.toLocaleLowerCase()))

    t.position = i for t, i in @tags

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
    for k, v of map
      if !v?
        tag[k] = undefined
      else
        tag[k] = JSON.parse(JSON.stringify(v))

    this._notify('tag-changed', tag)
    tag

  find_tag_by_name: (name) ->
    _.find(@tags, (v) -> v.name == name)

exports = require.make_export_object('models/tag_store')
exports.TagStore = TagStore
