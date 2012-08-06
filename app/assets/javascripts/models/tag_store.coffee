observable = require('models/observable').observable

class TagStore
  observable(this)

  constructor: () ->
    @tags = []

  add: (tag) ->
    throw 'tagAlreadyExists' if @tags.some((v) -> v.name == tag.name)
    @tags.push(tag)
    @tags.sort((a, b) -> a.name.toLocaleLowerCase().localeCompare(b.name.toLocaleLowerCase()))
    position = @tags.indexOf(tag)
    this._notify('tag-added', { position: position, tag: tag })

  remove: (tag) ->
    position = @tags.indexOf(tag)
    throw 'tagNotFound' if position == -1
    @tags.splice(position, 1)
    this._notify('tag-removed', { position: position, tag: tag })

  find_tag_by_name: (name) ->
    _.find(@tags, (v) -> v.name == name)

exports = require.make_export_object('models/tag_store')
exports.TagStore = TagStore
