define [
  'jquery'
  'apps/Tree/app'
], ($, App) ->
  $ ->
    el = (id) -> document.getElementById(id)

    new App({
      focusEl: el('focus')
      treeEl: el('tree')
      documentListEl: el('document-list-container')
      navEl: document.getElementsByTagName('nav')[0]
      mainEl: el('main')
      fullSizeEl: el('main')
      innerFullSizeEl: $('#main>.flex-row')[0]
    })
