# Stand-in credits service

A tiny gRPC server implementing `proto/credits.proto` that approves every
Authorise call and logs what it's sent. It lets Yardmaster be built and tested
against the credits contract before the real Credits Service exists.

Built in step 8 (credits hook), together with mutual TLS. It then joins
`deploy/docker-compose.yml`.
