module SessionHelpers
  module LoginHelper
    def log_in_as(user)
      visit('/login')
      within('.session-form', wait: WAIT_LOAD) do
        fill_in('Email', with: user[:email])
        fill_in('Password', with: user[:password])
        click_on('Log in')
      end
      # Wait for Bootstrap to respond to clicks. This will happen before $.ready is set.
      wait_for_javascript_to_return_true('window.jQuery && window.jQuery.ready', wait: WAIT_LOAD)
    end
  end
end
