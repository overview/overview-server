define [
  'jquery'
  './_show/models/animated_focus'
  './_show/models/animator'
  './_show/models/property_interpolator'
  './_show/models/remote_tag_list'
  './_show/models/world'
  './_show/models/selection'
  './_show/controllers/keyboard_controller'
  './_show/controllers/logger'
  './_show/controllers/tag_list_controller'
  './_show/controllers/focus_controller'
  './_show/controllers/tree_controller'
  './_show/controllers/document_list_controller'
  './_show/controllers/document_contents_controller'
  './_show/controllers/auto_focus_controller'
], ($, \
    AnimatedFocus, Animator, PropertyInterpolator, RemoteTagList, World, Selection, \
    KeyboardController, Logger, \
    tag_list_controller, focus_controller, tree_controller, document_list_controller, document_contents_controller, auto_focus_controller) ->

  world = new World()

  remote_tag_list = new RemoteTagList(world.cache)

  world.cache.load_root().done ->
    world.state.set('selection', new Selection({ nodes: [world.cache.on_demand_tree.id_tree.root] }))
    Logger.set_server(world.cache.server)

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

  refocus_body_on_leave_window = () ->
    # Ugly fix for https://github.com/overview/overview-server/issues/321
    hidden = undefined

    callback = (e) ->
      if !document[hidden]
        if document.activeElement?.tagName? == 'IFRAME'
          window.focus()

    if document[hidden]?
      document.addEventListener("visibilitychange", callback)
    else if document[hidden = "mozHidden"]?
      document.addEventListener("mozvisibilitychange", callback)
    else if document[hidden = "webkitHidden"]?
      document.addEventListener("webkitvisibilitychange", callback)
    else if document[hidden = "msHidden"]?
      document.addEventListener("msvisibilitychange", callback)
    else
      hidden = undefined

  $ ->
    keyboard_controller = new KeyboardController(document)

    interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
    animator = new Animator(interpolator)
    focus = new AnimatedFocus(animator)

    $('#tag-list').each () ->
      tag_list_controller(this, remote_tag_list, world.state)
    $('#focus').each () ->
      focus_controller(this, focus)
    $('#tree').each () ->
      controller = tree_controller(this, world.cache, focus, world.state)
      keyboard_controller.add_controller('TreeController', controller)
    $('#document-list').each () ->
      controller = document_list_controller(this, world.cache, world.state)
      world.cache.tag_store.observe('tag-added', -> _.defer(refresh_height))
      world.cache.tag_store.observe('tag-removed', -> _.defer(refresh_height))
      keyboard_controller.add_controller('DocumentListController', controller)
    $('#document').each () ->
      controller = document_contents_controller(this, world.cache, world.state)
      keyboard_controller.add_controller('DocumentContentsController', controller)

    auto_focus_controller(focus, world)

    $(window).resize(refresh_height)
    refresh_height()

    refocus_body_on_leave_window()
