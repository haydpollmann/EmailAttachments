
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import email.EmailConstants;
import java.io.File;
import java.io.IOException;
import javax.mail.Flags;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GmailIncomingTest {
    
    private static final String MSG_SUCCESS = "Your file has been added to the Test File System";

    private static final Logger LOGGER = LoggerFactory.getLogger(GmailIncomingTest.class);
    Session session;

    public static void main(String[] args) {

        Properties properties = new Properties();
        // properties.put("mail.debug", "true");
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", "imap.gmail.com");
        properties.put("mail.imaps.port", "993");
        properties.put("mail.imaps.timeout", "10000");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "relay.jangosmtp.net");
        properties.put("mail.smtp.port", "25");
        GmailIncomingTest git = new GmailIncomingTest();
        git.session = Session.getInstance(properties);
        // getDefaultInstance
        IMAPStore store = null;
        Folder inbox = null;
        // check the inbox for any emails that may have been missed during restarts. internet problems etc.
        try {
            store = (IMAPStore) git.session.getStore("imaps");
            store.connect(EmailConstants.USERNAME, EmailConstants.PASSWORD);
            inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            Message[] arrayMessages = inbox.getMessages();
            for (Message message : arrayMessages) {
                if (!message.isSet(Flags.Flag.SEEN)) {
                    if (git.downloadAttachment(message)) {
                        git.reply(message, MSG_SUCCESS);
                    } else {
                        LOGGER.warn("could not add file to system");
                    }
                }
            }
        } catch (MessagingException ex) {
        }
        // enter idle loop to check for incoming emails

        try {
            store = (IMAPStore) git.session.getStore("imaps");
            store.connect(EmailConstants.USERNAME, EmailConstants.PASSWORD);

            if (!store.hasCapability("IDLE")) {
                throw new RuntimeException("IDLE not supported");
            }

            inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.addMessageCountListener(new MessageCountAdapter() {

                @Override
                public void messagesAdded(MessageCountEvent event) {
                    Message[] messages = event.getMessages();

                    for (Message message : messages) {
                        if (git.downloadAttachment(message)) {
                            git.reply(message, MSG_SUCCESS);
                        } else {
                            LOGGER.warn("could not add file to system");
                        }
                    }
                }
            });

            IdleThread idleThread = new IdleThread(inbox);
            idleThread.setDaemon(false);
            idleThread.start();

            idleThread.join();
            // idleThread.kill(); //to terminate from another thread

        } catch (InterruptedException | RuntimeException | MessagingException e) {
        } finally {

            close(inbox);
            close(store);
        }
    }

    private static class IdleThread extends Thread {

        private final Folder folder;
        private volatile boolean running = true;

        public IdleThread(Folder folder) {
            super();
            this.folder = folder;
        }

        public synchronized void kill() {

            if (!running) {
                return;
            }
            this.running = false;
        }

        @Override
        public void run() {
            while (running) {

                try {
                    ensureOpen(folder);
                    LOGGER.debug("Entering Idle...");
                    ((IMAPFolder) folder).idle();
                } catch (MessagingException e) {
                    LOGGER.error(e.toString());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        LOGGER.error(e.toString());
                    }
                }

            }
        }
    }

    public static void close(final Folder folder) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (final MessagingException e) {
            // ignore
        }

    }

    public static void close(final Store store) {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (final MessagingException e) {
            // ignore
        }

    }

    public static void ensureOpen(final Folder folder) throws MessagingException {

        if (folder != null) {
            Store store = folder.getStore();
            if (store != null && !store.isConnected()) {
                store.connect(EmailConstants.USERNAME, EmailConstants.PASSWORD);
            }
        } else {
            throw new MessagingException("Unable to open a null folder");
        }

        if (folder.exists() && !folder.isOpen() && (folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
            LOGGER.debug("Opening folder " + folder.getFullName());
            folder.open(Folder.READ_WRITE);
            if (!folder.isOpen()) {
                throw new MessagingException("Unable to open folder " + folder.getFullName());
            }
        }

    }

    private boolean downloadAttachment(Message message) {

        try {
            String contentType = message.getContentType();
            String messageContent = "";

            // store attachment file name, separated by comma
            String attachFiles = "";

            if (contentType.contains("multipart")) {
                // content may contain attachments
                Multipart multiPart = (Multipart) message.getContent();
                int numberOfParts = multiPart.getCount();
                for (int partCount = 0; partCount < numberOfParts; partCount++) {
                    MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        // this part is attachment
                        String fileName = part.getFileName();
                        attachFiles += fileName + ", ";
                        // create an email standard so I can name them properly
                        part.saveFile("C:\\Users\\Hayden\\Documents\\NetBeansProjects\\Gmail2" + File.separator + fileName);
                        //File file = new File(saveDirectory + File.separator + fileName);
                        //part.saveFile(file);
                    }
                }

                if (attachFiles.length() > 1) {
                    attachFiles = attachFiles.substring(0, attachFiles.length() - 2);
                }
            }

            String from = message.getFrom()[0].toString();
            String subject = message.getSubject();
            String sentDate = message.getSentDate().toString();
            //print out details of each message
            LOGGER.debug("From: " + from);
            LOGGER.debug("Subject: " + subject);
            LOGGER.debug("Sent Date: " + sentDate);
            LOGGER.debug("Message: " + messageContent);
            LOGGER.debug("Attachments: " + attachFiles);

            // Mark message as read
            message.setFlag(Flags.Flag.SEEN, true);

        } catch (MessagingException | IOException ex) {
            LOGGER.error(ex.toString());
            return false;
        }
        return true;
    }

    private void reply(Message message, String text) {
        try {
            Message replyMessage = new MimeMessage(session);
            String to = InternetAddress.toString(message
                    .getRecipients(Message.RecipientType.TO));
            replyMessage = (MimeMessage) message.reply(false);
            replyMessage.setFrom(new InternetAddress(to));
            replyMessage.setText(text);
            replyMessage.setReplyTo(message.getReplyTo());

            try ( // Send the message by authenticating the SMTP server
                    // Create a Transport instance and call the sendMessage
                    Transport t = session.getTransport("smtp")) {
                //connect to the smpt server using transport instance
                //change the user and password accordingly
                t.connect("smtp.gmail.com", EmailConstants.USERNAME, EmailConstants.PASSWORD);
                t.sendMessage(replyMessage,
                        replyMessage.getAllRecipients());
            }
        } catch (MessagingException ex) {
            LOGGER.error(ex.toString());
        }
    }
}
