@onmessage = (e) =>
  callback_name = "callback_#{e.data.request_id}"
  this[callback_name] = (data) =>
    delete this[callback_name]
    @postMessage({ request_id: e.data.request_id, data: data })

  importScripts("https://#{encodeURIComponent(e.data.username)}:#{encodeURIComponent(e.data.password)}@www.documentcloud.org/api/projects.json?callback=#{encodeURIComponent(callback_name)}")
