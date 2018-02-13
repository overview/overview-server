module FeatureHelpers
  module AdminSessionHelper
    # Capybara session in charge of creating and deleting users.
    def admin_session
      # Singleton admin session: open once, used for all tests
      @@admin_session ||= begin
        s = Capybara::Session.new(Capybara.default_driver)

        s.extend SessionHelpers::WaitHelper # used by log_in_as
        s.extend SessionHelpers::LoginHelper # log_in_as
        s.extend FeatureHelpers::AdminSessionHelper::AdminSessionMethods

        s.log_in_as(email: OVERVIEW_ADMIN_EMAIL, password: OVERVIEW_ADMIN_PASSWORD)
        s.click_on('Admin')
        s.click_on('Users', wait: WAIT_FAST)
        s.assert_selector('.new-user', wait: WAIT_LOAD)

        s
      end
    end

    module AdminSessionMethods
      TEST_USER_EMAIL = 'test-RANDOM@example.org'
      TEST_USER_PASSWORD = 'created during test'

      # Creates a new user. You can use its { :email, :password } to log in
      # in another session.
      def create_test_user
        user = {
          email: TEST_USER_EMAIL.sub(/RANDOM/, rand(999999999).to_s),
          password: TEST_USER_PASSWORD
        }
        # Scroll to the bottom of the page, where the new-user form is
        within('.new-user') do
          fill_in('email', with: user[:email])
          fill_in('password', with: user[:password])
          click_on('Create')
        end
        # Wait for user to be created
        assert_selector('tr:not(.updating) td.email', text: user[:email], wait: WAIT_LOAD)
        user
      end

      # Deletes a user created by create_test_user
      def destroy_test_user(user)
        td = find('tr:not(.updating) td.email', text: user[:email])
        tr = td.find(:xpath, '..')
        accept_alert(wait: WAIT_FAST) do
          tr.find_link('Delete', match: :one).click
        end
        # Wait for user to be deleted.
        # (<tr> will have class=updating, then it will disappear after the user is gone)
        assert_no_selector('td.email', text: user[:email], wait: WAIT_LOAD)
      end

      # Grabs the requested token (reset-password or confirmation) for the given
      # user. Assumes the user and token exist.
      #
      # Steps to grab the token:
      #
      # 1. Refresh the page
      # 2. Find the user
      # 3. Grab the HTML attribute that UserAdminApp adds for this very purpose
      def get_user_token(user, token)
        attr_name = "data-#{token}-token"

        # 1. Refresh the page
        refresh
        assert_selector('.new-user', wait: WAIT_LOAD) # wait for page to reload

        # 2. Find the user
        tr = find('tr', text: user[:email], wait: WAIT_LOAD) # wait for users to load

        # 3. Grab the HTML attribute
        tr.find("[#{attr_name}]")[attr_name.to_sym] # return attribute in question
      end
    end
  end
end
