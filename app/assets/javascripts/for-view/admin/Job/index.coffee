define [
  'jquery'
  'apps/JobAdmin/App'
], ($, App) ->
  $ ->
    new App(el: $('.container'))
