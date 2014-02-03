define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/AbstractProgressView'
], ($, Backbone, AbstractProgressView) ->
  describe 'apps/MassUploadForm/views/AbstractProgressView', ->
    model = undefined
    view = undefined

    beforeEach ->
      SpecificProgressView = AbstractProgressView.extend
        progressProperty: 'progress'
        errorProperty: 'error'

      model = new Backbone.Model()
      view = new SpecificProgressView({model: model})

    it 'shows a progress bar', ->
      expect(view.$el.find('progress').length).toEqual(1)

    it 'updates the text', ->
      expect(view.$el.find('.text').text()).toEqual('0.0 kB / 0.0 kB')
      model.set('progress', {loaded: 1024, total: 2048})
      expect(view.$el.find('.text').text()).toEqual('1.0 kB / 2.0 kB')

    it 'updates the progress bar', ->
      $progressEl = view.$el.find('progress')
      expect($progressEl.prop('value')).toEqual(0)
      expect($progressEl.prop('max')).toEqual(100)
      model.set('progress', {loaded: 1024, total: 2048})
      expect($progressEl.prop('value')).toEqual(50)
      expect($progressEl.prop('max')).toEqual(100)

    it 'does not divide by zero when showing 0/0 uploaded', ->
      model.set('progress', loaded: 0, total: 1)
      model.set('progress', loaded: 0, total: 0)
      $progress = view.$('progress')
      expect($progress.prop('value')).toEqual(0)
      expect($progress.prop('max')).toEqual(100)

    it 'shows errors', ->
      model.set('error', 'an error')
      expect(view.$el.find('.message').text()).toEqual('an error')
