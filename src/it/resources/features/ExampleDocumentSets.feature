Feature: Example document sets
  In order to familiarize people with Overview
  To compel them to use it
  As a user
  I should be able to clone a document set

  Scenario: New user sees clone buttons
    Given there is a public document set "example document set" owned by "admin@example.org"
      And I am logged in as "user@example.org"
    When I browse to the document sets page
     And I wait for all AJAX requests to complete
     And I wait for all animations to complete
    Then I should see a clone button for the example document set "example document set"

  @worker
  Scenario: Clone an example document set
    Given there is a public document set "example document set" owned by "admin@example.org"
      And I am logged in as "user@example.org"
    When I clone the example document set "example document set"
     And I wait for all jobs to complete
     And I wait for all AJAX requests to complete
     And I wait for all animations to complete
    Then I should see a document set "example document set"

  Scenario: Mark a document set public
    Given there is a document set "example document set" owned by "admin@example.org"
      And I am logged in as "admin@example.org"
    When I browse to the document sets page
     And I open the share dialog for "example document set"
     And I click the "Set as example document set" checkbox
     And I wait for all AJAX requests to complete
    Then the document set "example document set" should be public

  Scenario: Non-admin can't mark a document set public
    Given there is a document set "example document set" owned by "user@example.org"
      And I am logged in as "user@example.org"
    When I browse to the document sets page
     And I open the share dialog for "example document set"
    Then I should not see a "Set as example document set" checkbox
