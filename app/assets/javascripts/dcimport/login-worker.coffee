@onmessage = (e) =>
  callback_name = "callback_#{e.data.request_id}"
  this[callback_name] = (data) =>
    delete this[callback_name]
    @postMessage({ request_id: e.data.request_id, data: data })

  try
    importScripts("https://#{encodeURIComponent(e.data.username)}:#{encodeURIComponent(e.data.password)}@www.documentcloud.org/api/projects.json?callback=#{encodeURIComponent(callback_name)}")
  catch e
    # ignore

  # Synchronous. If the callback still exists here, it wasn't run
  if this[callback_name]?
    delete this[callback_name]
    @postMessage({ request_id: e.data.request_id, error: 'Error' })
