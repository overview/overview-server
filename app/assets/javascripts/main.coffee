AnimatedFocus = require('models/animated_focus').AnimatedFocus
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
RemoteTagList = require('models/remote_tag_list').RemoteTagList
World = require('models/world').World
Selection = require('models/selection').Selection

world = new World()

remote_tag_list = new RemoteTagList(world.cache)

world.cache.load_root().done ->
  world.state.set('selection', new Selection({ nodes: [world.cache.on_demand_tree.id_tree.root] }))

log = require('globals').logger

refresh_height = () ->
  MARGIN = 5 #px

  # Make the main div go below the (variable-height) navbar
  h = $('body>nav').height()
  $('#main, #document').css({ top: h })

  # Shrink the document list to available space
  $tag_list = $('#tag-list')
  $document_list = $('#document-list')
  parent_height = $document_list.parent().height()
  tag_list_bottom = $tag_list.position().top + $tag_list.outerHeight()
  $document_list.height(parent_height - tag_list_bottom - MARGIN * 3) # dunno why 3

  # Round the iframe's parent's width, because it needs an integer number of px
  $document = $('#document')
  $iframe = $document.find('iframe')
  $iframe.width(1)
  w = Math.floor($document.width(), 10)
  $iframe.width(w)

jQuery ($) ->
  log_controller = require('controllers/log_controller').log_controller
  log_controller(log, world.cache.server)

  interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
  animator = new Animator(interpolator)
  focus = new AnimatedFocus(animator)

  $('#tag-list').each () ->
    tag_list_controller = require('controllers/tag_list_controller').tag_list_controller
    tag_list_controller(this, remote_tag_list, world.state)
  $('#focus').each () ->
    focus_controller = require('controllers/focus_controller').focus_controller
    focus_controller(this, focus)
  $('#tree').each () ->
    tree_controller = require('controllers/tree_controller').tree_controller
    tree_controller(this, world.cache, focus, world.state)
  $('#document-list').each () ->
    document_list_controller = require('controllers/document_list_controller').document_list_controller
    document_list_controller(this, world.cache, world.state)
    world.cache.tag_store.observe('tag-added', -> _.defer(refresh_height))
    world.cache.tag_store.observe('tag-removed', -> _.defer(refresh_height))
  $('#document').each () ->
    document_contents_controller = require('controllers/document_contents_controller').document_contents_controller
    document_contents_controller(this, world.cache, world.state)

  $(window).resize(refresh_height)
  refresh_height()
