# This is part of an elaborate workaround for
# https://play.lighthouseapp.com/projects/82401/tickets/103-commonjs-should-work-for-coffeescript-files
#
# In our HTML, we'll include this file first and then all others. This file
# makes CommonJS-style require() tags work as expected.
#
# When the bug is fixed, we'll do this:
#
# 1) fix the HTML to include only main.min.js
# 2) remove all calls to make_export_object() in every JS file
# 3) remove this file
# 4) fix any dependency loops we find

exports_root = {}

module_name_to_export_object = (module_name) ->
  parts = module_name.split(/\//g)
  exports = exports_root

  for part in parts
    exports = (exports["_#{part}"] ||= {})

  exports

window.require = (module_name) ->
  module_name_to_export_object(module_name)

window.require.make_export_object = (module_name) ->
  module_name_to_export_object(module_name)
