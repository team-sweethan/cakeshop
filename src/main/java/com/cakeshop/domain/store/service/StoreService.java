package com.cakeshop.domain.store.service;

import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
import com.cakeshop.domain.store.dto.form.StoreBasicInfoForm;
import com.cakeshop.domain.store.dto.form.StoreBusinessHoursForm;
import com.cakeshop.domain.store.dto.form.StorePickupInfoForm;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.mapper.StoreMapper;
import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.domain.store.entity.StoreHoliday;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import com.cakeshop.global.infra.FileStorageDirectory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoreService {

    // 현재 서비스는 단일 매장을 운영하므로 초기 SQL에서 보장한 대표 행을 사용한다.
    public static final long DEFAULT_STORE_ID = 1L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    // 매장 이미지가 환경별 prefix 아래에서 사용하는 도메인 하위 디렉터리
    private static final String IMAGE_DIRECTORY =
            FileStorageDirectory.STORE.getPath();

    private final StoreMapper storeMapper;
    private final FileStorageClient fileStorageClient;

    public StoreService(StoreMapper storeMapper, FileStorageClient fileStorageClient) {
        this.storeMapper = storeMapper;
        this.fileStorageClient = fileStorageClient;
    }

    @Transactional(readOnly = true)
    public StoreView getStoreView() {
        Store store = findDefaultStore();
        List<StoreBusinessHour> hours = storeMapper.findBusinessHours(DEFAULT_STORE_ID);
        List<StoreHoliday> holidays = storeMapper.findHolidays(DEFAULT_STORE_ID);
        Map<DayOfWeek, StoreBusinessHour> hourMap = toHourMap(hours);

        StoreBusinessHour weekday = representativeHour(hourMap,
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        StoreBusinessHour weekend = representativeHour(hourMap, List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        Set<DayOfWeek> closedDays = EnumSet.noneOf(DayOfWeek.class);
        hours.stream().filter(StoreBusinessHour::isClosed)
            .map(StoreBusinessHour::getDayOfWeek)
            .forEach(closedDays::add);

        return new StoreView(
            store.getId(), store.getName(), store.getDescription(), store.getImageUrl(),
            store.getAddress(), store.getPhone(),
            weekday.getOpenTime(), weekday.getCloseTime(), weekend.getOpenTime(), weekend.getCloseTime(),
            Set.copyOf(closedDays), store.getPickupPlace(), store.getPickupStartTime(), store.getPickupEndTime(),
            store.getPickupIntervalMinutes(), List.copyOf(holidays)
        );
    }

    @Transactional(readOnly = true)
    public StorePublicView getPublicStore() {
        StoreView store = getStoreView();
        String businessHours = "평일 %s / 주말 %s".formatted(
            formatHourRange(store.weekdayOpenTime(), store.weekdayCloseTime()),
            formatHourRange(store.weekendOpenTime(), store.weekendCloseTime())
        );
        String closedDays = store.closedDays().isEmpty()
            ? "정기 휴무 없음"
            : store.closedDays().stream()
                .sorted()
                .map(this::toKoreanDay)
                .reduce((left, right) -> left + "·" + right)
                .orElse("") + "요일 휴무";

        return new StorePublicView(
            store.name(), store.description(), store.imageUrl(), store.address(), store.phone(),
            businessHours, closedDays,
            store.pickupPlace(), "%s ~ %s".formatted(
                formatTime(store.pickupStartTime()), formatTime(store.pickupEndTime()))
        );
    }

    @Transactional
    public void updateBasicInfo(StoreBasicInfoForm form, MultipartFile image) {
        Store store = findDefaultStore();
        updateBasicInfo(
            store,
            form.getName(),
            form.getDescription(),
            form.getAddress(),
            form.getPhone(),
            image
        );
    }

    @Transactional
    public void deleteImage() {
        Store store = findDefaultStore();
        String imageUrl = store.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        if (storeMapper.clearImageUrl(DEFAULT_STORE_ID, imageUrl) != 1) {
            throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
        }

        // DB가 기존 URL을 제거한 뒤 커밋된 경우에만 저장 파일을 정리한다.
        registerCommitFileDeletion(imageUrl);
    }

    @Transactional
    public void updateBusinessHours(StoreBusinessHoursForm form) {
        findDefaultStore();
        updateBusinessHours(
            form.getWeekdayOpenTime(),
            form.getWeekdayCloseTime(),
            form.getWeekendOpenTime(),
            form.getWeekendCloseTime(),
            form.getClosedDays()
        );
    }

    @Transactional
    public void updatePickupInfo(StorePickupInfoForm form) {
        Store store = findDefaultStore();
        updatePickupInfo(
            store,
            form.getPickupPlace(),
            form.getPickupStartTime(),
            form.getPickupEndTime(),
            form.getPickupIntervalMinutes()
        );
    }

    private void updateBasicInfo(Store store,
                                 String name,
                                 String description,
                                 String address,
                                 String phone,
                                 MultipartFile image) {
        store.setName(name.trim());
        store.setDescription(trimToNull(description));
        store.setAddress(address.trim());
        store.setPhone(phone.trim());

        // 새 이미지가 올라온 경우에만 교체한다. 첨부가 없으면 기존 이미지를 그대로 유지한다.
        String previousImageUrl = store.getImageUrl();
        boolean imageReplaced = image != null && !image.isEmpty();
        if (imageReplaced) {
            validateImage(image);
            String newImageUrl = fileStorageClient.store(image, IMAGE_DIRECTORY);
            store.setImageUrl(newImageUrl);
            registerRollbackCleanup(newImageUrl);
        }

        if (storeMapper.updateBasicInfo(store) != 1) {
            throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
        }

        // DB 저장이 확정된 뒤에만 이전 파일을 지워, 실패 시 원본이 사라지는 것을 막는다.
        if (imageReplaced && previousImageUrl != null) {
            registerCommitFileDeletion(previousImageUrl);
        }
    }

    private void updatePickupInfo(Store store,
                                  String pickupPlace,
                                  LocalTime pickupStartTime,
                                  LocalTime pickupEndTime,
                                  Integer pickupIntervalMinutes) {
        store.setPickupPlace(pickupPlace.trim());
        store.setPickupStartTime(pickupStartTime);
        store.setPickupEndTime(pickupEndTime);
        store.setPickupIntervalMinutes(pickupIntervalMinutes);

        if (storeMapper.updatePickupInfo(store) != 1) {
            throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
        }
    }

    private void updateBusinessHours(LocalTime weekdayOpenTime,
                                     LocalTime weekdayCloseTime,
                                     LocalTime weekendOpenTime,
                                     LocalTime weekendCloseTime,
                                     Set<DayOfWeek> closedDays) {
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            boolean closed = closedDays.contains(day);
            StoreBusinessHour hour = new StoreBusinessHour();
            hour.setStoreId(DEFAULT_STORE_ID);
            hour.setDayOfWeek(day);
            hour.setClosed(closed);
            // DB 제약(chk_store_business_hour_time): 휴무일은 영업시간이 NULL 이어야 한다.
            hour.setOpenTime(closed ? null : (weekend ? weekendOpenTime : weekdayOpenTime));
            hour.setCloseTime(closed ? null : (weekend ? weekendCloseTime : weekdayCloseTime));
            storeMapper.upsertBusinessHour(hour);
        }
    }

    @Transactional
    public void addHoliday(StoreHolidayForm form) {
        findDefaultStore();
        if (storeMapper.existsHolidayDate(DEFAULT_STORE_ID, form.getHolidayDate())) {
            throw new BusinessException(StoreErrorCode.HOLIDAY_ALREADY_EXISTS);
        }

        StoreHoliday holiday = new StoreHoliday();
        holiday.setStoreId(DEFAULT_STORE_ID);
        holiday.setHolidayDate(form.getHolidayDate());
        holiday.setReason(form.getReason().trim());
        storeMapper.insertHoliday(holiday);
    }

    @Transactional
    public void deleteHoliday(long holidayId) {
        if (storeMapper.deleteHoliday(DEFAULT_STORE_ID, holidayId) != 1) {
            throw new BusinessException(StoreErrorCode.HOLIDAY_NOT_FOUND);
        }
    }

    private Store findDefaultStore() {
        return storeMapper.findStoreById(DEFAULT_STORE_ID)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
    }

    private Map<DayOfWeek, StoreBusinessHour> toHourMap(List<StoreBusinessHour> hours) {
        Map<DayOfWeek, StoreBusinessHour> result = new EnumMap<>(DayOfWeek.class);
        hours.forEach(hour -> result.put(hour.getDayOfWeek(), hour));
        return result;
    }

    // 평일/주말 대표 영업시간은 '휴무가 아닌' 첫 요일에서 가져온다.
    // 월요일 하나만 정기 휴무여도 평일 시간(화~금)이 통째로 사라지지 않게 하기 위함이다.
    private StoreBusinessHour representativeHour(Map<DayOfWeek, StoreBusinessHour> hours, List<DayOfWeek> candidates) {
        StoreBusinessHour fallback = null;
        for (DayOfWeek day : candidates) {
            StoreBusinessHour hour = hours.get(day);
            if (hour == null) {
                continue;
            }
            if (fallback == null) {
                fallback = hour; // 후보가 모두 휴무면 시간이 null인 행이라도 대표로 둔다(공개 화면에서 '휴무'로 표시).
            }
            if (!hour.isClosed()) {
                return hour;
            }
        }
        if (fallback == null) {
            // 초기 SQL이 빠졌거나 데이터가 손상된 경우 화면에 잘못된 기본값을 만들지 않는다.
            throw new BusinessException(StoreErrorCode.NOT_FOUND);
        }
        return fallback;
    }

    private String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    // 평일/주말이 모두 휴무라 대표 시간이 없을 수 있으므로 null을 '휴무'로 표시한다.
    private String formatHourRange(LocalTime open, LocalTime close) {
        if (open == null || close == null) {
            return "휴무";
        }
        return "%s ~ %s".formatted(formatTime(open), formatTime(close));
    }

    // 파일 확장자는 위조가 쉬우므로 브라우저가 보낸 content type 이 image/* 인지 확인한다.
    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(StoreErrorCode.INVALID_IMAGE);
        }
    }

    private void registerCommitFileDeletion(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteStoredFileQuietly(imageUrl);
                    }
                });
    }

    private void registerRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteStoredFileQuietly(imageUrl);
                        }
                    }
                });
    }

    private void deleteStoredFileQuietly(String imageUrl) {
        try {
            fileStorageClient.delete(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("매장 이미지 저장 파일을 정리하지 못했습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toKoreanDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }
}
