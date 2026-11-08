sequenceDiagram
autonumber
participant Client
participant Controller as FcmNotificationController
participant SellerSvc as SellerService
participant FcmSvc as ResilientFcmService
participant Queue as PushNotificationQueue
participant Redis as Redis (Cache)
participant Executor as RetryExecutor

    Note over Client, Controller: 1. Initial Request
    Client->>Controller: POST /notify/search (SearchNotificationRequest)
    
    Controller->>SellerSvc: findMatchingSellers()
    SellerSvc->>Controller: Returns List<String> (Seller IDs)
    
    Controller->>FcmSvc: sendToUsers(sellerIds, notification)
    
    loop For each Seller ID
        Note over FcmSvc, Queue: 2. Initial Send Failure
        FcmSvc--xClient: Fcm Send Fails (Network/Error)
        FcmSvc->>Queue: enqueueForRetry(userId, notification)
        
        activate Queue
        Queue->>Redis: storeInQueue(retryCount=0)
        Queue->>Executor: scheduleRetry(delay=5s)
        deactivate Queue
    end
    
    Controller-->>Client: 200 OK (Acknowledged)

    Note over Queue, Executor: 3. Retry Loop (Async)
    
    rect rgb(255, 240, 240)
        Note right of Executor: Retry Attempt 1
        Executor->>Queue: processRetry()
        activate Queue
        Queue->>Redis: removeFromQueue()
        Queue->>Queue: attemptRedelivery()
        Queue--xClient: Send Fails
        Queue->>Queue: scheduleNextRetry(count=1)
        Queue->>Redis: storeInQueue(retryCount=1)
        Queue->>Executor: scheduleRetry(delay=30s)
        deactivate Queue
    end

    rect rgb(255, 230, 230)
        Note right of Executor: Retry Attempt 2
        Executor->>Queue: processRetry()
        activate Queue
        Queue->>Redis: removeFromQueue()
        Queue->>Queue: attemptRedelivery()
        Queue--xClient: Send Fails
        Queue->>Queue: scheduleNextRetry(count=2)
        Queue->>Redis: storeInQueue(retryCount=2)
        Queue->>Executor: scheduleRetry(delay=5m)
        deactivate Queue
    end

    rect rgb(255, 200, 200)
        Note right of Executor: Retry Attempt 3 (Final)
        Executor->>Queue: processRetry()
        activate Queue
        Queue->>Redis: removeFromQueue()
        Queue->>Queue: attemptRedelivery()
        Queue--xClient: Send Fails
        
        Note over Queue, Redis: 4. Dead Letter Logic
        Queue->>Queue: scheduleNextRetry(count=3)
        
        alt retryCount >= 3
            Queue->>Queue: moveToDeadLetter()
            Queue->>Redis: LPUSH push:dead:{userId}
            Note right of Redis: Notification Stored in DLQ
        end
        deactivate Queue
    end

# PostgreSQL development migration

PostgreSQL is the application persistence boundary. To run the schema and fixtures locally, follow [`docs/postgresql-development-cutover.md`](docs/postgresql-development-cutover.md). The Compose profile provisions distinct runtime and migration roles, runs Flyway as a one-shot job, and provides idempotent development fixtures.

Authentication currently uses the OTP-to-JWT canonical model documented in [`docs/canonical-authentication.md`](docs/canonical-authentication.md). Incomplete Google and Clerk integrations remain disabled.

The canonical versioned HTTP API and legacy-route migration policy are documented in [`docs/api-boundaries.md`](docs/api-boundaries.md).
