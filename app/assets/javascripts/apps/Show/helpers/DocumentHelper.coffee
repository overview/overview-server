define [ 'i18n' ], (i18n) ->
  t = i18n.namespaced('views.Tree.show.helpers.DocumentHelper')

  title: (document) ->
    baseTitle = document?.title && t('title', document.title_proper) || t('title.empty')
    if document?.pageNumber?
      t('title.page', baseTitle, document.pageNumber)
    else
      baseTitle
