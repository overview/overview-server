define [
  'jquery'
  'backbone'
  'apps/DocumentMetadata/views/AddFieldView'
  'i18n'
], ($, Backbone, AddFieldView, i18n) ->
  describe 'apps/DocumentMetadata/views/AddFieldView', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentMetadata.AddFieldView',
        label: 'label'
        expand: 'expand'
        placeholder: 'placeholder'
        submit: 'submit'
        reset: 'reset'

      @model = new Backbone.Model(fields: [ 'foo', 'bar' ])
      @subject = new AddFieldView(model: @model)

    afterEach ->
      @subject.stopListening()

    it 'should start collapsed', ->
      expect(@subject.$el).not.to.have.class('expanded')

    it 'should add expanded class', ->
      @subject.$('a.expand').click()
      expect(@subject.$el).to.have.class('expanded')

    describe 'when expanded', ->
      beforeEach -> @subject.$('a.expand').click()

      it 'should remove expanded class when clicking expand', ->
        @subject.$('a.expand').click()
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should remove expanded class and empty form when pressing Escape', ->
        e = $.Event('keydown', which: 27, keyCode: 27)
        $input = @subject.$('input')
        $input.val('foo')
        $input.trigger(e)
        expect(@subject.$el).not.to.have.class('expanded')
        expect($input.val()).to.eq('')

      it 'should remove expanded class when clicking reset', ->
        # @subject.$('button[type=reset]').click() ... jQuery simulated event won't reset the form
        @subject.$el.trigger('reset') # so we fake it
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should remove expanded class when submitting', ->
        @subject.$('input').val('baz')
        @subject.$el.trigger('submit') # As with reset, we fake the event
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should not submit an empty value, even if the browser is not HTML5-compliant', ->
        @subject.$('input').val(' ')
        @subject.$el.trigger('submit')
        expect(@model.get('fields')).to.deep.eq([ 'foo', 'bar' ])

      it 'should do nothing when adding an existing field', ->
        @subject.$('input').val('foo')
        @subject.$el.trigger('submit')
        expect(@model.get('fields')).to.deep.eq([ 'foo', 'bar' ])

      it 'should add a metadata field', ->
        @subject.$('input').val('baz')
        @subject.$el.trigger('submit')
        expect(@model.get('fields')).to.deep.eq([ 'foo', 'bar', 'baz' ])

      it 'should reset the form after adding a metadata field', ->
        @subject.$('input').val('baz')
        @subject.$el.trigger('submit')
        @subject.$('a.expand').click()
        expect(@subject.$('input').val()).to.eq('')
