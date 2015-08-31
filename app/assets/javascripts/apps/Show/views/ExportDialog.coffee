define [
  'underscore'
  'backbone'
  'i18n'
  'bootstrap-modal'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.ExportDialog')

  class ExportDialog extends Backbone.View
    id: 'document-set-export-modal'
    tagName: 'div'
    className: 'modal fade'
    template: _.template("""
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
            <h4 class="modal-title"><%- t('h4') %></h4>
          </div>
          <div class="modal-body">
            <div class="selection">
              <h5><%- t('selection.label') %></h5>
              <div class="btn-group" role="group">
                <button data-selection="all" class="btn"></button>
                <button data-selection="list" class="btn"></button>
              </div>
            </div>
            <div class="format">
              <h5><%- t('format.label') %></h5>
              <div class="btn-group" role="group">
                <button data-format="spreadsheet" class="btn"><%- t('format.spreadsheet') %></button>
                <button data-format="bundle" class="btn"><%- t('format.bundle') %></button>
              </div>
            </div>
            <div class="format-spreadsheet">
              <h5><%- t('list.spreadsheet') %></h5>
              <ul>
                <li><%- t('formats.DocumentsWithStringTags.csv') %> <a href="#" class="download-csv-strings"><%- t('download') %></a></li>
                <li><%- t('formats.DocumentsWithColumnTags.csv') %> <a href="#" class="download-csv-columns"><%- t('download') %></a></li>
                <li><%- t('formats.DocumentsWithStringTags.xlsx') %> <a href="#" class="download-xlsx-strings"><%- t('download') %></a></li>
                <li><%- t('formats.DocumentsWithColumnTags.xlsx') %> <a href="#" class="download-xlsx-columns"><%- t('download') %></a></li>
              </ul>
              <p><%= t('spreadsheet_help_html') %></p>
            </div>
            <div class="format-bundle">
              <h5><%- t('list.bundle') %></h5>
              <ul>
                <li><%- t('formats.ZipArchive') %> <a class="download-zip" href="#"><%- t('download') %></a></li>
              </ul>
            </div>
          </div>
          <div class="modal-footer">
            <a href="#" data-dismiss="modal" class="btn"><%- t('close') %></a>
          </div>
        </div>
      </div>
    """)

    events:
      'click button': '_onClickButton'

    initialize: (options) ->
      throw new Error('Must pass options.documentSet, a DocumentSet') if !options.documentSet
      # May also pass options.documentList

      @documentSet = options.documentSet
      @documentList = options.documentList

      @_initialRender()

      if @documentList? && !@documentList.get('length')?
        # Races here are rare.
        @listenToOnce(@documentList, 'change:length', @render)
      else
        @render()

    _initialRender: ->
      html = @template(t: t)
      @$el.html(html)
      @$('.modal-body').children().css(display: 'none')

    _onClickButton: (e) ->
      $button = Backbone.$(e.currentTarget)
      $button.addClass('active btn-primary')
      $button.siblings().removeClass('active btn-primary')
      @render()

    render: ->
      @_renderNDocuments()
      @_renderSelectionVisible()
      @_renderFormatVisible()
      @_renderOptionsVisible()
      @_renderHrefs()

    _renderNDocuments: ->
      nDocuments = @documentSet.nDocuments
      nDocumentsInList = @documentList?.get('length') || 0

      @$('button[data-selection="all"]').text(t('selection.all', nDocuments))
      @$('button[data-selection="list"]').text(t('selection.list', nDocumentsInList))

    _renderSelectionVisible: ->
      $selection = @$('.selection')
      $selection.css(display: 'block')

      if !@documentList? || @documentList.get('length') == @documentSet.nDocuments
        $selection.find('button:eq(0)').addClass('active btn-primary')
        $selection.find('button[data-selection="list"]').prop('disabled', true)

    _renderFormatVisible: ->
      @$('.format').css(display: 'block') if @$('.selection button.active').length

    _renderOptionsVisible: ->
      format = @$('button.active[data-format]').attr('data-format')
      return if !format

      @$('.format-spreadsheet, .format-bundle').css(display: 'none')
      @$(".format-#{format}").css(display: 'block')

    _renderHrefs: ->
      selection = @$('button.active[data-selection]').attr('data-selection')
      return if !selection

      urlPrefix = "/documentsets/#{@documentSet.id}"
      basenameEncoded = encodeURIComponent(@documentSet.name)

      queryString = if selection == 'all'
        ''
      else
        '?' + @documentList.params.toQueryString()

      @$('a.download-zip')
        .attr(href: "#{urlPrefix}/archive/view/#{basenameEncoded}.zip#{queryString}")
      for ext in [ 'csv', 'xlsx' ]
        for tags in [ 'string', 'column' ]
          @$("a.download-#{ext}-#{tags}s")
            .attr(href: "#{urlPrefix}/export/documents-with-#{tags}-tags/#{ext}/#{basenameEncoded}.#{ext}#{queryString}")

  ExportDialog.show = (options) ->
    dialog = new ExportDialog(options)
    dialog.$el
      .appendTo('body')
      .modal()
      .on('hidden.bs.modal', => dialog.remove())

  ExportDialog
