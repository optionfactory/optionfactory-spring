package net.optionfactory.spring.pem;

public class TestData {

    public static final String PRIVATE_KEY_EC = """
                        -----BEGIN EC PRIVATE KEY-----
                        MHcCAQEEIPfZ06HFTnqHcdigT22kdKjPmJpajO85ixy/OPRTsVUNoAoGCCqGSM49
                        AwEHoUQDQgAEM0pmHtcqUoVOTG1PbTVa9At0DtUU1enJPWV1fjPT4O8BPXi/dFob
                        fFvEAtGJ72DhWZtFRBxpWOLtNu39KovuDA==
                        -----END EC PRIVATE KEY-----
                        """;

    public static final String PRIVATE_KEY_PKCS1 = """
                        -----BEGIN RSA PRIVATE KEY-----
                        MIICXAIBAAKBgQDQh3nA2IsoFsVV55AyDF4xoAIca1pxM5TDu5H8+xmzzgyff75P
                        LZjA1dIl8R3A2CS/rIrdtm34wPkO1JcdAbP792flSaNTirnlqRtCqb6QiSoJgMq7
                        m+ieI+olzJZWo3+ZC4r0EPLuz3FLC0HP0vaVTw+Lrhb/dkaEKwdouOfxSwIDAQAB
                        AoGAC/0+CtBob82fWukNaVM8ty2z8V5avNdyXi6SnjDxsVzPbPwzPOoqNiwVAQlz
                        5HqI4lxRP54VhI/Twf6HEavfZSqaX1IiUhPW1QW2jFstv/9Cmt8jp5b60kED8exz
                        9MetwItpZ3RxVh/UP2hwbiu7JUxb2QyVq9bnHYQUpN6q7MECQQD3sqQ2mO7NvqOP
                        IxYx49niHyVRMwI1rYnV0++R/kNj2mVSI9wA52IpppyWbnvdZjgET7n/cftrFhL1
                        lTbH6WRHAkEA14S/8/K8XuPUAC5itArfE5IVhzNCMEIPsje17hnkzVVJ7f3/wBfg
                        RW0/QknpJU38COesJk6RDJ56O0cxLECg3QJBAKAO+tgg+OdQkkZTSOtSLiBVOfr/
                        eCURj6jx+7QeVpO2s5Rhga+/1QnPFQ6MNFQ70mTO1AUCNZHcQMIa+rwxz88CQHHz
                        K3a1Qem99gp+fAuLr/TuCVnpvTY8x4XINPYGVNZIIQWnhYCwXxD2OTs4TwA9YmPm
                        8jty0PhmFmSfOJ2YMjUCQBGHLcwqJujybxO6jykeDbTu23H5WDdYBsMAZI0hO9mV
                        w3qOlJUPynzB51B83QOqE0fjLNDY5oQ9Tp+LTcI78OE=
                        -----END RSA PRIVATE KEY-----
                       """;

