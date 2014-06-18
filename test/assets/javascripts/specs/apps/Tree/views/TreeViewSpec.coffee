define [
  'backbone'
  'apps/Tree/views/TreeView',
], (Backbone, TreeView) ->
  describe 'apps/Tree/views/TreeView', ->
    class Document extends Backbone.Model

    describe 'helpers', ->
      describe '#getHighlightedNodeIds', ->
        onDemandTree =
          nodes:
            1:
              id: 1
              parentId: null
            2:
              id: 2
              parentId: 1
            3:
              id: 3
              parentId: 2
            4:
              id: 4
              parentId: 2
            5:
              id: 5
              parentId: 3

        it 'highlights only the selection and its child nodes', ->
          document = new Document(id: 12, nodeids: [ 5, 3, 2, 1 ])
          expect(
            TreeView.helpers.getHighlightedNodeIds({ type: 'node', node: { id : 3 } }, document, onDemandTree)
          ).to.deep.eq(
            3: null
            5: null
          )

