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

def chrome_args
  # Dynamic: each time we call this, create a new user-data-dir
  ret = [
    'disable-gpu',
    'no-sandbox',
    "user-data-dir=/tmp/overview-integration-tester-#{rand(0xffff)}"
  ]
  ret << 'headless' if ENV['HEADLESS'] != 'false'
  ret
end

Selenium::WebDriver.logger.level = "warn"
Capybara.register_driver :selenium_chrome_headless do |app|
  # https://robots.thoughtbot.com/headless-feature-specs-with-chrome
  capabilities = Selenium::WebDriver::Remote::Capabilities.chrome(
    chromeOptions: { args: chrome_args }
  )

  Capybara::Selenium::Driver.new app,
    browser: :chrome,
    desired_capabilities: capabilities
end
Capybara.default_driver = :selenium_chrome_headless
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