    public static final String PRIVATE_KEY_PKCS8_ENCRYPTED = """
                -----BEGIN ENCRYPTED PRIVATE KEY-----
                MIIFLTBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQIGxLKwYccdR8CAggA
                MAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUDBAEqBBAOXNOL9oQPuJw5KQVWY8P0BIIE
                0OxMxV6BM8Oq2Wgkwl9BKNz/e3UsYcoftm7whiEW0EHNYZJyeyN0Cl5az5t4GLwN
                P/ChwT0wvcoS8f4Oo7nV140VtQGkDCUe3ZgUo1UtHsYu7Loczz016DmBhanY+LZl
                En/JqA6U+FVFN4534jaxQC+pRHmz7Ygu3Lm+8GW2hNlKgC8OqkgN+vt7PWkpvhD3
                UVkNVcaG0LbBUvJXAUXTxZV3EW1AiSReSWLp+r9/wHSP6UmmuTRdtKztsgKVs7kk
                ZHBA7aFKmQTllYPkLwx/YIrWRC2kumUm2FwCoN1525kw8Va7dJGapJJqRDSQBahd
                auXR/K5Cww2zZ1CmeVZOmXGG5IuPlv3Odl1Wz17bRM2RNRf1ipdXGy7T9ACm+LQG
                EiQ69h8U3jkwu5IP2mGUrUXzsTcMke246F6HDxwZqXSWo5lzMd7ucrg7ZoWvsfi+
                fOCk2OtBDy9eEd8omFSr4BInC6JgyFuqmqK60cf9LrqvlxhQjC+Rc7734Fpe7XEz
                hJ01nOzqrPqnivO47z4wGXVa+kTPa1W6mcGb8VL7CtYE/eUwE1iiisLJHEecWtx9
                2mqm3IdIXdhH9EoFRjz1UgGzzX9wvxlseGQCxTQ81DDbcGvKX6GLvBjF9M3bfud5
                53zn9vPizShG+QTVUCH7W1NLjoSDDgbNGBNGeZobsxUwNta7LPyqIDBXDOou9vRY
                j8QDbRFeORhEJKHhXgJJncPaRR/ukGtMXfNrbows/E4UGw6WiUy6ekHV+JXAFI7l
                l+WZ9YUNSsLpIdLDz9BYHQ/woM9ECnFLSDSo62hJexxA29hPuGG4z8p4asG2aSKh
                McCrcR338eMg2hUTcVbyKgm8vZ+9YC4vz5ELnDWc1RIxvTaptf5vOwooSEAY/hoa
                UjTjA2vGk3Y5k2Y3ojngPZWd3EtLtteubzlMlhynpoAjl3V3Yp0agOfDPPpBbHd6
                YW+H75P3I5geRWLungieyt88S42SrF3VJ9D/eP4ZpYG5NzrN6zaikTggBEmaOBZi
                fms9qiqNdC0zVM8RbhBIFezMGbbMD2Sqyizb8wflxhvfwF9V2cdtwEGzxZd+T9S4
                y+/zFjQo79nNi3isc0SRfpx5wfuNsA3pkErNdMxih+3hhv8r6eBP0PdP2Uva9g1V
                ViBh5Cl56LVn0aFOatMwrl1tDquTY6/RRC/0/MLEMbZOUJnbkAss+3lztJaOBYO6
                53YLhUieIvXoLWYMFUPdqv2RZMf4UjxVcZJ0iKfnRCWQuP9t4ZJuTTCF6i7uiDTW
                0cmy5PqflQPm6IoC+NLoBuT0bq0F3y+He+9pq49+O+Be0CQnt5u+08OkdLJodmYC
                1T6PznkmCi6P1DF4dSZwl0VDhPO26E/wMtN6VXXhOlRjfPbVJiQdtytfWAaE2Kbn
                35LBJjctDJEHP/r+eELs2KGUxTZ7bitgsGILMqHjZT27kEjDXOF4FJKBdI9G4l+p
                fPKV+0zAVRyWes5WO9yA4iJj8ulCap8aMWC1d6MIpOSxqpmyWpw1NrbeLBJtAHVJ
                kE/D5FQjsV/TlvFvSmCwsjbsgIH4R3dCZxr9v58lmszeIJHYmxEcfWV/KuGPPWnm
                8lyxGQE+lJWmuEVrGsVbKb599geX2T6+j4A9H+SDMKZ8
                -----END ENCRYPTED PRIVATE KEY-----
                       """;
    public static final String PRIVATE_KEY_PKCS8_CLEARTEXT = """
                        -----BEGIN PRIVATE KEY-----
                        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCTW3TNH8henHya
                        DfuM2VroyYfhOHZaEhTwVZLg0YLIYAbiq/CmyCNEYk5/HPbqbtBedmQ9sMInBUOB
                        0geD7tujWh3CGFQUAM8Kcp+Bh7PrrXQKEsAghIJKzjmCjygJ0U9MBfHw7DxM0NGX
                        LGe7Blp1hWd+EM5DoCsvZk5BX2YEOPW/W6Zs+uF3GE5dhi/SqH68eCXew2YG764O
                        Lm3yzZVRbvr0ZR41IhHf2kj6fTF5t/ZM/Nx0mn6wevqD8uFNV49wkUZWSYbLlqUB
                        wZujiuxhMNlR7LHRy0hWvsC3N8QYqInoa+lRg/JT02MDmAKdH3x1yQzCBw+C7Sno
                        wZb7MLUfAgMBAAECggEAJT8xS2YUuiF492HAa05Hhd7m+z1P4R1/8G3Y9f3PPoHT
                        S7llV2rwSylEKSozmXdQMQhg9xz2PnG8qhRiNO2L1AMD0ZtfqTy5GK13dwDLsC9E
                        TQWHTdDgtDLjY4Z2+uBO2GsKSDkWVt5TlBAz2hjVha4mRj3OPVa/g7gq4KhR+4/d
                        9xjyXNcBa41cvOV3lACLQ0bz85z9Iwr1vPxopMXLkU3zzczU6uafp3bD7a4YzfDJ
                        FgAulqpbDYzrKgEROAIfgA/+rZG33KY3w1IxSgcuKR+m8H8LMFNMTE95SFv18kGG
                        xuARCphEuc5tnz7AHFWEeiiSLoEjMnKX68p4rcunvQKBgQC5c1fBi46HRj/eB7Ws
                        RU9faEljKC2v2EmnP3alJtEB6bTkJ1sP/DeNsjziAc1clgWhisikQgUtB5QJCARI
                        bux/zyf8kDwsqoezJUHK3Hs/yRwhPVNXuJXKhTOiG4MGl2TpxOE/3zCkiya79Z6B
                        mzHhEOuaN4d9aRITItoRs8SrNQKBgQDLakhovFgIxYOxgfSDj3KtyuFHh6kGaQAo
                        FJMWWVP8KEIA7BN+lVZAbWbUKzpl/BDckegdqm+lnyqqkVrqycwsMao2r+yFgpAb
                        6RhF+c/eyIgQtqXb46dsOQKbYImQuWSOnaEORb5+hCxNVbiLzPs/dt4zDEJNDVVw
                        Uml61/LVgwKBgQCH5kiW4sgtxYMkGA5AEgKabffpnBXu2NcQouc1G37qofMXhueq
                        jS/AvK71CFEP2jiCKwvhoyfb2cPX4nIRCohxGMO6x4/xQ35x/4l5OZ1wHtZoXWJn
                        1DKg2H//+Z1JBUTEMqzGe65PxlE6SEJBxBMHVbjxqGO3uXmvYEjh1BT5SQKBgGq9
                        jN2Y58FClAaToRFgNtdHvMtiPqnkc3aUxVJW8aFCJtCBEQG9r5MDVZBEVtKpYNe9
                        oMXgZ9HLIgJ7X/AQkJkoPp+P1VeB2ckrmdcubYwEQpIypforDfHWQK30DHvrLP9B
                        bAAnTPzqsqyqLLr/h2AYKiUza58vPgRA2qThqMWtAoGARYADCq5uIl7rZP7npAaq
                        MrHPZBMib3sQ0496eJBEVaYAc2tsNBiuDEsKy/c3EQaWYIDFGJcv/dlnj7OLwnu4
                        NpZGyaHkUphy+EbAIUksSBNhAouUwaOhMbirGx8fOT0aVUfpIC6dZcRQNCSbk6Y6
                        xpBye1BeJLIR/Z+ZMTgJhkM=
                        -----END PRIVATE KEY-----                      

                       """;

