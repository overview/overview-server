define [
  'jquery'
  'apps/PluginAdmin/App'
], ($, App) ->
  $ ->
    $el = $('.app')
    new App(el: $el.get(0))
