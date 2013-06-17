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
  './controllers/search_result_list_controller'
  './controllers/focus_controller'
  './controllers/tree_controller'
  './controllers/document_list_controller'
  './controllers/document_contents_controller'
  './controllers/auto_focus_controller'
  './views/Mode'
], ($, \
    AnimatedFocus, Animator, PropertyInterpolator, RemoteTagList, World, Selection, \
    KeyboardController, Logger, \
    tag_list_controller, search_result_list_controller, \
    focus_controller, tree_controller, document_list_controller, document_contents_controller, auto_focus_controller, \
    ModeView) ->

  class App
    constructor: (options) ->
      throw 'need options.mainEl' if !options.mainEl
      throw 'need options.navEl' if !options.navEl

      # TODO remove searchDisabled entirely
      searchDisabled = $(options.mainEl).attr('data-is-searchable') == 'false'

      els = (->
        html = """
          <div id="tree-app-left">
            <div id="tree-app-tree-container">
              <div id="tree-app-left-top">
                <div id="tree-app-tree"></div>
                <div id="tree-app-zoom-slider"></div>
              </div>
              <div id="tree-app-left-bottom">
                <div id="tree-app-tags"></div>
                <div id="tree-app-search"></div>
              </div>
            </div>
          </div>
          <div id="tree-app-right">
            <div id="tree-app-document-list"></div>
            <div id="tree-app-document"></div>
          </div>
        """

        $(options.mainEl).html(html)

        $('#tree-app-search').remove() if searchDisabled

        el = (id) -> document.getElementById(id)

        {
          main: options.mainEl
          tree: el('tree-app-tree')
          zoomSlider: el('tree-app-zoom-slider')
          tags: el('tree-app-tags')
          search: el('tree-app-search')
          documentList: el('tree-app-document-list')
          document: el('tree-app-document')
          left: el('tree-app-left')
          leftTop: el('tree-app-left-top')
          leftBottom: el('tree-app-left-bottom')
        }
      )()

      world = new World()

      remote_tag_list = new RemoteTagList(world.cache)

      world.cache.load_root().done ->
        world.state.set('selection', new Selection({ nodes: [world.cache.on_demand_tree.id_tree.root] }))
        Logger.set_server(world.cache.server)

      refreshHeight = () ->
        # Make the main div go below the (variable-height) navbar
        h = $(options.navEl).height()
        $(options.fullSizeEl).css({ top: h })

        # Give room to the tags and search results at the bottom
        h = $(els.leftBottom).outerHeight()
        $(els.leftTop).css({ bottom: h })

        # Round the iframe's parent's width, because it needs an integer number of px
        $document = $(els.document)
        $document.find('iframe')
          .width(1)
          .width($document.width())

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
      focus_controller(els.zoomSlider, focus)

      controller = tree_controller(els.tree, world.cache, focus, world.state)
      keyboard_controller.add_controller('TreeController', controller)

      controller = document_contents_controller({
        cache: world.cache
        state: world.state
        el: els.document
      })
      keyboard_controller.add_controller('DocumentContentsController', controller)

      controller = document_list_controller(els.documentList, els.document, world.cache, world.state)
      keyboard_controller.add_controller('DocumentListController', controller)

      new ModeView({ el: options.mainEl, state: world.state })

      auto_focus_controller(focus, world)

      tag_list_controller({
        remote_tag_list: remote_tag_list
        state: world.state
        el: els.tags
      })

      if !searchDisabled
        search_result_list_controller({
          cache: world.cache
          state: world.state
          el: els.search
        })

      for store in [ world.cache.tag_store, world.cache.search_result_store ]
        for event in [ 'added', 'removed', 'changed' ]
          store.observe(event, refreshHeight)

      throttledRefreshHeight = _.throttle(refreshHeight, 100)
      $(window).resize(throttledRefreshHeight)
      refreshHeight()

      refocus_body_on_leave_window()
      refocus_body_on_event()