    public static final String CERTIFICATE_X509 = """                
                        -----BEGIN CERTIFICATE-----
                        MIIDBTCCAe2gAwIBAgIUDKkx5o9IMIYX1hp7353LpDuInEUwDQYJKoZIhvcNAQEL
                        BQAwEjEQMA4GA1UEAwwHYXNkLmNvbTAeFw0yNDA1MjcxNTA5NThaFw0yNTA1Mjcx
                        NTA5NThaMBIxEDAOBgNVBAMMB2FzZC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IB
                        DwAwggEKAoIBAQCTW3TNH8henHyaDfuM2VroyYfhOHZaEhTwVZLg0YLIYAbiq/Cm
                        yCNEYk5/HPbqbtBedmQ9sMInBUOB0geD7tujWh3CGFQUAM8Kcp+Bh7PrrXQKEsAg
                        hIJKzjmCjygJ0U9MBfHw7DxM0NGXLGe7Blp1hWd+EM5DoCsvZk5BX2YEOPW/W6Zs
                        +uF3GE5dhi/SqH68eCXew2YG764OLm3yzZVRbvr0ZR41IhHf2kj6fTF5t/ZM/Nx0
                        mn6wevqD8uFNV49wkUZWSYbLlqUBwZujiuxhMNlR7LHRy0hWvsC3N8QYqInoa+lR
                        g/JT02MDmAKdH3x1yQzCBw+C7SnowZb7MLUfAgMBAAGjUzBRMB0GA1UdDgQWBBR2
                        Bq0c8hRjtNiWPCYT8AdnHCKlQDAfBgNVHSMEGDAWgBR2Bq0c8hRjtNiWPCYT8Adn
                        HCKlQDAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQA6q2QPIVhx
                        oSyMx5uU9o5aFxsw7bUlp2iNQlOR0dzYGoancQpoQM5VWfnn/c1Yj1h+R+xd4idC
                        N3+A8z5EpeuIz6kE76u92B+4X30UvbQiUN0zIuYWeO9bJiiBoA0wjwl4mx8gGzNm
                        apxDg+o4UmAcP/KgtgKG7gunLUjTzYQDghXoJ12RVit7ayGxxQVshqyWkmz2/6a9
                        D3Ad8euIjARYPZbG8R/i7K6VJxnmpDLLVoZXPuF4wezCw9fYYS5UeDro8631rAVh
                        x5cPe9Kn47YzUcFL1v1m5sB32+ivuzY14V+g87QLwdCvdMmQaaDUrIQe3ALsojpV
                        thl3ttAh7avu
                        -----END CERTIFICATE-----
                       """;

