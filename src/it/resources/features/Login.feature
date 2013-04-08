Feature: Login
  In order to let users manage their own stuff
  As a user
  I should be able to log in

  Scenario: Logging in
    Given I am not logged in
    And there is a user "user@example.org" with password "OlReshtob7"
    When I browse to the welcome page
    And I log in with email "user@example.org" and password "OlReshtob7"
    Then I should be logged in as "user@example.org"
