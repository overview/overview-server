define [
  'jquery'
  'underscore'
  'i18n'
  '../models/observable'
], ($, _, i18n, observable) ->
  # A modal dialog that allows editing a node.
  #
  # Usage:
  #
  #   node = { id: 3, description: 'description' }
  #   view = new NodeFormView(node)
  #   view.observe('change', (node) -> ...) # this node is a modified shallow copy
  #   view.observe('closed', () -> view = undefined) # remove all references
  #
  # (These events are properly-named: "change" is imperative, meaning the caller
  # should do something; "closed" is past-tense, meaning the caller should react.)
  class NodeFormView
    observable(this)

    constructor: (@node) ->
      $form = $(this._create_form_string())
      $('body').append($form)

      $form.modal()
      $form.find('input[type=text]:eq(0)').focus()

      $form.on 'submit', (e) =>
        e.preventDefault()
        new_node = this._build_node_from_form()
        this._notify('change', new_node)
        $form.modal('hide')

      $form.on 'hidden', () =>
        $form.remove()
        this._notify('closed')

      @form = $form[0] # for unit testing

    _build_node_from_form: () ->
      $description = $('input[name=description]', @form)
      description = $description.val()

      _.defaults({ description: description }, @node)

    _create_form_string: () ->
      _.template("""
        <form id="node-form-view-dialog" class="modal" role="dialog" action="#" method="post">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h3><%- i18n('views.Node._form.h3') %></h3>
          </div>
          <div class="modal-body">
            <div class="form-horizontal">
              <div class="control-group">
                <label class="control-label" for="node-form-description"><%- i18n('views.Node._form.labels.description') %></label>
                <div class="controls">
                  <input type="text" name="description" id="node-form-description" required="required" value="<%- node.description %>" />
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <input type="reset" class="btn" data-dismiss="modal" value="<%- i18n('views.Node._form.close') %>" />
            <input type="submit" class="btn btn-primary" value="<%- i18n('views.Node._form.submit') %>" />
          </div>
        </form>""")({ i18n: i18n, node: @node })
