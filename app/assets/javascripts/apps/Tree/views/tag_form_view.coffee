define [
  'jquery'
  'underscore'
  'i18n'
  '../models/observable'
  '../models/color_table'
  'spectrum' # $.fn.spectrum
  'bootstrap-modal' # $.fn.modal
], ($, _, i18n, observable, ColorTable) ->
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
      $form = $(this._create_form_string())
      $('body').append($form)

      $color = $form.find('input[type=color]')
      $color.spectrum({
        preferredFormat: 'hex6'
        showButtons: false
        move: (color) -> $color.spectrum('set', color.toHexString()).change() # Issue #168
      })

      $form.modal()
      $form.find('input[type=text]').focus()

      $form.on 'hidden', () =>
        $color.spectrum('destroy')
        $form.remove()
        this._notify('closed')

      $form.on 'submit', (e) =>
        e.preventDefault()
        new_tag = this._build_tag_from_form()
        this._notify('change', new_tag)
        $form.modal('hide')

      $input_delete = $form.find('input.delete')
      $input_delete.on 'click', (e) =>
        e.preventDefault()

        message = $input_delete.attr('data-confirm')

        if !message || window.confirm(message)
          this._notify('delete')
          $form.modal('hide')

      @form = $form[0] # for unit testing

    _build_tag_from_form: () ->
      $form = $(@form)

      $name = $form.find('input[name=name]')
      $color = $form.find('input[name=color]')

      name = $name.val()
      color = $color.val().toLowerCase()

      { id: @tag.id, name: name, color: color }

    _create_form_string: () ->
      _.template("""
        <form method="get" action="#" id="tag-form-view-dialog" class="modal" role="dialog">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal">×</button>
          <h3><%- i18n('views.Tag._form.h3') %></h3>
        </div>
        <div class="modal-body">
          <div class="form-horizontal" method="post" action="#">
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
          </div>
        </div>
        <div class="modal-footer">
          <input type="reset" class="btn pull-left btn-danger delete" data-dismiss="modal" data-confirm="<%- i18n('views.Tag._form.confirm_delete', tag.name) %>" value="<%- i18n('views.Tag._form.delete') %>" />
          <input type="reset" class="btn" data-dismiss="modal" value="<%- i18n('views.Tag._form.close') %>" />
          <input type="submit" class="btn btn-primary" value="<%- i18n('views.Tag._form.submit') %>" />
        </div>
        </form>""")({ i18n: i18n, tag: @tag, color_table: new ColorTable() })
