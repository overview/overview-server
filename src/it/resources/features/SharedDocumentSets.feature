Feature: Shared document sets
  In order to make Overview popular
  As a user
  I should be able to share a document set with others

  Scenario: Sharing a document set
    Given there is a document set "example document set" owned by "user@example.org"
      And I am logged in as "user@example.org"
    When I browse to the document sets page
     And I open the share dialog for "example document set"
     And I enter an email of "user2@example.org"
     And I click the "Add user" button
     And I wait for all AJAX requests to complete
    Then "user2@example.org" should be allowed to view the document set "example document set"

  Scenario: Un-sharing a document set
    Given there is a document set "example document set" owned by "user@example.org"
      And "user2@example.org" is allowed to view the document set "example document set"
      And I am logged in as "user@example.org"
    When I browse to the document sets page
     And I open the share dialog for "example document set"
     And I hover over the list item "user2@example.org"
     And I click the "remove" link
    Then "user2@example.org" should not be allowed to view the document set "example document set"

  Scenario: Finding a shared document set
    Given there is a document set "example document set" owned by "user@example.org"
      And "user2@example.org" is allowed to view the document set "example document set"
      And I am logged in as "user2@example.org"
    When I browse to the document sets page
     And I click the "Document sets shared with you" link
     And I wait for all AJAX requests to complete
    Then I should see a clone button for the shared document set "example document set"

  @worker
  Scenario: Cloning a shared document set
    Given there is a document set "example document set" owned by "user@example.org"
      And "user2@example.org" is allowed to view the document set "example document set"
      And I am logged in as "user2@example.org"
    When I clone the shared document set "example document set"
     And I wait for all jobs to complete
     And I wait for all AJAX requests to complete
     And I wait for all animations to complete
    Then I should see a document set "example document set"
