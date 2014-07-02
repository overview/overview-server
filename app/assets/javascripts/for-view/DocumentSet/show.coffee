define [
  'jquery'
  'apps/Show/app'
], ($, App) ->
  $ ->
    el = (id) -> document.getElementById(id)

    new App({
      mainEl: el('main')
      navEl: $('nav')[0]
    })
