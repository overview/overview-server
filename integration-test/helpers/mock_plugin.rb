require 'webrick'

# A plugin that starts on port 3333.
#
# Usage:
#
#     MockPlugin.serving_mock_plugin_on_port('right-pane', 3333) do
#       # Do stuff assuming an HTTP server is serving an Overview plugin
#       # whose "/show" returns the contents of
#       # "/app/files/mock-plugins/right-pane.html"
#     end
#     # Now the HTTP server is gone, as if it never existed
class MockPlugin
  def initialize(slug)
    @slug = slug
  end

  # Runs block with a server listening on the given port
  def with_server_on_port(port, &block)
    server = MockPlugin.create_server(@slug, port)
    thread = MockPlugin.start_server_in_thread(server)
    begin
      yield
    ensure
      MockPlugin.stop_server_and_join_thread(server, thread)
    end
  end

  def self.serving_mock_plugin_on_port(slug, port, &block)
    mock_plugin = MockPlugin.new(slug)
    mock_plugin.with_server_on_port(port, &block)
  end

  # Runs server in the background and returns its thread as soon as the
  # server is listening for HTTP requests.
  #
  # Call `stop_server_and_join_thread` to kill the server and return once it's done.
  def self.start_server_in_thread(server)
    thread = Thread.new do
      server.start # returns after @server.shutdown
    end

    # Wait for server to begin listening for HTTP requests
    t1 = Time.now
    while server.status != :Running
      sleep 0.02
      if Time.now - t1 > 15
        throw Exception.new('WEBrick server did not start in a timely fashion. Test suite will now have undefined behavior.')
      end
    end

    thread
  end

  # Makes the WEBrick thread stop
  #
  # Returns as soon as the TCP port is available to use again.
  def self.stop_server_and_join_thread(server, thread)
    server.shutdown

    t1 = Time.now
    while server.status != :Stop
      sleep 0.02
      if Time.now - t1 > 10
        throw Exception.new('WEBrick server did not stop in a timely fashion. Test suite will now have undefined behavior.')
      end
    end

    thread.join
  end

  # Returns a new WEBrick server
  def self.create_server(slug, port)
    path = "#{File.expand_path(File.dirname(__FILE__))}/../files/mock-plugins/#{slug}.html"

    server = WEBrick::HTTPServer.new({
      Port: port,
      BindAddress: '0.0.0.0', # ViewFilter needs to be accessed by Overview server
      AccessLog: [],
      Logger: WEBrick::Log.new('/dev/null')
    })

    server.mount_proc '/metadata' do |req, res|
      res.status = 200
      res['Content-Type'] = 'application/json'
      res['Access-Control-Allow-Origin'] = '*'
      res.body = '{}'
    end
    server.mount_proc '/show' do |req, res|
      res.status = 200
      res['Content-Type'] = 'text/html; charset=utf-8'
      res['Cache-Control'] = 'no-cache'
      res.body = IO.read(path, mode: 'rb')
    end

    server
  end
end
