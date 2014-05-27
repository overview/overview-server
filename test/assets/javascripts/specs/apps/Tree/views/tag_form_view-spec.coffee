define [
  'jquery'
  'i18n'
  'apps/Tree/views/tag_form_view'
], ($, i18n, TagFormView) ->
  describe 'views/tag_form_view', ->
    describe 'TagFormView', ->
      tag = undefined
      view = undefined
      actions = undefined
      old_fx = $.fx

      beforeEach ->
        @sandbox = sinon.sandbox.create()
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
        @sandbox.restore()
        remove_view()
        $.fx = old_fx
        $(document).off('.modal')

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
        expect($input.val()).to.eq('foo')

      it 'should have a "color" input with the start color', ->
        $input = $('input[name=color]', view.div)
        expect($input.val()).to.eq('#abcdef')

      it 'should assign "color" based on tag name when the tag has no color', ->
        remove_view()
        delete tag.color
        view = new TagFormView(tag)
        $input = $('input[name=color]', view.div)
        expect($input.val()).to.eq('#0089ff')

      it 'should trigger "change" on change', ->
        spy = sinon.spy()
        view.observe('change', spy)
        actions.set_name('bar')
        actions.submit()
        expect(spy).to.have.been.calledWith(id: 3, name: 'bar', color: '#abcdef')

      it 'should not change the existing tag on change', ->
        actions.set_name('bar')
        actions.submit()
        expect(tag.name).to.eq('foo')

      it 'should hide after "change" and automatically trigger "closed"', ->
        spy1 = sinon.spy()
        spy2 = sinon.spy()
        view.observe('change', spy1)
        view.observe('closed', spy2)
        actions.set_name('bar')
        actions.submit()
        expect(spy1).to.have.been.called
        expect(spy2).to.have.been.called

      it 'should trigger "change" when submit is clicked (as opposed to when the form is submitted)', ->
        spy = sinon.spy()
        view.observe('change', spy)
        $('.btn-primary', view.div).click()
        expect(spy).to.have.been.called

      it 'should trigger "closed" when close is clicked', ->
        spy = sinon.spy()
        view.observe('closed', spy)
        actions.close()
        expect(spy).to.have.been.called

      it 'should trigger "delete" when delete is clicked', ->
        deleted = false
        view.observe('delete', -> deleted = true)
        actions.delete()
        expect(deleted).to.be(true)

      it 'should hide after "delete" and automatically trigger "closed"', ->
        i = 1
        val1 = undefined
        val2 = undefined
        view.observe('delete', () -> val1 = i++)
        view.observe('closed', () -> val2 = i++)
        actions.delete()
        expect(val1).to.eq(1)
        expect(val2).to.eq(2)

      it 'should confirm when deleting and trigger delete if OK is pressed', ->
        deleted = false
        view.observe('delete', () -> deleted = true)
        @sandbox.stub(window, 'confirm').returns(true)
        actions.delete_with_prompt()
        expect(window.confirm).to.have.been.calledWith('Confirm delete')
        expect(deleted).to.be(true)

      it 'should confirm when deleting and not delete or hide if OK is not pressed', ->
        failed = false
        view.observe('delete', () -> failed = true)
        view.observe('change', () -> failed = true)
        @sandbox.stub(window, 'confirm').returns(false)
        actions.delete_with_prompt()
        expect(window.confirm).to.have.been.calledWith('Confirm delete')
        expect(failed).to.be(false)
