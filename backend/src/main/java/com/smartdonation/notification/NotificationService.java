package com.smartdonation.notification;

import com.smartdonation.dto.DonorMatchResponse;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.DonorNotification;
import com.smartdonation.entity.NotificationStatus;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.repository.DonorNotificationRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final DonorNotificationRepository notificationRepository;
    private final UserAccountRepository userRepository;
    private final JavaMailSender mailSender;

    public NotificationService(DonorNotificationRepository notificationRepository, UserAccountRepository userRepository, JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    public void notifyDonors(BloodRequest request, List<DonorMatchResponse> matches) {
        for (DonorMatchResponse match : matches) {
            UserAccount donor = userRepository.findById(match.donorId()).orElseThrow();
            DonorNotification notification = new DonorNotification();
            notification.setBloodRequest(request);
            notification.setDonor(donor);
            notification.setChannel("EMAIL");
            notification.setMessage("Emergency blood request for " + request.getRequiredBloodGroup() + " near " + request.getHospitalName());
            notification.setSentAt(LocalDateTime.now());
            notification.setStatus(sendEmail(donor, notification.getMessage()) ? NotificationStatus.SENT : NotificationStatus.PENDING);
            notificationRepository.save(notification);
        }
    }

    private boolean sendEmail(UserAccount donor, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(donor.getEmail());
            message.setSubject("Emergency blood donation request");
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
