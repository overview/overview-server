define [
  'backbone'
  'apps/Job/app'
  'i18n'
], (Backbone, JobApp, i18n) ->
  describe 'apps/Job/app', ->
    class Job extends Backbone.Model

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.show.apps.Job.heading': 'heading'
        'views.DocumentSet.show.apps.Job.heading.error': 'heading.error'
        'views.DocumentSet.show.apps.Job.description': 'description'

      @view = new Job
        progress: null

      @el = document.createElement('div')

      @viewApp = new JobApp
        el: @el
        view: @view

    afterEach ->
      @viewApp.remove()

    it 'should show a heading', -> expect(@viewApp.$('h3')).to.contain('heading')
    it 'should show a description', -> expect(@viewApp.$('p.description')).to.contain('description')
    it 'should show a status', -> expect(@viewApp.$('p.status')).to.exist

    it 'should show a progress bar', ->
      $progress = @viewApp.$('progress')
      expect($progress).to.exist
      expect($progress.prop('value')).to.eq(0)

    describe 'when progress changes', ->
      beforeEach ->
        @view.set(type: 'error', progress: { state: 'ERROR', fraction: 0.4, description: 'foo' })

      it 'should change progress', -> expect(@viewApp.$('progress').prop('value')).to.eq(0.4)
      it 'should change status', -> expect(@viewApp.$('p.status')).to.contain('foo')
      it 'should change state', -> expect(@viewApp.$el.children()).to.have.class('error')
      it 'should set heading', -> expect(@viewApp.$('h3')).to.contain('heading.error')
