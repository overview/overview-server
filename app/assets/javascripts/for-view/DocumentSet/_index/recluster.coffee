define [ 'jquery', 'apps/ImportOptions/app' ], ($, OptionsApp) ->
  appOptions =
    supportedLanguages: window.supportedLanguages
    defaultLanguageCode: window.defaultLanguageCode
    onlyOptions: [ 'tree_title', 'tag_id', 'lang', 'supplied_stop_words', 'important_words' ]

  $('.document-sets').on 'submit', 'form.create-tree', (e) ->
    documentSetId = $(e.currentTarget).closest('[data-document-set-id]').attr('data-document-set-id')
    OptionsApp.interceptSubmitEvent(
      e,
      $.extend(
        { tagListUrl: "/documentsets/#{documentSetId}/tags.json" },
        appOptions
      )
    )