    public static final String CERTIFICATE_X509_CHAIN = """
                        -----BEGIN CERTIFICATE-----
                        MIIHbjCCBlagAwIBAgIQB1vO8waJyK3fE+Ua9K/hhzANBgkqhkiG9w0BAQsFADBZ
                        MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMTMwMQYDVQQDEypE
                        aWdpQ2VydCBHbG9iYWwgRzIgVExTIFJTQSBTSEEyNTYgMjAyMCBDQTEwHhcNMjQw
                        MTMwMDAwMDAwWhcNMjUwMzAxMjM1OTU5WjCBljELMAkGA1UEBhMCVVMxEzARBgNV
                        BAgTCkNhbGlmb3JuaWExFDASBgNVBAcTC0xvcyBBbmdlbGVzMUIwQAYDVQQKDDlJ
                        bnRlcm5ldMKgQ29ycG9yYXRpb27CoGZvcsKgQXNzaWduZWTCoE5hbWVzwqBhbmTC
                        oE51bWJlcnMxGDAWBgNVBAMTD3d3dy5leGFtcGxlLm9yZzCCASIwDQYJKoZIhvcN
                        AQEBBQADggEPADCCAQoCggEBAIaFD7sO+cpf2fXgCjIsM9mqDgcpqC8IrXi9wga/
                        9y0rpqcnPVOmTMNLsid3INbBVEm4CNr5cKlh9rJJnWlX2vttJDRyLkfwBD+dsVvi
                        vGYxWTLmqX6/1LDUZPVrynv/cltemtg/1Aay88jcj2ZaRoRmqBgVeacIzgU8+zmJ
                        7236TnFSe7fkoKSclsBhPaQKcE3Djs1uszJs8sdECQTdoFX9I6UgeLKFXtg7rRf/
                        hcW5dI0zubhXbrW8aWXbCzySVZn0c7RkJMpnTCiZzNxnPXnHFpwr5quqqjVyN/aB
                        KkjoP04Zmr+eRqoyk/+lslq0sS8eaYSSHbC5ja/yMWyVhvMCAwEAAaOCA/IwggPu
                        MB8GA1UdIwQYMBaAFHSFgMBmx9833s+9KTeqAx2+7c0XMB0GA1UdDgQWBBRM/tAS
                        TS4hz2v68vK4TEkCHTGRijCBgQYDVR0RBHoweIIPd3d3LmV4YW1wbGUub3Jnggtl
                        eGFtcGxlLm5ldIILZXhhbXBsZS5lZHWCC2V4YW1wbGUuY29tggtleGFtcGxlLm9y
                        Z4IPd3d3LmV4YW1wbGUuY29tgg93d3cuZXhhbXBsZS5lZHWCD3d3dy5leGFtcGxl
                        Lm5ldDA+BgNVHSAENzA1MDMGBmeBDAECAjApMCcGCCsGAQUFBwIBFhtodHRwOi8v
                        d3d3LmRpZ2ljZXJ0LmNvbS9DUFMwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQG
                        CCsGAQUFBwMBBggrBgEFBQcDAjCBnwYDVR0fBIGXMIGUMEigRqBEhkJodHRwOi8v
                        Y3JsMy5kaWdpY2VydC5jb20vRGlnaUNlcnRHbG9iYWxHMlRMU1JTQVNIQTI1NjIw
                        MjBDQTEtMS5jcmwwSKBGoESGQmh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdp
                        Q2VydEdsb2JhbEcyVExTUlNBU0hBMjU2MjAyMENBMS0xLmNybDCBhwYIKwYBBQUH
                        AQEEezB5MCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2VydC5jb20wUQYI
                        KwYBBQUHMAKGRWh0dHA6Ly9jYWNlcnRzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEds
                        b2JhbEcyVExTUlNBU0hBMjU2MjAyMENBMS0xLmNydDAMBgNVHRMBAf8EAjAAMIIB
                        fQYKKwYBBAHWeQIEAgSCAW0EggFpAWcAdABOdaMnXJoQwzhbbNTfP1LrHfDgjhuN
                        acCx+mSxYpo53wAAAY1b0vxkAAAEAwBFMEMCH0BRCgxPbBBVxhcWZ26a8JCe83P1
                        JZ6wmv56GsVcyMACIDgpMbEo5HJITTRPnoyT4mG8cLrWjEvhchUdEcWUuk1TAHYA
                        fVkeEuF4KnscYWd8Xv340IdcFKBOlZ65Ay/ZDowuebgAAAGNW9L8MAAABAMARzBF
                        AiBdv5Z3pZFbfgoM3tGpCTM3ZxBMQsxBRSdTS6d8d2NAcwIhALLoCT9mTMN9OyFz
                        IBV5MkXVLyuTf2OAzAOa7d8x2H6XAHcA5tIxY0B3jMEQQQbXcbnOwdJA9paEhvu6
                        hzId/R43jlAAAAGNW9L8XwAABAMASDBGAiEA4Koh/VizdQU1tjZ2E2VGgWSXXkwn
                        QmiYhmAeKcVLHeACIQD7JIGFsdGol7kss2pe4lYrCgPVc+iGZkuqnj26hqhr0TAN
                        BgkqhkiG9w0BAQsFAAOCAQEABOFuAj4N4yNG9OOWNQWTNSICC4Rd4nOG1HRP/Bsn
                        rz7KrcPORtb6D+Jx+Q0amhO31QhIvVBYs14gY4Ypyj7MzHgm4VmPXcqLvEkxb2G9
                        Qv9hYuEiNSQmm1fr5QAN/0AzbEbCM3cImLJ69kP5bUjfv/76KB57is8tYf9sh5ik
                        LGKauxCM/zRIcGa3bXLDafk5S2g5Vr2hs230d/NGW1wZrE+zdGuMxfGJzJP+DAFv
                        iBfcQnFg4+1zMEKcqS87oniOyG+60RMM0MdejBD7AS43m9us96Gsun/4kufLQUTI
                        FfnzxLutUV++3seshgefQOy5C/ayi8y1VTNmujPCxPCi6Q==
                        -----END CERTIFICATE-----
                        -----BEGIN CERTIFICATE-----
                        MIIEyDCCA7CgAwIBAgIQDPW9BitWAvR6uFAsI8zwZjANBgkqhkiG9w0BAQsFADBh
                        MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3
                        d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBH
                        MjAeFw0yMTAzMzAwMDAwMDBaFw0zMTAzMjkyMzU5NTlaMFkxCzAJBgNVBAYTAlVT
                        MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxMzAxBgNVBAMTKkRpZ2lDZXJ0IEdsb2Jh
                        bCBHMiBUTFMgUlNBIFNIQTI1NiAyMDIwIENBMTCCASIwDQYJKoZIhvcNAQEBBQAD
                        ggEPADCCAQoCggEBAMz3EGJPprtjb+2QUlbFbSd7ehJWivH0+dbn4Y+9lavyYEEV
                        cNsSAPonCrVXOFt9slGTcZUOakGUWzUb+nv6u8W+JDD+Vu/E832X4xT1FE3LpxDy
                        FuqrIvAxIhFhaZAmunjZlx/jfWardUSVc8is/+9dCopZQ+GssjoP80j812s3wWPc
                        3kbW20X+fSP9kOhRBx5Ro1/tSUZUfyyIxfQTnJcVPAPooTncaQwywa8WV0yUR0J8
                        osicfebUTVSvQpmowQTCd5zWSOTOEeAqgJnwQ3DPP3Zr0UxJqyRewg2C/Uaoq2yT
                        zGJSQnWS+Jr6Xl6ysGHlHx+5fwmY6D36g39HaaECAwEAAaOCAYIwggF+MBIGA1Ud
                        EwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFHSFgMBmx9833s+9KTeqAx2+7c0XMB8G
                        A1UdIwQYMBaAFE4iVCAYlebjbuYP+vq5Eu0GF485MA4GA1UdDwEB/wQEAwIBhjAd
                        BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwdgYIKwYBBQUHAQEEajBoMCQG
                        CCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2VydC5jb20wQAYIKwYBBQUHMAKG
                        NGh0dHA6Ly9jYWNlcnRzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEdsb2JhbFJvb3RH
                        Mi5jcnQwQgYDVR0fBDswOTA3oDWgM4YxaHR0cDovL2NybDMuZGlnaWNlcnQuY29t
                        L0RpZ2lDZXJ0R2xvYmFsUm9vdEcyLmNybDA9BgNVHSAENjA0MAsGCWCGSAGG/WwC
                        ATAHBgVngQwBATAIBgZngQwBAgEwCAYGZ4EMAQICMAgGBmeBDAECAzANBgkqhkiG
                        9w0BAQsFAAOCAQEAkPFwyyiXaZd8dP3A+iZ7U6utzWX9upwGnIrXWkOH7U1MVl+t
                        wcW1BSAuWdH/SvWgKtiwla3JLko716f2b4gp/DA/JIS7w7d7kwcsr4drdjPtAFVS
                        slme5LnQ89/nD/7d+MS5EHKBCQRfz5eeLjJ1js+aWNJXMX43AYGyZm0pGrFmCW3R
                        bpD0ufovARTFXFZkAdl9h6g4U5+LXUZtXMYnhIHUfoyMo5tS58aI7Dd8KvvwVVo4
                        chDYABPPTHPbqjc1qCmBaZx2vN4Ye5DUys/vZwP9BFohFrH/6j/f3IL16/RZkiMN
                        JCqVJUzKoZHm1Lesh3Sz8W2jmdv51b2EQJ8HmA==
                        -----END CERTIFICATE-----
                       """;

