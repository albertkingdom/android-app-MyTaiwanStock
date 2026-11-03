## Purpose

Lets a user send screenshots from the system share sheet straight into the trade screenshot import, without opening the app and searching the photo library first.

## ADDED Requirements

### Requirement: Accept shared images from the system share sheet

The app SHALL appear in the system share sheet for images by declaring `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters with the MIME type `image/*`. When the app receives such an intent with at least one image URI, taken from `EXTRA_STREAM` or, when that is missing, from the intent's clip data, the system SHALL switch to the stock list tab and open the trade screenshot import with those images, skipping the photo picker. The system SHALL recognize, preview and write the shared images exactly as images chosen in the photo picker, and SHALL NOT write any record before the user confirms in the preview. The system SHALL NOT copy, store or upload the shared images. The system SHALL read only `content:` URIs whose authority does not belong to this app, and SHALL ignore any other shared URI.

#### Scenario: Two images are shared

- **WHEN** the user shares two screenshots that contain trade rows to the app
- **THEN** the app SHALL open the import and show the trades from both images in one preview
- **AND** no record SHALL be written until the user confirms

#### Scenario: Non-image or empty share

- **WHEN** the received intent has another action, a MIME type that does not start with "image/", or no image URI
- **THEN** the app SHALL NOT open the import

#### Scenario: Shared URI points at the app's own files

- **WHEN** a share carries a `file:` URI or a `content:` URI of one of this app's own providers
- **THEN** the app SHALL ignore that URI and SHALL NOT read it
- **AND** the app SHALL show a message that some shared images cannot be read
- **AND** when no shared URI is left, the app SHALL NOT open the import

#### Scenario: Reopened from the recent apps screen

- **WHEN** a task that was started by a share is reopened from the recent apps screen
- **THEN** the app SHALL NOT open the import again for that earlier share

#### Scenario: Activity recreated after handling a share

- **WHEN** the screen is rotated after a shared intent opened the import
- **THEN** the app SHALL NOT open a second import for the same intent

#### Scenario: Shared from another app

- **WHEN** the user shares an image from another app
- **THEN** the import SHALL open on top of the sharing app
- **AND** after the import completes, the app SHALL show its stock list
- **AND** pressing back from that stock list SHALL return to the sharing app

#### Scenario: Image delivered only in clip data

- **WHEN** the intent carries the image URI in its clip data and not in `EXTRA_STREAM`
- **THEN** the app SHALL open the import with that image

#### Scenario: Share received while the app is already open

- **WHEN** the user shares images while the app is already running, possibly with an import open
- **THEN** the system SHALL open a new app window on top of the sharing app with an import for the shared images
- **AND** an import already open in the other window SHALL stay unchanged
- **AND** when both imports are confirmed, they SHALL be written one after the other and SHALL NOT store the same timestamp twice
