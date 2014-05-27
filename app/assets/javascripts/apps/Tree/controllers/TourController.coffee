define [
  '../../Tour/app'
  'i18n'
], (TourApp, i18n) ->
  t = i18n.namespaced('views.Tree.show.Tour')

  tour = [
    {
      find: '#tree-app-tree'
      placement: 'right'
      title: t('folders1.title')
      bodyHtml: t('folders1.body_html')
    }
    {
      find: '#tree-app-tree'
      placement: 'right'
      title: t('folders2.title')
      bodyHtml: t('folders2.body_html')
    }
    {
      find: '#tree-app-document-list'
      placement: 'left'
      title: t('documentList.title')
      bodyHtml: t('documentList.body_html')
    }
    {
      find: '#tree-app-tags'
      placement: 'top'
      title: t('tags.title')
      bodyHtml: t('tags.body_html')
    }
    {
      find: '#Intercom'
      placement: 'bottom'
      title: t('contact.title')
      bodyHtml: t('contact.body_html')
    }
  ]

  ->
    app = new TourApp(tour)
