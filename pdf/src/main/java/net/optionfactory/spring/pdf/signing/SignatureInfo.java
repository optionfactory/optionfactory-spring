package net.optionfactory.spring.pdf.signing;

import java.time.ZonedDateTime;

public record SignatureInfo(String name, String reason, String location, ZonedDateTime at, CommitmentType commitmentType) {

    public enum CommitmentType {
        PROOF_OF_ORIGIN("1.2.840.113549.1.9.16.6.1"),
        PROOF_OF_RECEIPT("1.2.840.113549.1.9.16.6.2"),
        PROOF_OF_DELIVERY("1.2.840.113549.1.9.16.6.3"),
        PROOF_OF_SENDER("1.2.840.113549.1.9.16.6.4"),
        PROOF_OF_APPROVAL("1.2.840.113549.1.9.16.6.5"),
        PROOF_OF_CREATION("1.2.840.113549.1.9.16.6.6");

        public final String oid;

        CommitmentType(String oid) {
            this.oid = oid;
        }
    }

}
