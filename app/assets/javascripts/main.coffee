AnimatedFocus = require('models/animated_focus').AnimatedFocus
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
RemoteTagList = require('models/remote_tag_list').RemoteTagList
World = require('models/world').World

world = new World()

remote_tag_list = new RemoteTagList(world.cache)

world.cache.load_root()

log = require('globals').logger

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
    tree_controller(this, world.cache.on_demand_tree, focus, world.state)
  $('#document-list').each () ->
    document_list_controller = require('controllers/document_list_controller').document_list_controller
    document_list_controller(this, world.cache, world.state)

    $document_list = $(this)
    refresh_height = () ->
      parent_height = +$document_list.parent().height()
      prevall_height = _($document_list.prevAll()).reduce(((sum, el) -> sum + (+$(el).height())), 0)
      $document_list.height(parent_height - prevall_height - 44) # FIXME remove "44px"

    refresh_height()
    $(window).resize(refresh_height)
    world.cache.tag_store.observe('tag-added', -> _.defer(refresh_height))
    world.cache.tag_store.observe('tag-removed', -> _.defer(refresh_height))
  $('#document').each () ->
    document_contents_controller = require('controllers/document_contents_controller').document_contents_controller
    document_contents_controller(this, world.cache, world.state)
