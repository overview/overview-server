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

      @viz = new Job
        progress: null

      @el = document.createElement('div')

      @vizApp = new JobApp
        el: @el
        viz: @viz

    afterEach ->
      @vizApp.remove()

    it 'should show a heading', -> expect(@vizApp.$('h3')).to.contain('heading')
    it 'should show a description', -> expect(@vizApp.$('p.description')).to.contain('description')
    it 'should show a status', -> expect(@vizApp.$('p.status')).to.exist

    it 'should show a progress bar', ->
      $progress = @vizApp.$('progress')
      expect($progress).to.exist
      expect($progress.prop('value')).to.eq(0)

    describe 'when progress changes', ->
      beforeEach ->
        @viz.set(type: 'error', progress: { state: 'ERROR', fraction: 0.4, description: 'foo' })

      it 'should change progress', -> expect(@vizApp.$('progress').prop('value')).to.eq(0.4)
      it 'should change status', -> expect(@vizApp.$('p.status')).to.contain('foo')
      it 'should change state', -> expect(@vizApp.$el.children()).to.have.class('error')
      it 'should set heading', -> expect(@vizApp.$('h3')).to.contain('heading.error')
