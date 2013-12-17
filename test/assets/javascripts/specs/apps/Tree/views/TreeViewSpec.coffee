  define [
    'apps/Tree/views/TreeView',
  ], (TreeView) ->
    describe 'apps/Tree/views/TreeView', ->
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

          documentStore =
            documents:
              12:
                id: 12
                nodeids: [5,3,2,1]

          it 'highlights only the selection and its child nodes', ->
            expect(
              TreeView.helpers.getHighlightedNodeIds({ type: 'node', nodeId: 3 }, 12, onDemandTree, documentStore)
            ).toEqual(
              3: null
              5: null
            )

