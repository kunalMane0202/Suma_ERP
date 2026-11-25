package com.suma.integration;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

/** Email-based ticket ingestion over IMAPS. baseUrl: imaps://host:993/INBOX. Processed messages are flagged SEEN. */
@Component
public class EmailConnector implements TicketConnector {
    @Override
    public String type() { return "EMAIL"; }

    @Override
    public List<Map<String, Object>> fetch(Integration i, Map<String, String> creds) throws Exception {
        URI uri = URI.create(i.getBaseUrl());
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "20000");
        Session session = Session.getInstance(props);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Store store = session.getStore("imaps")) {
            store.connect(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 993, creds.get("username"), creds.get("password"));
            String folderName = uri.getPath() == null || uri.getPath().length() <= 1 ? "INBOX" : uri.getPath().substring(1);
            try (Folder folder = store.getFolder(folderName)) {
                folder.open(Folder.READ_WRITE);
                Message[] msgs = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                for (int k = 0; k < Math.min(msgs.length, 50); k++) {
                    MimeMessage m = (MimeMessage) msgs[k];
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("messageId", m.getMessageID());
                    rec.put("subject", m.getSubject());
                    rec.put("body", text(m));
                    if (m.getFrom() != null && m.getFrom().length > 0 && m.getFrom()[0] instanceof InternetAddress a) {
                        rec.put("fromName", a.getPersonal());
                        rec.put("fromEmail", a.getAddress());
                    }
                    out.add(rec);
                    m.setFlag(Flags.Flag.SEEN, true);
                }
            }
        }
        return out;
    }

    private static String text(Part p) throws Exception {
        if (p.isMimeType("text/plain")) return String.valueOf(p.getContent());
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String t = text(mp.getBodyPart(i));
                if (t != null && !t.isBlank()) return t;
            }
        }
        if (p.isMimeType("text/html")) return String.valueOf(p.getContent()).replaceAll("<[^>]+>", " ");
        return "";
    }

    @Override
    public String test(Integration i, Map<String, String> creds) throws Exception {
        URI uri = URI.create(i.getBaseUrl());
        Session session = Session.getInstance(new Properties());
        try (Store store = session.getStore("imaps")) {
            store.connect(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 993, creds.get("username"), creds.get("password"));
            return "Connected to mailbox " + uri.getHost();
        }
    }

    @Override
    public Map<String, Object> defaultMapping() {
        return Map.of("externalId", "messageId", "title", "subject", "description", "body", "reporterName", "fromName", "reporterEmail", "fromEmail");
    }
}
