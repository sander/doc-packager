Feature: Local inventory

  Example: Site with attachments
    Given a directory with contents
      | File path             |
      | /index.html           |
      | /other.html           |
      | /dir1/index.html      |
      | /dir1/image.png       |
      | /dir2/dir3/other.html |
      | /dir2/dir4/image.png  |
    When I generate a local inventory
    Then the inventory contains the following pages:
      | Inventory path   | File path             |
      | /                | /index.html           |
      | /dir1            | /dir1/index.html      |
      | /dir2            |                       |
      | /dir2/dir3       |                       |
      | /dir2/dir3/other | /dir2/dir3/other.html |
      | /other           | /other.html           |
    And the inventory contains the following attachments:
      | Inventory path | Attachment name | File path            |
      | /dir1          | image.png       | /dir1/image.png      |
      | /dir2          | image.png       | /dir2/dir4/image.png |

  Example: Nonexistent directory
    When I ask to generate a local inventory for a file
    Then I get an error that the provided path is not a directory
