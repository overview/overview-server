define [ 'i18n' ], (i18n) ->
  t = i18n.namespaced('views.Tree.show.helpers.DocumentHelper')

  title: (document) ->
    baseTitle = document?.title && t('title', document.title) || t('title.empty')
    if document?.page_number?
      t('title.page', baseTitle, document.page_number)
    else
      baseTitle
