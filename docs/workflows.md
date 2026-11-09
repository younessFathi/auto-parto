# Functional Workflows

These diagrams explain what buyers, sellers and members can do, the decisions they make, and the resulting business outcomes. They describe backend capabilities; they do not imply that a particular screen or mobile application has been built. Availability depends on the enabled features and account permissions.

Start with a journey below. Each diagram covers one user goal. The [technical workflow reference](technical-workflows.md) contains implementation and operations details.

## Journey index

- [Join or sign in](#join-or-sign-in)
- [Manage a profile or deactivate an account](#manage-a-profile-or-deactivate-an-account)
- [Request a car part](#request-a-car-part)
- [Publish and manage a listing](#publish-and-manage-a-listing)
- [Find parts matching a request](#find-parts-matching-a-request)
- [Make and decide on an offer](#make-and-decide-on-an-offer)
- [View or change a wallet balance](#view-or-change-a-wallet-balance)
- [Attach photos to a request or listing](#attach-photos-to-a-request-or-listing)
- [View an uploaded image](#view-an-uploaded-image)
- [Choose notification preferences](#choose-notification-preferences)
- [Receive account notifications](#receive-account-notifications)
- [Enable push notifications on a device](#enable-push-notifications-on-a-device)

## Join or sign in

A buyer or seller accesses their account.

```mermaid
flowchart TD
    A["Choose buyer or seller access"] --> B["Provide identity details and request a verification code"]
    B --> C["Receive the code through the enabled verification channel"]
    C --> D["Submit the code"]
    D --> E{"Code accepted?"}
    E -->|No| F["Correct the code or request another when allowed"]
    F --> D
    E -->|Yes| G["Account is created when eligible or existing account is recognized"]
    G --> H["Continue as the verified buyer or seller"]
```

Public onboarding supports buyers and sellers. Administrator access follows a separate controlled process. Repeated attempts can be temporarily limited.

Business rules and implementation reference: [supporting documentation](canonical-authentication.md).

## Manage a profile or deactivate an account

A member maintains their own account information.

```mermaid
flowchart TD
    A["Signed-in buyer or seller"] --> B{"What do you want to do?"}
    B -->|Edit profile| C["Change your profile details"]
    C --> D{"Details valid?"}
    D -->|No| C
    D -->|Yes| E["Save and view updated profile"]
    B -->|Deactivate account| F["Request deactivation of your own account"]
    F --> G["Account is marked inactive"]
```

Deactivation marks the account inactive. It does not promise immediate termination of every existing signed-in session.

Business rules and implementation reference: [supporting documentation](canonical-authentication.md).

## Request a car part

A buyer describes a needed part and manages the request.

```mermaid
flowchart TD
    A["Buyer describes the part needed"] --> B{"Required details valid?"}
    B -->|No| A
    B -->|Yes| C["Create an open request"]
    C --> D["View your requests"]
    D --> E{"Next action?"}
    E -->|Find parts| F["Look for matching published listings"]
    E -->|Finish request| G["Close the open request"]
    E -->|Withdraw request| H["Cancel the open request"]
    F --> D
```

Only the owner closes or cancels an open request. Closing does not establish that payment or delivery occurred.

Business rules and implementation reference: [supporting documentation](api-part-requests.md).

## Publish and manage a listing

A seller controls when their part is available.

```mermaid
stateDiagram-v2
    state "Draft" as Draft
    state "Published" as Published
    state "Paused" as Paused
    state "Reserved" as Reserved
    state "Closed" as Closed
    [*] --> Draft: Seller creates listing
    Draft --> Published: Publish
    Draft --> Closed: Close
    Published --> Paused: Pause availability
    Paused --> Published: Publish again
    Published --> Reserved: Mark reserved
    Published --> Closed: Close
    Paused --> Closed: Close
    Reserved --> Closed: Close
    Closed --> [*]
```

The seller manages their own listing. Reservation is a listing decision; accepting an offer does not automatically reserve it. Delivery progress is separate.

Business rules and implementation reference: [supporting documentation](api-part-listings.md).

## Find parts matching a request

A buyer discovers available parts for an open request.

```mermaid
flowchart TD
    A["Buyer selects their open request"] --> B["Ask for matching parts"]
    B --> C{"Request eligible and search supported?"}
    C -->|No| D["Review the request or search criteria"]
    C -->|Yes| E["Find published listings matching the criteria"]
    E --> F{"Matches found?"}
    F -->|No| G["No matching listings returned"]
    F -->|Yes| H["Review matching listings"]
    H --> I["Choose whether to make an offer"]
```

Searching does not reserve a part or automatically notify sellers. A request must belong to the buyer and remain open.

Business rules and implementation reference: [supporting documentation](api-part-requests.md).

## Make and decide on an offer

A buyer and seller express and decide on an offer.

```mermaid
flowchart TD
    A["Buyer makes an offer for a listing"] --> C["Offer awaits a decision"]
    B["Seller makes an offer for a request"] --> C
    C --> D{"Participant action?"}
    D -->|Seller accepts| E["Offer accepted"]
    D -->|Seller rejects| F["Offer rejected"]
    D -->|Either participant cancels| G["Offer cancelled"]
    D -->|No action| C
```

Current rules give acceptance and rejection to the seller participant, including when the seller created the offer. Either participant may cancel a pending offer. Acceptance records the decision; payment, reservation and delivery are separate.

Business rules and implementation reference: [supporting documentation](technical-workflows.md#offer-creation-and-decisions).

## View or change a wallet balance

An authorized account holder views or requests a change to their wallet.

```mermaid
flowchart TD
    A["Account holder opens their wallet"] --> B["View balance and transaction history"]
    B --> C{"Requested action?"}
    C -->|View only| D["Review account activity"]
    C -->|Credit or debit| E["Provide amount and currency"]
    E --> F{"Repeated request?"}
    F -->|Same request already completed| G["Show previous result without charging again"]
    F -->|Conflicting repeat| H["Ask caller to resolve the conflicting request"]
    F -->|New request| I{"Valid amount and sufficient funds for a debit?"}
    I -->|No| J["Decline change and keep balance"]
    I -->|Yes| K["Update balance and record the transaction"]
```

Wallet credits and debits are account operations. A credit is not proof of an external payment, and offer acceptance does not automatically move money. Repeat protection applies while the request reference is retained.

Business rules and implementation reference: [supporting documentation](wallet-ledger.md).

## Attach photos to a request or listing

A buyer or seller adds a supported image to their own marketplace item.

```mermaid
flowchart TD
    A["Owner chooses a request or listing"] --> B["Select an image"]
    B --> C{"Allowed image and within limits?"}
    C -->|No| D["Choose a suitable image"]
    D --> B
    C -->|Yes| E["Upload image"]
    E --> F{"Upload completed?"}
    F -->|Yes| G["Image reference is available for the item"]
    F -->|No| H["Show failure so the owner can retry"]
```

Uploads require ownership of the target. Failed or unfinished uploads must not be presented as completed attachments.

Business rules and implementation reference: [supporting documentation](file-storage.md).

## View an uploaded image

A signed-in member retrieves an available image.

```mermaid
flowchart TD
    A["Member requests an image"] --> B{"Signed in?"}
    B -->|No| C["Sign in before continuing"]
    B -->|Yes| D{"Image reference active and content available?"}
    D -->|Yes| E["Return image content"]
    D -->|No| F["Report that the image cannot be retrieved"]
```

Current image retrieval requires sign-in, but does not restrict retrieval to the uploader. This documents the existing access policy.

Business rules and implementation reference: [supporting documentation](file-storage.md).

## Choose notification preferences

A member saves their notification choices.

```mermaid
flowchart TD
    A["Member opens notification preferences"] --> B["View saved choices or defaults"]
    B --> C["Choose notification channels and categories"]
    C --> D["Save choices for your account"]
    D --> E["View updated preferences"]
```

Saving preferences is supported. Preference enforcement is incomplete across delivery paths, so these choices are not currently a universal guarantee that messages will be suppressed.

Business rules and implementation reference: [supporting documentation](technical-workflows.md#notification-preferences).

## Receive account notifications

A member receives updates when the relevant notification features are enabled.

```mermaid
flowchart TD
    A["An enabled feature creates an account notification"] --> B["Keep the notification available for delivery"]
    B --> C{"Member connected for live updates?"}
    C -->|Yes| D["Attempt to send the update"]
    C -->|No| E["Keep pending notification for later attempts"]
    D --> F{"Send attempt succeeds?"}
    F -->|No| E
    F -->|Yes| G["Member can receive the update"]
    E --> H{"Further attempts allowed?"}
    H -->|Yes| C
    H -->|No| I["Record unsuccessful delivery outcome"]
    J["Member reconnects with a valid previous notification reference"] --> K["Return available recent updates after that reference"]
```

A successful send does not prove the member read the message. Catch-up is limited; it is not an unlimited notification history. Marketplace matching and offer actions are not assumed to create notifications automatically.

Business rules and implementation reference: [supporting documentation](notifications-outbox.md).

## Enable push notifications on a device

A member registers a device for supported push updates.

```mermaid
flowchart TD
    A["Member submits a device registration"] --> B{"Device registration allowed for this account?"}
    B -->|No| C["Reject registration without taking another account device"]
    B -->|Yes| D["Register the device for the account"]
    D --> E["An enabled feature requests a push notification"]
    E --> F["Attempt delivery to registered devices"]
    F --> G{"Provider result?"}
    G -->|Accepted| H["Provider accepted the message"]
    G -->|Invalid device registration| I["Stop using the invalid registration"]
    G -->|Other failure| J["Report delivery failure"]
```

Push availability depends on configuration and the device application. Provider acceptance does not prove display or reading. Push is a separate delivery path; automatic fallback from account notifications is not assumed.

Business rules and implementation reference: [supporting documentation](device-tokens.md).
