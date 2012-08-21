observable = require('models/observable').observable
ColorTable = require('views/color_table').ColorTable

TAG_KEY = 'overview-tag'

class TagListView
  observable(this)

  constructor: (@div, @tag_list) ->
    @color_table = new ColorTable()
    this._init_html()
    this._observe_tag_add()
    this._observe_tag_remove()

  _init_html: () ->
    $div = $(@div)
    $ul = $('<ul class="btn-toolbar"></ul>')
    $form_li = $('<li class="btn-group"></li>')
    $form_li.append(this._create_form())
    $ul.append($form_li)
    $div.append($ul)

    notify = this._notify.bind(this)

    $ul.on 'click', 'a.tag-add', (e) ->
      e.preventDefault()
      $li = $(this).closest('li')
      tag = $li.data(TAG_KEY)
      notify('add-clicked', tag)

    $ul.on 'click', 'a.tag-remove', (e) ->
      e.preventDefault()
      $li = $(this).closest('li')
      tag = $li.data(TAG_KEY)
      notify('remove-clicked', tag)

  _create_form: () ->
    $form = $('<form method="post" action="#" class="input-append"><input type="text" name="tag_name" placeholder="New tag" class="input-mini" /><input type="submit" value="Tag" class="btn" /></form')
    $form.on 'submit', (e) =>
      e.preventDefault()
      $input = $form.find('input[name=tag_name]')
      name = $.trim($input.val())
      if name.length > 0
        this._create_or_add_tag(name)
      $input.val('')

  _observe_tag_add: () ->
    @tag_list.observe 'tag-added', (obj) =>
      this._add_tag(obj)

  _create_or_add_tag: (name) ->
    tag = @tag_list.find_tag_by_name(name)
    if tag?
      this._notify('add-clicked', tag)
    else
      this._notify('create-submitted', { name: name })

  _add_tag: (tag) ->
    $li = $('<li class="btn-group"><a class="btn tag-name"></a><a class="btn tag-add" alt="add tag to selection" title="add tag to selection"><i class="icon-plus"></i></a><a class="btn tag-remove" alt="remove tag from selection" title="remove tag from selection"><i class="icon-minus"></i></a></li>')
    $li.data(TAG_KEY, tag)
    $li.find('.tag-name').text(tag.name)
    $li.find('.btn').addClass(@color_table.get_tag_color_class(tag.name))

    $ul = $('ul', @div)
    $li.insertBefore($ul.children()[tag.position])

  _observe_tag_remove: () ->
    @tag_list.observe 'tag-removed', (tag) =>
      this._remove_tag(tag)

  _remove_tag: (tag) ->
    $ul = $('ul', @div)
    $li = $($ul.children()[tag.position])
    $li.remove()

exports = require.make_export_object('views/tag_list_view')
exports.TagListView = TagListView
