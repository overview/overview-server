DrawableNode = require('models/drawable_node').DrawableNode

describe 'models/drawable_node', ->
  describe 'DrawableNode', ->
    node = undefined
    animated_node = undefined
    drawable_node = undefined

    describe 'with a leaf animated_node', ->
      beforeEach ->
        node = { id: 1, doclist: { n: 1 } }
        animated_node = { node: node, loaded: true, loaded_fraction: { current: 1 } }
        drawable_node = new DrawableNode(animated_node, 1)

      it 'should contain an animated_node', ->
        expect(drawable_node.animated_node).toBe(animated_node)

      it 'should contain a fraction', ->
        expect(drawable_node.fraction).toEqual(1)

      it 'should have width=1', ->
        expect(drawable_node.width).toEqual(1)

      it 'should have width_with_padding > width', ->
        expect(drawable_node.width_with_padding).toBeGreaterThan(drawable_node.width)

      it 'should have children=undefined', ->
        expect(drawable_node.children).toBeUndefined()

      it 'should have height=1', ->
        expect(drawable_node.height).toEqual(1)

    describe 'with a non-expanded animated_node', ->
      beforeEach ->
        node = { id: 1, doclist: { n: 2 } }
        animated_node = { node: node, children: undefined, loaded: false, loaded_fraction: { current: 0 } }
        drawable_node = new DrawableNode(animated_node, 1)

      it 'should have children=undefined', ->
        expect(drawable_node.children).toBeUndefined()

      it 'should have height=1', ->
        expect(drawable_node.height).toEqual(1)

    describe 'with an expanded animated_node', ->
      beforeEach ->
        node = { id: 1, doclist: { n: 2 } }
        node2 = { id: 2, doclist: { n: 1 } }
        node3 = { id: 3, doclist: { n: 1 } }
        animated_node2 = { node: node2, loaded: true, loaded_fraction: { current: 1 } }
        animated_node3 = { node: node3, loaded: true, loaded_fraction: { current: 1 } }
        animated_node = { node: node, children: [ animated_node2, animated_node3 ], loaded: true, loaded_fraction: { current: 1 } }
        drawable_node = new DrawableNode(animated_node, 1)

      it 'should have children corresponding to animated nodes', ->
        expect(drawable_node.children.map((dn) -> dn.animated_node)).toEqual(animated_node.children)

      it 'should have width=2', ->
        expect(drawable_node.width).toEqual(2)

      it 'should have width_with_padding > width', ->
        expect(drawable_node.width_with_padding).toBeGreaterThan(drawable_node.width)

      it 'should set relative_x on children, centering them', ->
        expect(drawable_node.children[0].relative_x).toBeLessThan(0)
        expect(drawable_node.children[1].relative_x).toBeGreaterThan(0)

      it 'should have height=2', ->
        expect(drawable_node.height).toEqual(2)

    describe 'with a half-loaded expanded animated_node', ->
      beforeEach ->
        node = { id: 1, doclist: { n: 2 } }
        node2 = { id: 2, doclist: { n: 1 } }
        node3 = { id: 3, doclist: { n: 1 } }
        animated_node2 = { node: node2, loaded: true, loaded_fraction: { current: 1 } }
        animated_node3 = { node: node3, loaded: true, loaded_fraction: { current: 1 } }
        animated_node = { node: node, children: [ animated_node2, animated_node3 ], loaded: true, loaded_fraction: { current: 0.05 } }
        drawable_node = new DrawableNode(animated_node, 1)

      it 'should set fraction=1 on the node', ->
        expect(drawable_node.fraction).toEqual(1)

      it 'should have width_with_padding > width', ->
        expect(drawable_node.width_with_padding).toBeGreaterThan(drawable_node.width)

      it 'should set fraction=0.05 on the children', ->
        expect(drawable_node.children.map((dn) -> dn.fraction)).toEqual([0.05, 0.05])

      it 'should set height=1.05', ->
        expect(drawable_node.height).toEqual(1.05)

      it 'should set relative_x on children, centering them', ->
        expect(drawable_node.children[0].relative_x).toBeLessThan(0)
        expect(drawable_node.children[1].relative_x).toBeGreaterThan(0)
