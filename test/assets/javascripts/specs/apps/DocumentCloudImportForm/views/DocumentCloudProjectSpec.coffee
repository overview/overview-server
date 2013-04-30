require [
  'backbone'
  'apps/DocumentCloudImportForm/views/DocumentCloudProject'
  'i18n'
], (Backbone, ProjectView, i18n) ->
  describe 'apps/DocumentCloudImportForm/views/DocumentCloudProject', ->
    model = undefined
    view = undefined

    beforeEach ->
      model = new Backbone.Model({
        project: new Backbone.Model({
          id: 123
          title: ''
          description: null
          document_ids: []
        }),
        credentials: undefined
        status: 'unknown'
      })
      i18n.reset_messages({
        'views.DocumentCloudImportJob.new.project.document_count': 'document_count,{0}'
      })
      view = new ProjectView({ model: model })

    it 'should not render anything when status is not fetched', ->
      model.set('status', 'unknown')
      expect(view.$el.html()).toEqual('')
      model.set('status', 'fetching')
      expect(view.$el.html()).toEqual('')
      model.set('status', 'error')
      expect(view.$el.html()).toEqual('')

    it 'should render the title', ->
      model.get('project').set('title', 'title')
      model.set('status', 'fetched')
      expect(view.$('.title').text()).toEqual('title')

    it 'should render the description', ->
      model.get('project').set('description', 'description')
      model.set('status', 'fetched')
      expect(view.$('.description').text()).toEqual('description')

    it 'should not render an empty description', ->
      model.get('project').set('description', null)
      model.set('status', 'fetched')
      expect(view.$('.description').length).toEqual(0)

    it 'should render the number of documents', ->
      model.get('project').set('document_ids', { length: 1234 })
      model.set('status', 'fetched')
      expect(view.$('.document-count').text()).toContain('document_count,1234')
