/*
 * Copyright 2013 Samuel Franklyn <sfranklyn@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gamya.mailengine;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.CharacterFieldDescription;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.DataQueueEntry;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.IllegalObjectTypeException;
import com.ibm.as400.access.ObjectDoesNotExistException;
import com.ibm.as400.access.Record;
import com.ibm.as400.access.RecordFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailEngine {

    private static final Logger logger = Logger.getLogger(MailEngine.class.getName());
    private final Properties configProperties;

    public static void main(String[] args) {
        MailEngine mailEngine = new MailEngine();
        mailEngine.readQueue();
    }

    public MailEngine() {
        configProperties = new Properties();
        InputStream inputStream = this.getClass().getClassLoader().
                getResourceAsStream("config.properties");
        try {
            configProperties.load(inputStream);
            FileHandler fileHandler = new FileHandler("log.xml");
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void sendMail(String from, String to, String subject, String text) {
        final String username = configProperties.getProperty("mail.username");
        final String password = configProperties.getProperty("mail.password");

        Session session = Session.getInstance(configProperties,
                new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(text);

            Transport.send(message);
        } catch (MessagingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public void readQueue() {
        String systemName = configProperties.getProperty("as400.systemname");
        String userName = configProperties.getProperty("as400.username");
        String password = configProperties.getProperty("as400.password");
        String dataQueue = configProperties.getProperty("as400.dataqueue");

        AS400 as400 = new AS400(systemName, userName, password);
        DataQueue dq = new DataQueue(as400, dataQueue);

        CharacterFieldDescription fromFD
                = new CharacterFieldDescription(new AS400Text(100, as400),
                        "FROM");
        CharacterFieldDescription toFD
                = new CharacterFieldDescription(new AS400Text(100, as400),
                        "TO");
        CharacterFieldDescription subjectFD
                = new CharacterFieldDescription(new AS400Text(100, as400),
                        "SUBJECT");
        CharacterFieldDescription textFD
                = new CharacterFieldDescription(new AS400Text(700, as400),
                        "TEXT");

        RecordFormat recordFormat = new RecordFormat();
        recordFormat.addFieldDescription(fromFD);
        recordFormat.addFieldDescription(toFD);
        recordFormat.addFieldDescription(subjectFD);
        recordFormat.addFieldDescription(textFD);

        String from;
        String to;
        String subject;
        String text;
        Record record;
        try {
            logger.log(Level.INFO, "Read mail data queue");
            while (true) {
                DataQueueEntry DQData = dq.read(-1);
                record = recordFormat.getNewRecord(DQData.getData());
                from = record.getField("FROM").toString().trim();
                logger.log(Level.INFO, "From: {0}", from);
                to = record.getField("TO").toString().trim();
                logger.log(Level.INFO, "To: {0}", to);
                subject = record.getField("SUBJECT").toString().trim();
                logger.log(Level.INFO, "Subject: {0}", subject);
                text = record.getField("TEXT").toString().trim();
                logger.log(Level.INFO, "Text: {0}", text);
                sendMail(from, to, subject, text);
            }
        } catch (AS400SecurityException | ErrorCompletingRequestException |
                IOException | IllegalObjectTypeException |
                InterruptedException | ObjectDoesNotExistException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

}
