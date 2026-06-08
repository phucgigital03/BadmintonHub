package com.badmintonhub.user.service.impl;

import com.badmintonhub.user.service.EmailService;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public EmailServiceImpl(
            @Value("${sendgrid.api-key:}") String apiKey,
            @Value("${sendgrid.from-email}") String fromEmail,
            @Value("${sendgrid.from-name}") String fromName) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String link = "http://localhost:3000/api/auth/verify-email?token=" + token;

        if (!StringUtils.hasText(apiKey)) {
            log.info("[DEV] Email verify link for {}: {}", toEmail, link);
            return;
        }

        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            String subject = "Xác thực email - BadmintonHub";
            Content content = new Content(
                    "text/html",
                    "<p>Chào mừng bạn đến với BadmintonHub!</p>"
                            + "<p>Nhấn vào liên kết sau để xác thực email của bạn:</p>"
                            + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                            + "<p>Liên kết có hiệu lực trong 24 giờ.</p>");
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sendGrid = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            sendGrid.api(request);
        } catch (Exception e) {
            // Never fail registration because email delivery failed.
            log.warn("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }
}
