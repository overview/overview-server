define [
  'backbone'
  './views/MetadataSchema'
  'i18n'
], (Backbone, MetadataSchemaView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.MetadataSchemaEditor.App')

  class MetadataSchemaEditorApp extends Backbone.View
    className: 'metadata-schema-editor'

    template: _.template '''
			<div class="modal metadata-schema-editor-modal" tabindex="-1" role="dialog">
				<div class="modal-dialog" role="document">
					<div class="modal-content">
						<div class="modal-header">
							<button type="button" class="close" data-dismiss="modal" aria-label="<%- t('close') %>"><span aria-hidden="true">&times;</span></button>
							<h4 class="modal-title"><%- t('title') %></h4>
						</div>
						<div class="modal-body">
              <p><%- t('explanation') %></p>
              <div class="metadata-schema"></div>
						</div>
						<div class="modal-footer">
							<button type="button" class="btn btn-default" data-dismiss="modal"><%- t('close') %></button>
						</div>
					</div><!-- /.modal-content -->
				</div><!-- /.modal-dialog -->
			</div><!-- /.modal -->
    '''

    initialize: (options) ->
      if !options.documentSet
        throw 'Must specify options.documentSet, a Backbone.Model with a `metadataSchema` attribute'

      @documentSet = options.documentSet

    render: ->
      @el.innerHTML = @template(t: t)
      @view = new MetadataSchemaView(documentSet: @documentSet, el: @el.querySelector('.metadata-schema'))
      @view.render()
      @

    remove: ->
      @view.remove()
      Backbone.View.prototype.remove.call(@)

    show: ->
      @$('.metadata-schema-editor-modal').modal()
