package com.cakeshop.global.infra.kakao;

import com.cakeshop.domain.notification.entity.NotificationDelivery;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 카카오 알림톡 및 SMS 중계 REST API 클라이언트 (Solapi 표준 연동 및 Mock 가상 발송 지원)
 */
@Component
public class SolapiKakaoAlimtalkClient {

    private static final Logger log = LoggerFactory.getLogger(SolapiKakaoAlimtalkClient.class);
    private static final String SOLAPI_API_URL = "https://api.solapi.com/messages/v4/send";

    private final NotificationMapper notificationMapper;
    private final RestTemplate restTemplate;

    @Value("${app.kakao.alimtalk.enabled:true}")
    private boolean enabled;

    @Value("${app.kakao.alimtalk.api-key:}")
    private String apiKey;

    @Value("${app.kakao.alimtalk.api-secret:}")
    private String apiSecret;

    @Value("${app.kakao.alimtalk.sender-phone:01000000000}")
    private String senderPhone;

    @Value("${app.kakao.alimtalk.pf-id:}")
    private String pfId;

    public SolapiKakaoAlimtalkClient(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 카카오 알림톡 / SMS 메시지 전송
     */
    public void sendAlimtalk(Long notificationId, String recipientPhone, String title, String content) {
        if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
            log.warn("알림톡 발송 실패: 수신자 전화번호가 없습니다. (NotificationId: {})", notificationId);
            return;
        }

        String normalizedPhone = recipientPhone.replace("-", "").trim();
        String normalizedSender = senderPhone.replace("-", "").trim();

        LocalDateTime now = LocalDateTime.now();

        // API Key / Secret 이 설정되어 있으면 실제 REST API 전송, 없으면 Mock 모드 작동
        if (enabled && isConfigured()) {
            sendRealAlimtalk(notificationId, normalizedPhone, normalizedSender, title, content, now);
        } else {
            sendMockAlimtalk(notificationId, normalizedPhone, title, content, now);
        }
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() 
            && apiSecret != null && !apiSecret.trim().isEmpty();
    }

    /**
     * 솔라피 (Solapi) REST API 호출 - 실제 스마트폰 전송
     */
    private void sendRealAlimtalk(Long notificationId, String recipient, String sender, String title, String content, LocalDateTime now) {
        try {
            log.info("▶ [Solapi REST API] 실제 알림 메시지 전송 시작 - To: {}, Title: {}", recipient, title);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", generateAuthorizationHeader());

            Map<String, Object> body = new HashMap<>();
            Map<String, Object> message = new HashMap<>();
            message.put("to", recipient);
            message.put("from", sender);
            message.put("text", "[" + title + "]\n" + content);

            // PF_ID (카카오 알림톡 채널 ID)가 세팅되어 있는 경우 알림톡 옵션 설정
            if (pfId != null && !pfId.trim().isEmpty()) {
                Map<String, Object> kakaoOptions = new HashMap<>();
                kakaoOptions.put("pfId", pfId);
                message.put("kakaoOptions", kakaoOptions);
                message.put("type", "ATA"); // ATA = 알림톡
            } else {
                message.put("type", "LMS"); // 카카오 채널 미설정 시 LMS 문자로 자동 전환
            }

            body.put("message", message);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(SOLAPI_API_URL, requestEntity, Map.class);

            String providerMsgId = "SOLAPI_" + UUID.randomUUID().toString().substring(0, 8);
            if (response.getBody() != null && response.getBody().containsKey("groupId")) {
                providerMsgId = String.valueOf(response.getBody().get("groupId"));
            }

            log.info("✔ [Solapi REST API] 성공적으로 전송되었습니다! ProviderMsgId: {}", providerMsgId);

            recordDelivery(notificationId, recipient, "KAKAO_ALIMTALK", providerMsgId, "SENT", null, null, now, now);

        } catch (Exception e) {
            log.error("✖ [Solapi REST API] 전송 실패: {}", e.getMessage(), e);
            recordDelivery(notificationId, recipient, "KAKAO_ALIMTALK", null, "FAILED", "API_ERROR", e.getMessage(), now, null);
        }
    }

    /**
     * 로컬/테스트 가상 발송 모드 (Mock Mode)
     */
    private void sendMockAlimtalk(Long notificationId, String recipient, String title, String content, LocalDateTime now) {
        String mockProviderMsgId = "MOCK_" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("================================================================================");
        log.info("📱 [카카오 알림톡 / SMS 가상 발송 모드 (Mock Mode)]");
        log.info(" 수신자 전화번호 : {}", recipient);
        log.info(" 알림 제목       : {}", title);
        log.info(" 알림 내용       : {}", content);
        log.info(" 메시지 ID       : {}", mockProviderMsgId);
        log.info(" (💡 application.yml에 Solapi api-key & api-secret을 등록하면 실제 스마트폰으로 전송됩니다)");
        log.info("================================================================================");

        recordDelivery(notificationId, recipient, "KAKAO_ALIMTALK", mockProviderMsgId, "SENT", null, null, now, now);
    }

    /**
     * DB notification_deliveries 테이블에 발송 이력 기록
     */
    private void recordDelivery(Long notificationId, String recipient, String channel, String providerMessageId,
                                String status, String failureCode, String failureReason,
                                LocalDateTime requestedAt, LocalDateTime sentAt) {
        NotificationDelivery delivery = NotificationDelivery.builder()
                .notificationId(notificationId)
                .recipient(recipient)
                .templateCode("DEFAULT_TEMPLATE")
                .providerMessageId(providerMessageId)
                .status(status)
                .failureReason(failureReason)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            notificationMapper.saveDelivery(delivery);
        } catch (Exception e) {
            log.error("notification_deliveries DB 저장 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Solapi HMAC-SHA256 Authorization 헤더 생성
     */
    private String generateAuthorizationHeader() {
        try {
            String date = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String salt = UUID.randomUUID().toString().replaceAll("-", "");
            String data = date + salt;

            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] rawHmac = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            String signature = sb.toString();

            return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
        } catch (Exception e) {
            log.error("Solapi HMAC 인증 헤더 생성 실패: {}", e.getMessage());
            return "";
        }
    }
}
