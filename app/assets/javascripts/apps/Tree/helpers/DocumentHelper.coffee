define [ 'i18n' ], (i18n) ->
  t = i18n.namespaced('views.Tree.show.helpers.DocumentHelper')

  DocumentCloudPageRegex = /.*#p(\d+)$/

  title: (document) ->
    baseTitle = document?.title && t('title', document.title) || t('title.empty')
    m = DocumentCloudPageRegex.exec(document?.documentcloud_id || '')
    if m?
      page = parseInt(m[1], 10)
      t('title.page', baseTitle, page)
    else
      baseTitle
