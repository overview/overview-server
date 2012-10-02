observable = require('models/observable').observable
ColorTable = require('views/color_table').ColorTable

# A modal dialog that allows editing a tag.
#
# Usage:
#
#   tag = { id: 1, name: 'foo', color: '#ffff00' }
#   view = new TagFormView(tag)
#   view.observe('change', (tag) -> ...) # this tag is a modified shallow copy
#   view.observe('delete', () -> ...)
#   view.observe('closed', () -> view = undefined) # remove all references
#
# (These events are properly-named: "change" and "delete" are imperative,
# meaning the caller should do something; "closed" is past-tense, meaning the
# caller should respond.)
class TagFormView
  observable(this)

  constructor: (@tag) ->
    $div = $(this._create_div_string())
    $('body').append($div)
    $div.modal()

    $div.on 'hidden', () =>
      $div.remove()
      this._notify('closed')

    $div.find('form.form-horizontal').on 'submit', (e) =>
      e.preventDefault()
      new_tag = this._build_tag_from_form()
      this._notify('change', new_tag)
      $div.modal('hide')

    $div.find('form.delete').on 'submit', (e) =>
      e.preventDefault()
      e.stopPropagation() # So form-with-confirm doesn't run

      $form = $(e.target)
      message = $form.attr('data-confirm')

      if !message || window.confirm(message)
        this._notify('delete')
        $div.modal('hide')

    $div.find('.btn-primary').on 'click', (e) ->
      $div.find('form.form-horizontal').submit()

    @div = $div[0] # for unit testing

  _build_tag_from_form: () ->
    $form = $('form', @div)

    $name = $form.find('input[name=name]')
    $color = $form.find('input[name=color]')

    name = $name.val()
    color = $color.val().toLowerCase()

    { id: @tag.id, name: name, color: color }

  _create_div_string: () ->
    _.template("""
      <div id="tag-form-view-dialog" class="modal" role="dialog">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal">×</button>
          <h3><%- i18n('views.Tag._form.h3') %></h3>
        </div>
        <div class="modal-body">
          <form class="form-horizontal" method="post" action="#">
            <div class="control-group">
              <label class="control-label" for="tag-form-name"><%- i18n('views.Tag._form.labels.name') %></label>
              <div class="controls">
                <input type="text" name="name" id="tag-form-name" required="required" value="<%- tag.name %>" />
              </div>
            </div>
            <div class="control-group">
              <label class="control-label" for="tag-form-color"><%- i18n('views.Tag._form.labels.color') %></label>
              <div class="controls">
                <input type="color" name="color" id="tag-form-color" required="required" value="<%- tag.color || color_table.get(tag.name) %>" />
              </div>
            </div>
          </form>
        </div>
        <div class="modal-footer">
          <form class="delete form-inline" data-confirm="<%- i18n('views.Tag._form.confirm_delete') %>" action="#">
            <input type="submit" class="btn btn-danger" value="<%- i18n('views.Tag._form.delete') %>" />
          </form>
          <button class="btn" data-dismiss="modal"><%- i18n('views.Tag._form.close') %></button>
          <button class="btn btn-primary"><%- i18n('views.Tag._form.submit') %></button>
        </div>
      </div>""")({ tag: @tag, color_table: new ColorTable() })

exports = require.make_export_object('views/tag_form_view')
exports.TagFormView = TagFormView
