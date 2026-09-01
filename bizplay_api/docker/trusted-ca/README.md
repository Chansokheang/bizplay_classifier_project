# Extra trusted CA certificates

Drop `.pem` / `.crt` certificates here and the Docker build imports them into the JVM
truststore of BOTH image stages (build + runtime).

Why this exists: the campus/lab network (Somansa) intercepts TLS and re-signs every HTTPS
connection with its own CA. Without that CA trusted, Maven inside the build container dies on
`PKIX path building failed` downloading from Maven Central — and the app at runtime would fail
the same way calling cloud-dev.bizplay.biz or the LLM endpoints.

To capture the proxy's CA chain **on the affected server**:

```bash
openssl s_client -showcerts -connect repo.maven.apache.org:443 </dev/null 2>/dev/null \
  | awk '/BEGIN CERTIFICATE/{f="docker/trusted-ca/proxy-ca-"++i".pem"} f{print>f} /END CERTIFICATE/{f=""}'
```

Inspect what you got (`openssl x509 -in <file> -noout -subject -issuer -enddate`); the last
certificate is the proxy's root CA — that one is required. Keeping the whole chain is harmless.

This folder is safe to commit: CA certificates are public keys. An empty folder (just this
README) is also fine — the import step skips silently and the build behaves exactly as before.
