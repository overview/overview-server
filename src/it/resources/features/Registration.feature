Feature: Registration
  In order to let users log in
  So they can manage their own stuff
  As a user
  I should be able to register

  Scenario: Registering
    When I browse to the welcome page
     And I register with email "user@example.org" and password "OlReshtob7"
    Then "user@example.org" should receive an email with a confirmation token
     And I should not be logged in

  Scenario: Logging in before confirmation
    Given there is a user "user@example.org" with password "OlReshtob7" and confirmation token "abcd123"
    When I log in with email "user@example.org" and password "OlReshtob7"
    Then I should not be logged in
     And I should see an error in the login form

  Scenario: Confirming registration
    Given there is a user "user@example.org" with password "OlReshtob7" and confirmation token "abcd123"
    When I confirm registration with token "abcd123"
    Then user "user@example.org" should be confirmed
     And I should be logged in as "user@example.org"

  Scenario: Requesting password reset
    Given there is a user "user@example.org" with password "OlReshtob7"
    When I request a password reset for "user@example.org"
    Then "user@example.org" should receive an email with a reset-password token
     And "user@example.org" should have a password-reset token
     And I should not be logged in

  Scenario: Requesting password reset of a nonexistent account
    When I request a password reset for "does-not-exist@example.org"
    Then "does-not-exist@example.org" should receive an email without a reset-password token
     And I should not be logged in

  Scenario: Logging in when there is a reset-password request
    Given there is a user "user@example.org" with password "OlReshtob7" and reset-password token "abcd123"
    When I log in with email "user@example.org" and password "OlReshtob7"
    Then I should be logged in as "user@example.org"

  Scenario: Resetting password
    Given there is a user "user@example.org" with password "OlReshtob7" and reset-password token "abcd123"
    When I browse to the reset-password page with token "abcd123"
     And I reset the password to "OlReshtob8"
    Then "user@example.org" should have password "OlReshtob8"
     And I should be logged in as "user@example.org"
