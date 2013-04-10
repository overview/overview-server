define [], ->
  if window.File?
    proto = window.File.prototype
    proto.slice ||= proto.webkitSlice || proto.mozSlice
    window.File
  else
    undefined
