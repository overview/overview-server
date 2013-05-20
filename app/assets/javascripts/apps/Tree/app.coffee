define [
  'jquery'
  './models/animated_focus'
  './models/animator'
  './models/property_interpolator'
  './models/remote_tag_list'
  './models/world'
  './models/selection'
  './controllers/keyboard_controller'
  './controllers/logger'
  './controllers/tag_list_controller'
  './controllers/focus_controller'
  './controllers/tree_controller'
  './controllers/document_list_controller'
  './controllers/document_contents_controller'
  './controllers/auto_focus_controller'
], ($, \
    AnimatedFocus, Animator, PropertyInterpolator, RemoteTagList, World, Selection, \
    KeyboardController, Logger, \
    tag_list_controller, focus_controller, tree_controller, document_list_controller, document_contents_controller, auto_focus_controller) ->

  class App
    constructor: (options) ->
      throw 'need options.tagListEl' if !options.tagListEl
      throw 'need options.focusEl' if !options.focusEl
      throw 'need options.treeEl' if !options.treeEl
      throw 'need options.documentListEl' if !options.documentListEl
      throw 'need options.documentEl' if !options.documentEl
      throw 'need options.navEl' if !options.navEl
      throw 'need options.fullSizeEls' if !options.fullSizeEls

      world = new World()

      remote_tag_list = new RemoteTagList(world.cache)

      world.cache.load_root().done ->
        world.state.set('selection', new Selection({ nodes: [world.cache.on_demand_tree.id_tree.root] }))
        Logger.set_server(world.cache.server)

      refresh_height = () ->
        MARGIN = 5 #px

        # Make the main div go below the (variable-height) navbar
        h = $(options.navEl).height()
        $(options.fullSizeEls).css({ top: h })

        # Shrink the document list to available space
        $document_list = $('#document-list')
        $prev = $document_list.prev()
        prev_bottom = $prev.position().top + $prev.outerHeight()
        top_margin = parseFloat($document_list.prevAll(':eq(0)').css('margin-top'))
        bottom_margin = parseFloat($document_list.css('margin-bottom')) # it's the last one
        parent_height = $document_list.parent().height()
        $document_list.height(parent_height - prev_bottom - top_margin - bottom_margin - 2) # 2px is the border

        # Round the iframe's parent's width, because it needs an integer number of px
        $document = $('#document')
        $iframe = $document.find('iframe')
        $iframe.width(1)
        w = Math.floor($document.width(), 10)
        $iframe.width(w)

      refocus = ->
        # Pull focus out of the iframe.
        #
        # We can't listen for events on the document iframe; so if it's present,
        # it breaks keyboard shortcuts. We need to re-grab focus whenever we can
        # without disturbing the user.
        #
        # For instance, if the user is logging in to DocumentCloud in the iframe,
        # we don't want to steal focus; so a timer is bad, and a mousemove handler
        # is bad. But if we register a click, it's worth using that to steal focus.
        window.focus() if document.activeElement?.tagName == 'IFRAME'

      refocus_body_on_leave_window = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/321
        hidden = undefined

        callback = (e) ->
          if !document[hidden]
            refocus()

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

      refocus_body_on_event = ->
        # Ugly fix for https://github.com/overview/overview-server/issues/362
        $('body').on 'click', (e) ->
          refocus()

      keyboard_controller = new KeyboardController(document)

      interpolator = new PropertyInterpolator(500, (x) -> -Math.cos(x * Math.PI) / 2 + 0.5)
      animator = new Animator(interpolator)
      focus = new AnimatedFocus(animator)

      tag_list_controller(options.tagListEl, remote_tag_list, world.state)
      focus_controller(options.focusEl, focus)

      controller = tree_controller(options.treeEl, world.cache, focus, world.state)
      keyboard_controller.add_controller('TreeController', controller)

      controller = document_list_controller(options.documentListEl, world.cache, world.state)
      keyboard_controller.add_controller('DocumentListController', controller)

      controller = document_contents_controller(options.documentEl, world.cache, world.state)
      keyboard_controller.add_controller('DocumentContentsController', controller)

      auto_focus_controller(focus, world)

      world.cache.tag_store.observe('tag-added', -> _.defer(refresh_height))
      world.cache.tag_store.observe('tag-removed', -> _.defer(refresh_height))
      $(window).resize(refresh_height)
      refresh_height()

      refocus_body_on_leave_window()
      refocus_body_on_event()
