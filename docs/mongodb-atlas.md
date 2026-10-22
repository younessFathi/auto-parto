# MongoDB Atlas connection

The application reads its MongoDB connection string from `MONGODB_URI`. Local
development can use the default `mongodb://localhost:27017/auto-parto`; a
managed deployment should provide the Atlas connection string through an
environment variable or protected secret.

Example shape, with placeholders only:

```text
mongodb+srv://<username>:<url-encoded-password>@<cluster-host>/<database>?retryWrites=true&w=majority
```

The username and password are part of the URI and must be URL-encoded before
they are inserted. In particular, encode reserved characters such as `@`, `:`,
`/`, `?`, `#`, and `%`. Store the completed value only in a protected secret
such as `MONGODB_URI`; do not put it in `application*.yml`, `.env.example`, CI
configuration, logs, or documentation.

For Docker Compose, `MONGODB_URI` is passed to the application as
`SPRING_DATA_MONGODB_URI`. For Spring Boot deployments, either provide
`MONGODB_URI` or set the standard `SPRING_DATA_MONGODB_URI` property directly.
