require [
  'jquery'
  'i18n'
  'apps/Tree/views/node_form_view'
], ($, i18n, NodeFormView) ->
  describe 'views/node_form_view', ->
    describe 'NodeFormView', ->
      node = undefined
      view = undefined
      actions = undefined
      old_fx = $.fx

      beforeEach ->
        old_fx = $.fx
        $.fx = false

        i18n.reset_messages({
          'views.Node._form.h3': 'h3'
          'views.Node._form.labels.description': 'Description'
          'views.Node._form.close': 'Close'
          'views.Node._form.submit': 'Submit'
        })

        node = { id: 3, description: 'description' }
        view = new NodeFormView(node)

      afterEach ->
        remove_view()
        delete window.i18n
        $.fx = old_fx

      remove_view = () ->
        $form = $('#node-form-view-dialog')
        $form.modal('hide')
        $form.remove()
        $('.modal-backdrop').remove()

      actions = {
        submit: () -> $(view.form).submit()
        close: () -> $(view.form).modal('hide')
        set_description: (description) ->
          $input = $('input[name=description]', view.form)
          $input.val(description)
          $input.change()
      }

      it 'should have a "description" input with the start description', ->
        $input = $('input[name=description]', view.form)
        expect($input.val()).toEqual('description')

      it 'should trigger "change" on change', ->
        val = undefined
        view.observe('change', (node) -> val = node)
        actions.set_description('description 2')
        actions.submit()
        expect(val).toEqual({ id: 3, description: 'description 2' })

      it 'should not change the existing node on change', ->
        actions.set_description('description 2')
        actions.submit()
        expect(node.description).toEqual('description')

      it 'should hide after "change" and automatically trigger "closed"', ->
        i = 1
        val1 = undefined
        val2 = undefined
        view.observe('change', () -> val1 = i++)
        view.observe('closed', () -> val2 = i++)
        actions.set_description('description 2')
        actions.submit()
        expect(val1).toEqual(1)
        expect(val2).toEqual(2)

      it 'should trigger "closed" when close is clicked', ->
        closed = false
        view.observe('closed', -> closed = true)
        actions.close()
        expect(closed).toBe(true)

      it 'should not trigger "change" when close is clicked', ->
        change = false
        view.observe('change', -> changed = true)
        actions.close()
        expect(closed).toBe(false)
