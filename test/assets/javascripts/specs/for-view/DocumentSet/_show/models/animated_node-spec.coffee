require [
  'for-view/DocumentSet/_show/models/animated_node'
], (AnimatedNode) ->
  describe 'models/animated_node', ->
    describe 'AnimatedNode', ->
      create_node = (properties, children, selected) ->
        node = properties
        animated_node = new AnimatedNode(node, children, selected)
        [node, animated_node]

      mock_animator = { animate_object_properties: () -> }

      it 'should point to the original node with .node', ->
        [node, animated_node] = create_node({}, [], false)
        expect(animated_node.node).toBe(node)

      it 'should start with loaded_fraction=0 when the node has no children', ->
        [node, animated_node] = create_node({}, undefined, false)
        expect(animated_node.loaded_fraction.current).toEqual(0)

      it 'should start with loaded_fraction=1 when the node has children', ->
        [node, animated_node] = create_node({}, [], false)
        expect(animated_node.loaded_fraction.current).toEqual(1)

      it 'should start with selected_fraction=0 when the node is not selected', ->
        [node, animated_node] = create_node({}, [], false)
        expect(animated_node.selected_fraction.current).toEqual(0)

      it 'should start with selected_fraction=0 when the node is selected', ->
        [node, animated_node] = create_node({}, [], true)
        expect(animated_node.selected_fraction.current).toEqual(1)

      describe 'starting with an unloaded+unselected node', ->
        node = undefined
        animated_node = undefined

        beforeEach ->
          [node, animated_node] = create_node({}, undefined, false)

        it 'should animate towards selected_fraction=1', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.set_selected(true, mock_animator)
          expect(mock_animator.animate_object_properties).toHaveBeenCalledWith(animated_node, { selected_fraction: 1 }, undefined, undefined)

        it 'should pass time through to animator on set_selected', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.set_selected(true, mock_animator, 1000)
          expect(mock_animator.animate_object_properties.mostRecentCall.args[3]).toEqual(1000)

        it 'should set children on load()', ->
          [child, animated_child] = create_node({}, undefined, false)
          animated_node.load([animated_child], mock_animator)
          expect(animated_node.children).toEqual([animated_child])

        it 'should animate towards loaded_fraction=1 on load', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.load([], mock_animator)
          expect(mock_animator.animate_object_properties).toHaveBeenCalledWith(animated_node, { loaded_fraction: 1 }, undefined, undefined)

        it 'should pass time through to animator on load', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.load([], mock_animator, 1000)
          expect(mock_animator.animate_object_properties.mostRecentCall.args[3]).toEqual(1000)

      describe 'starting with a selected node', ->
        node = undefined
        animated_node = undefined

        beforeEach ->
          [node, animated_node] = create_node({}, undefined, true)

        it 'should animate towards selected_fraction=0', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.set_selected(false, mock_animator)
          expect(mock_animator.animate_object_properties).toHaveBeenCalledWith(animated_node, { selected_fraction: 0 }, undefined, undefined)

      describe 'starting with a loaded node', ->
        node = undefined
        animated_node = undefined

        beforeEach ->
          [node, animated_node] = create_node({}, [], false)

        it 'should animate towards loaded_fraction=0 on unload', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.unload(mock_animator)
          expect(mock_animator.animate_object_properties).toHaveBeenCalled()
          # We can't inspect args[2], but let's look at the rest
          mrc = mock_animator.animate_object_properties.mostRecentCall
          expect(mrc.args[0]).toBe(animated_node)
          expect(mrc.args[1]).toEqual({ loaded_fraction: 0 })
          expect(mrc.args[3]).toEqual(undefined)

        it 'should pass time through to animator on unload', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.unload(mock_animator, 1000)
          expect(mock_animator.animate_object_properties.mostRecentCall.args[3]).toEqual(1000)

        it 'should not clear children on unload (yet)', ->
          animated_node.unload(mock_animator)
          expect(animated_node.children).toBeDefined()

        it 'should set children=undefined when unload is done', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.unload(mock_animator)
          mock_animator.animate_object_properties.mostRecentCall.args[2].call({})
          expect(animated_node.children).toBeUndefined()

        it 'should not unload children if load() was called after unload() and before the animation completed', ->
          spyOn(mock_animator, 'animate_object_properties')
          animated_node.unload(mock_animator)
          callback = mock_animator.animate_object_properties.mostRecentCall.args[2]
          animated_node.load([], mock_animator)
          callback()
          expect(animated_node.children).toBeDefined()
