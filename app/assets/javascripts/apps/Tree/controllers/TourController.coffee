define [
  '../../Tour/app'
  'i18n'
], (TourApp, i18n) ->
  t = i18n.namespaced('views.Tree.show.Tour')

  tour = [
    {
      find: '#tree-app-document-list'
      placement: 'auto left'
      title: t('documentList.title')
      bodyHtml: t('documentList.body_html')
    }
    {
      find: '#tree-app-tree'
      placement: 'auto right'
      title: t('folders.title')
      bodyHtml: t('folders.body_html')
    }
    {
      find: '#tree-app-tags'
      placement: 'auto top'
      title: t('tags.title')
      bodyHtml: t('tags.body_html')
    }
  ]

  ->
    app = new TourApp(tour)
