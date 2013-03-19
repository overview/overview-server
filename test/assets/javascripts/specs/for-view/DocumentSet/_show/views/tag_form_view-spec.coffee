require [
  'jquery'
  'i18n'
  'for-view/DocumentSet/_show/views/tag_form_view'
], ($, i18n, TagFormView) ->
  describe 'views/tag_form_view', ->
    describe 'TagFormView', ->
      tag = undefined
      view = undefined
      actions = undefined
      old_fx = $.fx

      beforeEach ->
        $.fx = false
        tag = { id: 3, name: 'foo', color: '#abcdef' }
        i18n.reset_messages({
          'views.Tag._form.h3': 'h3'
          'views.Tag._form.labels.name': 'Name'
          'views.Tag._form.labels.color': 'Color'
          'views.Tag._form.confirm_delete': 'Confirm delete'
          'views.Tag._form.delete': 'Delete'
          'views.Tag._form.close': 'Close'
          'views.Tag._form.submit': 'Submit'
        })
        view = new TagFormView(tag)

      afterEach ->
        remove_view()
        delete window.i18n
        $.fx = old_fx

      remove_view = () ->
        $div = $('#tag-form-view-dialog')
        $div.modal('hide')
        $div.remove()
        $('.modal-backdrop').remove()

      actions = {
        submit: () -> $(view.form).submit()
        close: () -> $(view.form).modal('hide')
        delete: () -> $('input.delete', view.form).removeAttr('data-confirm').click()
        delete_with_prompt: () -> $('input.delete', view.form).click()
        set_name: (name) ->
          $input = $('input[name=name]', view.form)
          $input.val(name)
          $input.change()
        set_color: (color) ->
          $input = $('input[name=color]', view.form)
          $input.val(color)
          $input.change()
      }

      it 'should have a "name" input with the start name', ->
        $input = $('input[name=name]', view.div)
        expect($input.val()).toEqual('foo')

      it 'should have a "color" input with the start color', ->
        $input = $('input[name=color]', view.div)
        expect($input.val()).toEqual('#abcdef')

      it 'should assign "color" based on tag name when the tag has no color', ->
        remove_view()
        delete tag.color
        view = new TagFormView(tag)
        $input = $('input[name=color]', view.div)
        expect($input.val()).toEqual('#0089ff')

      it 'should trigger "change" on change', ->
        val = undefined
        view.observe('change', (tag) -> val = tag)
        actions.set_name('bar')
        actions.submit()
        expect(val).toEqual({ id: 3, name: 'bar', color: '#abcdef' })

      it 'should not change the existing tag on change', ->
        actions.set_name('bar')
        actions.submit()
        expect(tag.name).toEqual('foo')

      it 'should hide after "change" and automatically trigger "closed"', ->
        i = 1
        val1 = undefined
        val2 = undefined
        view.observe('change', () -> val1 = i++)
        view.observe('closed', () -> val2 = i++)
        actions.set_name('bar')
        actions.submit()
        expect(val1).toEqual(1)
        expect(val2).toEqual(2)

      it 'should trigger "change" when submit is clicked (as opposed to when the form is submitted)', ->
        changed = false
        view.observe('change', -> changed = true)
        $('.btn-primary', view.div).click()
        expect(changed).toBe(true)

      it 'should trigger "closed" when close is clicked', ->
        closed = false
        view.observe('closed', -> closed = true)
        actions.close()
        expect(closed).toBe(true)

      it 'should trigger "delete" when delete is clicked', ->
        deleted = false
        view.observe('delete', -> deleted = true)
        actions.delete()
        expect(deleted).toBe(true)

      it 'should hide after "delete" and automatically trigger "closed"', ->
        i = 1
        val1 = undefined
        val2 = undefined
        view.observe('delete', () -> val1 = i++)
        view.observe('closed', () -> val2 = i++)
        actions.delete()
        expect(val1).toEqual(1)
        expect(val2).toEqual(2)

      it 'should confirm when deleting and trigger delete if OK is pressed', ->
        deleted = false
        view.observe('delete', () -> deleted = true)
        spyOn(window, 'confirm').andReturn(true)
        actions.delete_with_prompt()
        expect(window.confirm).toHaveBeenCalledWith('Confirm delete')
        expect(deleted).toBe(true)

      it 'should confirm when deleting and not delete or hide if OK is not pressed', ->
        failed = false
        view.observe('delete', () -> failed = true)
        view.observe('change', () -> failed = true)
        spyOn(window, 'confirm').andReturn(false)
        actions.delete_with_prompt()
        expect(window.confirm).toHaveBeenCalledWith('Confirm delete')
        expect(failed).toBe(false)
