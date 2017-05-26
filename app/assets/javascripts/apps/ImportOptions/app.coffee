define [
  'jquery'
  'underscore'
  './models/Options'
  './views/Options'
  'i18n'
  'elements/button-with-form-attribute'
  'jquery.validate'
  'bootstrap-modal'
], ($, _, Options, OptionsView, i18n) ->
  t = i18n.namespaced('views.DocumentSet.index.ImportOptions')

  DialogTemplate = _.template("""
    <div class="import-options modal fade" method="get" action="">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <a href="#" class="close" data-dismiss="modal" aria-hidden="true">×</a>
            <h4 class="modal-title"><%- t('dialog.title') %></h4>
          </div>
          <div class="modal-body"></div>
          <div class="modal-footer">
            <button form="import-options-form" class="btn btn-default" type="reset"><%- t('dialog.cancel') %></button>
            <button form="import-options-form" class="btn btn-primary" type="submit"><%- t('dialog.submit') %></button>
          </div>
        </div>
      </div>
    </div>
  """)

  # Produces document-set import options, either inline in a form or
  # through a dialog.
  #
  # Usage:
  #     requiredOptions = {
  #       supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'}]
  #       defaultLanguageCode: 'en'
  #     }
  #
  #     # Inline
  #     app = new OptionsApp(requiredOptions)
  #     $('form.document-set-import').append(app.el)
  #
  #     # Dialog
  #     $('form.document-set-import').on 'submit', (e) ->
  #       OptionsApp.interceptSubmitEvent(e, requiredOptions)
  #
  # Inline
  # ======
  #
  # If you want to add document-set import options to an existing form, simply
  # append the `.fieldsetEl` value, which is an HTML fieldset element.
  #
  # Dialog
  # ======
  #
  # When you call `interceptSubmitEvent(submitEvent)`, this app will intercept
  # the event. This means:
  #
  # 1. It calls `event.preventDefault()`
  # 2. It presents a dialog box with additional options
  # 3. If the user presses "Submit" in the dialog, the options are added to the
  #    original form and `form.submit()` is called. (Step 1 does not apply when
  #    the options are added.)
  # 4. If the user presses "Cancel" in the dialog, the dialog disappears and
  #    the page is left alone.
  #
  # Options
  # =======
  #
  # Required:
  #
  # * `supportedLanguages`: An Array of `{ code: "en", name: "English" }` values
  # * `defaultLanguageCode`: A language code like `"en"`
  # * `onlyOptions`: An Array of options to include, such as `split_documents`.
  #
  # Valid options for the `onlyOptions` Array:
  #
  # * `name`: desired name of document set
  # * `split_documents`: each "page" of input (think, Microsoft Word WYSIWYG
  #                      rendering) becomes a document. False means each input
  #                      "file" becomes a document.
  # * `ocr`: when true run Tesseract on pages that don't have much text.
  # * `lang`: language to use when indexing. Overview currently ignores this
  #           value, except when generating a Tree visualization.
  # * `metadata_json`: JSON fields to add to DocumentSet metadata schema.
  class App
    constructor: (@options) ->
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @model = new Options({}, @options)
      @view = new OptionsView(model: @model)
      @el = @view.el
      @fieldsetEl = @view.$('fieldset')[0]

    # Creates a dialog, populating it with the app
    @_showDialog: (app) ->
      dialogHtml = DialogTemplate(t: t)
      $dialog = $(dialogHtml)
      $dialog.find('.modal-body').append(app.el)

      focus = -> $dialog.find(':input:visible:eq(0)').focus()

      $dialog.find('form.import-options').on('reset', -> $dialog.modal('hide'))

      $dialog
        .on('hidden.bs.modal', -> $dialog.remove())
        .appendTo('body')
        .modal()
        .on('show.bs.modal', focus) # might work -- and it's as early as possible
        .on('shown.bs.modal', focus) # will certainly work

    @createNewTreeDialog: (options) ->
      throw 'Must pass csrfToken, a String' if !options.csrfToken?
      throw 'Must pass url, a String' if !options.url?

      app = new App(options)

      $dialog = @_showDialog(app)

      $dialog
        .attr(method: 'post', action: options.url)
        .append($('<input type="hidden" name="csrfToken"/>').val(options.csrfToken))

    @addHiddenInputsThroughDialog: (form, options) ->
      app = new App(options)

      submit = ->
        for k, v of app.model.attributes
          $input = $('<input type="hidden" />')
            .attr('name', k)
            .attr('value', v)
          $(form).append($input)
        options.callback()

      $dialog = @_showDialog(app)

      $dialog.find('form.import-options').validate
        submitHandler: (form) ->
          # Fix integration tests: Firefox in background does not emit onChange
          # https://code.google.com/p/selenium/issues/detail?id=157#c44
          $(':input', form).change()

          submit()
          $dialog.modal('hide')
          false # Prevent HTML-default submit

      undefined

    @interceptSubmitEvent: (e, options) ->
      form = e.target

      app = new App(options)

      if form.hasAttribute('import-options-submitting')
        # We're intercepting a submit event that was generated by us. Stop.
      else
        # We're intercepting a user's submit event
        e.preventDefault()

        options = _.extend {}, options, callback: ->
          $(form)
            .attr('import-options-submitting', true)
            .submit()

        @addHiddenInputsThroughDialog(form, options)

      undefined
