# A node in an AnimatedTree.
#
# This points to a regular node, through ".node", which will never be
# undefined (even after the node is removed from the tree, and there are no
# other references to it).
#
# There are other properties which follow the "animated" pattern (that is,
# they are objects which have a "current" property):
#
# * loaded_fraction: 1 when loaded, 0 otherwise. Animates both ways.
# * selected_fraction: 1 when selected, 0 otherwise.
#
# There are corresponding "loaded" and "selected" booleans, which track the
# final state.
#
# These are set through load(), unload() and set_selected(), which accept an
# Animator.
#
# Finally, there is one more public property, managed automatically after it
# is passed to the constructor and load() methods:
#
# * children: list of child AnimatedNodes, or undefined. Set if and only if
#   loaded || loaded_fraction > 0
class AnimatedNode
  constructor: (@node, @children=undefined, @selected=false) ->
    @loaded = @children?

    @loaded_fraction = { current: @loaded && 1 || 0 }
    @selected_fraction = { current: @selected && 1 || 0 }
    @_load_count = @loaded && 1 || 0 # only remove children when @_load_count=0

  set_selected: (selected, animator, time=undefined) ->
    return if @selected == selected
    @selected = selected
    animator.animate_object_properties(this, { selected_fraction: selected && 1 || 0 }, undefined, time)

  load: (@children, animator, time=undefined) ->
    return if @loaded
    @loaded = true
    @_load_count++
    animator.animate_object_properties(this, { loaded_fraction: 1 }, undefined, time)

  unload: (animator, time=undefined) ->
    return if !@loaded
    @loaded = false
    on_unload = () =>
      @_load_count--
      @children = undefined if @_load_count == 0
    animator.animate_object_properties(this, { loaded_fraction: 0 }, on_unload, time)

exports = require.make_export_object('models/animated_node')
exports.AnimatedNode = AnimatedNode
