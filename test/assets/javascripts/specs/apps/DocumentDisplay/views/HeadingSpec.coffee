define [
  'backbone'
  'i18n'
  'apps/DocumentDisplay/views/Heading'
], (Backbone, i18n, Heading) ->
  describe 'apps/DocumentDisplay/views/Heading', ->
    state = undefined
    view = undefined

    beforeEach ->
      state = new Backbone.Model()
      state.set('preferences', new Backbone.Model())
      view = new Heading({ model: state })

    it 'should not render anything when there is no document', ->
      view.render()
      expect(view.$el.html()).to.eq('')

    it 'should render a document heading when the document changes', ->
      document = new Backbone.Model({ heading: 'Heading' })
      state.set('document', document)
      expect(view.$el.html()).to.eq('Heading')

    it 'should escape HTML', ->
      document = new Backbone.Model({ heading: '<>\'"&' })
      state.set('document', document)
      expect(view.$el.text()).to.eq('<>\'"&')

    it 'should render special text on empty', ->
      i18n.reset_messages({
        'views.Document.show.heading.empty': 'empty'
      })
      state.set('document', new Backbone.Model({ heading: '' }))
      expect(view.$el.html()).to.eq('empty')
