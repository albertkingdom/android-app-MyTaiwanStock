## Purpose

Lets a user send screenshots from the system share sheet straight into the trade screenshot import, without opening the app and searching the photo library first.

## ADDED Requirements

### Requirement: Accept shared images from the system share sheet

The app SHALL appear in the system share sheet for images by declaring `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters with the MIME type `image/*`. When the app receives such an intent with at least one image URI, the system SHALL switch to the stock list tab and open the trade screenshot import with those images, skipping the photo picker. The system SHALL recognize, preview and write the shared images exactly as images chosen in the photo picker, and SHALL NOT write any record before the user confirms in the preview. The system SHALL NOT copy, store or upload the shared images.

#### Scenario: Two images are shared

- **WHEN** the user shares two screenshots that contain trade rows to the app
- **THEN** the app SHALL open the import and show the trades from both images in one preview
- **AND** no record SHALL be written until the user confirms

#### Scenario: Non-image or empty share

- **WHEN** the received intent has another action, a MIME type that does not start with "image/", or no image URI
- **THEN** the app SHALL NOT open the import

#### Scenario: Activity recreated after handling a share

- **WHEN** the screen is rotated after a shared intent opened the import
- **THEN** the app SHALL NOT open a second import for the same intent

#### Scenario: App already running

- **WHEN** the app is already open on another tab and receives a shared image
- **THEN** the app SHALL switch to the stock list tab and open the import
