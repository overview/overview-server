require 'minitest/autorun'
require 'minitest/reporters'
require 'minitest/spec'
require 'capybara'
require 'selenium-webdriver'

OVERVIEW_URL = ENV['OVERVIEW_URL'] || "http://overview-web"
OVERVIEW_ADMIN_EMAIL = ENV['OVERVIEW_ADMIN_EMAIL'] || "admin@overviewdocs.com"
OVERVIEW_ADMIN_PASSWORD = ENV['OVERVIEW_ADMIN_PASSWORD'] || "admin@overviewdocs.com"

WAIT_FAST = 1       # 1s: how long to wait for JavaScript to execute
WAIT_TRANSITION = 2 # 2s: longest transition -- plus extra in case CPU is bogged down
WAIT_LOAD = 10      # 10s: how long to wait for a page to load
WAIT_SLOW = 70      # 60s: how long to wait for Overview to complete an import job

Minitest::Reporters.use! [
  Minitest::Reporters::SpecReporter.new,
  Minitest::Reporters::JUnitReporter.new('/app/reports')
]

Selenium::WebDriver.logger.level = "warn"
Capybara.register_driver :overview_chromium do |app|
  options = Selenium::WebDriver::Chrome::Options.new
  options.add_argument('--disable-gpu')
  options.add_argument('--no-sandbox') # Docker doesn't allow sandboxing
  options.add_argument('--disable-dev-shm-usage') # https://bugs.chromium.org/p/chromium/issues/detail?id=736452
  options.add_argument('--window-size=1200x900')
  options.add_argument('--headless') if ENV['HEADLESS'] != 'false'

  Capybara::Selenium::Driver.new(
    app,
    browser: :chrome,
    options: options
  )
end
Capybara.default_driver = :overview_chromium
Capybara.app_host = OVERVIEW_URL
Capybara.run_server = false
Capybara.default_max_wait_time = 0 # VERY IMPORTANT: default must be 0. Be explicit about where races are in each test!

# Include all helpers, unordered.
Dir.glob('/app/helpers/**/*.rb')
  .map { |e| e.sub(/\.rb$/, '') }
  .each { |e| require(e) }

class OverviewIntegrationSpec < Minitest::Test
  extend Minitest::Spec::DSL
  include FeatureHelper
end

Minitest::Spec.register_spec_type(/.*/, OverviewIntegrationSpec)