    public static final String PRIVATE_KEY_PKCS8_CLEARTEXT_AND_CERTIFICATE_CHAIN = """
                        -----BEGIN PRIVATE KEY-----
                        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCTW3TNH8henHya
                        DfuM2VroyYfhOHZaEhTwVZLg0YLIYAbiq/CmyCNEYk5/HPbqbtBedmQ9sMInBUOB
                        0geD7tujWh3CGFQUAM8Kcp+Bh7PrrXQKEsAghIJKzjmCjygJ0U9MBfHw7DxM0NGX
                        LGe7Blp1hWd+EM5DoCsvZk5BX2YEOPW/W6Zs+uF3GE5dhi/SqH68eCXew2YG764O
                        Lm3yzZVRbvr0ZR41IhHf2kj6fTF5t/ZM/Nx0mn6wevqD8uFNV49wkUZWSYbLlqUB
                        wZujiuxhMNlR7LHRy0hWvsC3N8QYqInoa+lRg/JT02MDmAKdH3x1yQzCBw+C7Sno
                        wZb7MLUfAgMBAAECggEAJT8xS2YUuiF492HAa05Hhd7m+z1P4R1/8G3Y9f3PPoHT
                        S7llV2rwSylEKSozmXdQMQhg9xz2PnG8qhRiNO2L1AMD0ZtfqTy5GK13dwDLsC9E
                        TQWHTdDgtDLjY4Z2+uBO2GsKSDkWVt5TlBAz2hjVha4mRj3OPVa/g7gq4KhR+4/d
                        9xjyXNcBa41cvOV3lACLQ0bz85z9Iwr1vPxopMXLkU3zzczU6uafp3bD7a4YzfDJ
                        FgAulqpbDYzrKgEROAIfgA/+rZG33KY3w1IxSgcuKR+m8H8LMFNMTE95SFv18kGG
                        xuARCphEuc5tnz7AHFWEeiiSLoEjMnKX68p4rcunvQKBgQC5c1fBi46HRj/eB7Ws
                        RU9faEljKC2v2EmnP3alJtEB6bTkJ1sP/DeNsjziAc1clgWhisikQgUtB5QJCARI
                        bux/zyf8kDwsqoezJUHK3Hs/yRwhPVNXuJXKhTOiG4MGl2TpxOE/3zCkiya79Z6B
                        mzHhEOuaN4d9aRITItoRs8SrNQKBgQDLakhovFgIxYOxgfSDj3KtyuFHh6kGaQAo
                        FJMWWVP8KEIA7BN+lVZAbWbUKzpl/BDckegdqm+lnyqqkVrqycwsMao2r+yFgpAb
                        6RhF+c/eyIgQtqXb46dsOQKbYImQuWSOnaEORb5+hCxNVbiLzPs/dt4zDEJNDVVw
                        Uml61/LVgwKBgQCH5kiW4sgtxYMkGA5AEgKabffpnBXu2NcQouc1G37qofMXhueq
                        jS/AvK71CFEP2jiCKwvhoyfb2cPX4nIRCohxGMO6x4/xQ35x/4l5OZ1wHtZoXWJn
                        1DKg2H//+Z1JBUTEMqzGe65PxlE6SEJBxBMHVbjxqGO3uXmvYEjh1BT5SQKBgGq9
                        jN2Y58FClAaToRFgNtdHvMtiPqnkc3aUxVJW8aFCJtCBEQG9r5MDVZBEVtKpYNe9
                        oMXgZ9HLIgJ7X/AQkJkoPp+P1VeB2ckrmdcubYwEQpIypforDfHWQK30DHvrLP9B
                        bAAnTPzqsqyqLLr/h2AYKiUza58vPgRA2qThqMWtAoGARYADCq5uIl7rZP7npAaq
                        MrHPZBMib3sQ0496eJBEVaYAc2tsNBiuDEsKy/c3EQaWYIDFGJcv/dlnj7OLwnu4
                        NpZGyaHkUphy+EbAIUksSBNhAouUwaOhMbirGx8fOT0aVUfpIC6dZcRQNCSbk6Y6
                        xpBye1BeJLIR/Z+ZMTgJhkM=
                        -----END PRIVATE KEY-----                      
                        -----BEGIN CERTIFICATE-----
                        MIIDBTCCAe2gAwIBAgIUDKkx5o9IMIYX1hp7353LpDuInEUwDQYJKoZIhvcNAQEL
                        BQAwEjEQMA4GA1UEAwwHYXNkLmNvbTAeFw0yNDA1MjcxNTA5NThaFw0yNTA1Mjcx
                        NTA5NThaMBIxEDAOBgNVBAMMB2FzZC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IB
                        DwAwggEKAoIBAQCTW3TNH8henHyaDfuM2VroyYfhOHZaEhTwVZLg0YLIYAbiq/Cm
                        yCNEYk5/HPbqbtBedmQ9sMInBUOB0geD7tujWh3CGFQUAM8Kcp+Bh7PrrXQKEsAg
                        hIJKzjmCjygJ0U9MBfHw7DxM0NGXLGe7Blp1hWd+EM5DoCsvZk5BX2YEOPW/W6Zs
                        +uF3GE5dhi/SqH68eCXew2YG764OLm3yzZVRbvr0ZR41IhHf2kj6fTF5t/ZM/Nx0
                        mn6wevqD8uFNV49wkUZWSYbLlqUBwZujiuxhMNlR7LHRy0hWvsC3N8QYqInoa+lR
                        g/JT02MDmAKdH3x1yQzCBw+C7SnowZb7MLUfAgMBAAGjUzBRMB0GA1UdDgQWBBR2
                        Bq0c8hRjtNiWPCYT8AdnHCKlQDAfBgNVHSMEGDAWgBR2Bq0c8hRjtNiWPCYT8Adn
                        HCKlQDAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQA6q2QPIVhx
                        oSyMx5uU9o5aFxsw7bUlp2iNQlOR0dzYGoancQpoQM5VWfnn/c1Yj1h+R+xd4idC
                        N3+A8z5EpeuIz6kE76u92B+4X30UvbQiUN0zIuYWeO9bJiiBoA0wjwl4mx8gGzNm
                        apxDg+o4UmAcP/KgtgKG7gunLUjTzYQDghXoJ12RVit7ayGxxQVshqyWkmz2/6a9
                        D3Ad8euIjARYPZbG8R/i7K6VJxnmpDLLVoZXPuF4wezCw9fYYS5UeDro8631rAVh
                        x5cPe9Kn47YzUcFL1v1m5sB32+ivuzY14V+g87QLwdCvdMmQaaDUrIQe3ALsojpV
                        thl3ttAh7avu
                        -----END CERTIFICATE-----
                       """;
}
