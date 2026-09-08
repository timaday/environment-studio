# Independent mock trust certificate

`mock-ca.pem` is a newly invented self-signed public CA certificate generated for
hosted destination configuration boundary tests. Its RSA key was generated only
in process memory and was never saved or printed. No private key is present or
recoverable from this fixture. It identifies no real service, account, application
or environment. This certificate does not qualify TLS connectivity or deployment
trust policy; tests only use its public bytes to exercise certificate-only parsing.
