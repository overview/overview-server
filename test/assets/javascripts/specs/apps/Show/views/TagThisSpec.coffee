define [
  'jquery'
  'underscore'
  'backbone'
  'i18n'
  'apps/Show/views/TagThis'
], ($, _, Backbone, i18n, TagThis) ->
  class Tag extends Backbone.Model

  class Tags extends Backbone.Collection
    model: Tag

  class State extends Backbone.Model
    # The TagThis view listens to change:document, change:documentListParams
    # and change:oneDocumentSelected events.
    #
    # The ThisTag view cares about oneDocumentSelected and the result of
    # getSelection().
    defaults:
      documentListParams: { toI18n: -> [ 'foo', 'bar' ] }
      oneDocumentSelected: false
      document: null

    getSelection: -> {}

  describe 'apps/Show/views/TagThis', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.TagThis',
        'button.list': 'button.list'
        'button.document': 'button.document'
        'value.foo': 'foo,{0}'
        'label.new': 'label.new'
        'label.existing': 'label.existing'
        'placeholder': 'placeholder'
        'hide': 'hide'

      @tags = new Tags([
        { name: 'foo' }
        { name: 'bar' }
      ])
      @state = new State

    describe 'when tagging a document', ->
      beforeEach ->
        @state.set
          oneDocumentSelected: true
        @view = new TagThis(tags: @tags, state: @state)

      it 'should show tag-this-document text', ->
        expect(@view.$('.prompt button')).to.have.text('button.document')

      describe 'after first click', ->
        beforeEach -> @view.$('.prompt button').click()

        it 'should show detials', ->
          expect(@view.$el).to.have.class('show-details')

        it 'should focus the tag name', ->
          # skip this test; it's a pain

        it 'should show a tag name', ->
          expect(@view.$('.details input')).to.have.value('foo,bar')

        it 'should show a second submit button with the same text', ->
          expect(@view.$('.details button')).to.have.text('button.document')

        it 'should show we are creating a new tag', ->
          expect(@view.$('.details label')).to.have.text('label.new')

        it 'should show we are creating an existing tag on edit', ->
          @view.$('input').val('foo')
          @view.$('input').trigger('input')
          expect(@view.$('.details label')).to.have.text('label.existing')

        it 'should disable input and show a placeholder on edit-to-empty', ->
          @view.$('input').val('')
          @view.$('input').trigger('input')
          expect(@view.$('.details label')).to.have.text('label.new')
          expect(@view.$('input')).to.have.attr('placeholder', 'placeholder')
          expect(@view.$('.details button')).to.be.disabled

        it 'should trigger tag', ->
          @view.on('tag', spy = sinon.spy())
          @view.$('.details form').trigger('submit')
          expect(spy).to.have.been.calledWith(name: 'foo,bar')

        it 'should hide details after tagging', ->
          @view.$('.details form').trigger('submit')
          expect(@view.$el).not.to.have.class('show-details')

        it 'should hide details on close', ->
          @view.$('.close').click()
          expect(@view.$el).not.to.have.class('show-details')

        it 'should switch to tagging a list', ->
          @state.set(oneDocumentSelected: false)
          expect(@view.$('.prompt button')).to.have.text('button.list')
          expect(@view.$el).not.to.have.class('show-details')

        it 'should switch tag name', ->
          @state.set(documentListParams: { toI18n: -> [ 'foo', 'baz' ] })
          expect(@view.$el).not.to.have.class('show-details')
          @view.$('.prompt button').click()
          expect(@view.$('.details input')).to.have.value('foo,baz')

        it 'should reset on document change', ->
          @state.set(document: 'bar')
          expect(@view.$el).not.to.have.class('show-details')

    describe 'when tagging a list', ->
      beforeEach ->
        @state.set(oneDocumentSelected: false)
        @view = new TagThis(tags: @tags, state: @state)

      it 'should show tag-this-list text', ->
        expect(@view.$('.prompt button')).to.have.text('button.list')
