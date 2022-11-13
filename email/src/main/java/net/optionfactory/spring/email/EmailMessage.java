package net.optionfactory.spring.email;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class EmailMessage {

    @NonNull
    public String messageId;
    @NonNull
    public String recipient;
    @NonNull
    public String subject;
    @Nullable
    public String textBody;
    @Nullable
    public String htmlBody;

}
