This is a Compose Multiplatform project targeting Android, iOS.

This is a ISO/IEC 18013-5:2021 proximity reader for requesting credentials built using
the Multipaz SDK. It includes a backend component for certifying reader authentication keys.

Learn more about [Multipaz](https://github.com/openwallet-foundation-labs/identity-credential)…

Learn more about [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)…

Learn more about [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)…

This is not an official or supported Google product.

## Generating reader keys for import

You can generate [PKCS#12](https://en.wikipedia.org/wiki/PKCS_12) files
for reader keys using the `multipazctl` and `openssl` command-line tools.
First create a reader root for a fictional Relying Party _Utopia Brewery_:

```shell
$ multipazctl generateReaderRoot --subject_and_issuer 'CN=Utopia Brewery Reader CA,O=Utopia Brewery,C=ZZ'
- Generated self-signed reader root cert and private key with curve P384
- Wrote reader root private key to reader_root_private_key.pem
- Wrote reader root cert to reader_root_certificate.pem
```

then create a reader certificate for user Bob Bobson, an employee of
_Utopia Brewery_:

```shell
$ multipazctl generateReaderCert --subject_and_issuer 'CN=Bob Bobson,O=Utopia Brewery,C=ZZ'
- Generated Reader cert and private key with curve P256
- Loaded reader root cert from reader_root_certificate.pem
- Loaded reader root private key from reader_root_private_key.pem
- Wrote reader private key to reader_private_key.pem
- Wrote reader cert to reader_certificate.pem
```

Finally, create the PKCS#12 file like this:

```shell
$ openssl pkcs12 -export -out reader_key_and_certchain.p12 -inkey reader_private_key.pem -in reader_certificate.pem -certfile reader_root_certificate.pem
Enter Export Password:
Verifying - Enter Export Password:
```

twice entering a passphrase to protect the key. The resulting file
can be sent to Bob Bobson (sharing the passphrase out of band) and
they can install it into the Multipaz  Identity Reader.
