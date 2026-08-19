package com.cakeshop.domain.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.store.dto.form.StoreBasicInfoForm;
import com.cakeshop.domain.store.dto.form.StoreBusinessHoursForm;
import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
import com.cakeshop.domain.store.dto.form.StorePickupInfoForm;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.mapper.StoreMapper;
import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class StoreServiceTests {

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private FileStorageClient fileStorageClient;

    private StoreService storeService;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(storeMapper, fileStorageClient);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateBasicInfo_validForm_updatesOnlyBasicInformation() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        when(storeMapper.updateBasicInfo(any(Store.class))).thenReturn(1);
        StoreBasicInfoForm form = validBasicInfoForm();

        storeService.updateBasicInfo(form, null);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).updateBasicInfo(captor.capture());
        assertThat(captor.getValue()).satisfies(updated -> {
            assertThat(updated.getName()).isEqualTo("스위트온 케이크");
            assertThat(updated.getDescription()).isEqualTo("예약 케이크 전문점");
            assertThat(updated.getAddress()).isEqualTo("서울시 OO구");
            assertThat(updated.getPhone()).isEqualTo("02-0000-0000");
        });
        verify(storeMapper, never()).updatePickupInfo(any(Store.class));
        verify(storeMapper, never()).upsertBusinessHour(any(StoreBusinessHour.class));
    }

    @Test
    void updateBasicInfo_updateMiss_throwsUpdateFailed() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));

        assertThatThrownBy(() -> storeService.updateBasicInfo(validBasicInfoForm(), null))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.UPDATE_FAILED));
    }

    @Test
    void deleteImage_existingImage_deletesStoredFileAfterCommit() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202608/photo.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.clearImageUrl(
                StoreService.DEFAULT_STORE_ID,
                "/uploads/store/202608/photo.jpg"))
            .thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        storeService.deleteImage();

        verify(storeMapper).clearImageUrl(
            StoreService.DEFAULT_STORE_ID,
            "/uploads/store/202608/photo.jpg");
        verify(fileStorageClient, never()).delete("/uploads/store/202608/photo.jpg");

        TransactionSynchronizationManager.getSynchronizations().forEach(
            TransactionSynchronization::afterCommit);

        verify(fileStorageClient).delete("/uploads/store/202608/photo.jpg");
    }

    @Test
    void deleteImage_withoutImage_doesNotCallDeleteCollaborators() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));

        storeService.deleteImage();

        verify(storeMapper, never()).clearImageUrl(any(), any());
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_imageChangedConcurrently_throwsUpdateFailedWithoutDeletingFile() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202608/old.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> storeService.deleteImage())
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.UPDATE_FAILED));
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_transactionRollsBack_keepsStoredFile() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202608/photo.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.clearImageUrl(
                StoreService.DEFAULT_STORE_ID,
                "/uploads/store/202608/photo.jpg"))
            .thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        storeService.deleteImage();
        TransactionSynchronizationManager.getSynchronizations().forEach(
            synchronization -> synchronization.afterCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorageClient, never()).delete("/uploads/store/202608/photo.jpg");
    }

    @Test
    void updateBusinessHours_validForm_updatesAllSevenDaysOnly() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        StoreBusinessHoursForm form = validBusinessHoursForm();
        form.setClosedDays(EnumSet.of(DayOfWeek.SUNDAY));

        storeService.updateBusinessHours(form);

        ArgumentCaptor<StoreBusinessHour> captor = ArgumentCaptor.forClass(StoreBusinessHour.class);
        verify(storeMapper, org.mockito.Mockito.times(7)).upsertBusinessHour(captor.capture());
        assertThat(captor.getAllValues()).hasSize(7);
        // 휴무일은 DB 제약에 맞춰 영업시간이 NULL 이어야 한다.
        assertThat(captor.getAllValues())
            .filteredOn(hour -> hour.getDayOfWeek() == DayOfWeek.SUNDAY)
            .singleElement()
            .satisfies(hour -> {
                assertThat(hour.isClosed()).isTrue();
                assertThat(hour.getOpenTime()).isNull();
                assertThat(hour.getCloseTime()).isNull();
            });
        // 영업일은 요일 구간에 맞는 시간이 유지되어야 한다(토요일=주말 시간).
        assertThat(captor.getAllValues())
            .filteredOn(hour -> hour.getDayOfWeek() == DayOfWeek.SATURDAY)
            .singleElement()
            .satisfies(hour -> {
                assertThat(hour.isClosed()).isFalse();
                assertThat(hour.getOpenTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(hour.getCloseTime()).isEqualTo(LocalTime.of(18, 0));
            });
        verify(storeMapper, never()).updateBasicInfo(any(Store.class));
        verify(storeMapper, never()).updatePickupInfo(any(Store.class));
    }

    @Test
    void updatePickupInfo_validForm_updatesOnlyPickupInformation() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        when(storeMapper.updatePickupInfo(any(Store.class))).thenReturn(1);

        storeService.updatePickupInfo(validPickupInfoForm());

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).updatePickupInfo(captor.capture());
        assertThat(captor.getValue()).satisfies(updated -> {
            assertThat(updated.getPickupPlace()).isEqualTo("1층 카운터");
            assertThat(updated.getPickupStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(updated.getPickupEndTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(updated.getPickupIntervalMinutes()).isEqualTo(60);
        });
        verify(storeMapper, never()).updateBasicInfo(any(Store.class));
        verify(storeMapper, never()).upsertBusinessHour(any(StoreBusinessHour.class));
    }

    @Test
    void updatePickupInfo_updateMiss_throwsUpdateFailed() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));

        assertThatThrownBy(() -> storeService.updatePickupInfo(validPickupInfoForm()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.UPDATE_FAILED));
    }

    @Test
    void updateBasicInfo_newImage_deletesPreviousImageAfterCommit() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202601/old.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.updateBasicInfo(any(Store.class))).thenReturn(1);
        when(fileStorageClient.store(any(), org.mockito.ArgumentMatchers.eq("store")))
            .thenReturn("/uploads/store/202607/new.jpg");
        MultipartFile image = new MockMultipartFile(
            "image", "cake.jpg", "image/jpeg", new byte[] {1, 2, 3});

        TransactionSynchronizationManager.initSynchronization();

        storeService.updateBasicInfo(validBasicInfoForm(), image);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).updateBasicInfo(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo("/uploads/store/202607/new.jpg");
        verify(fileStorageClient, never()).delete("/uploads/store/202601/old.jpg");

        TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit);

        verify(fileStorageClient).delete("/uploads/store/202601/old.jpg");
    }

    @Test
    void updateBasicInfo_transactionRollsBack_keepsPreviousImage() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202601/old.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.updateBasicInfo(any(Store.class))).thenReturn(1);
        when(fileStorageClient.store(any(), org.mockito.ArgumentMatchers.eq("store")))
            .thenReturn("/uploads/store/202607/new.jpg");
        MultipartFile image = new MockMultipartFile(
            "image", "cake.jpg", "image/jpeg", new byte[] {1, 2, 3});
        TransactionSynchronizationManager.initSynchronization();

        storeService.updateBasicInfo(validBasicInfoForm(), image);

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorageClient, never()).delete("/uploads/store/202601/old.jpg");
        verify(fileStorageClient).delete("/uploads/store/202607/new.jpg");
    }

    @Test
    void updateBasicInfo_previousImageCleanupFails_keepsSuccessfulResult() {
        Store existing = store();
        existing.setImageUrl("/uploads/store/202601/old.jpg");
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(existing));
        when(storeMapper.updateBasicInfo(any(Store.class))).thenReturn(1);
        when(fileStorageClient.store(any(), org.mockito.ArgumentMatchers.eq("store")))
            .thenReturn("/uploads/store/202607/new.jpg");
        doThrow(new IllegalStateException("S3 delete failed"))
            .when(fileStorageClient).delete("/uploads/store/202601/old.jpg");
        MultipartFile image = new MockMultipartFile(
            "image", "cake.jpg", "image/jpeg", new byte[] {1, 2, 3});
        TransactionSynchronizationManager.initSynchronization();

        storeService.updateBasicInfo(validBasicInfoForm(), image);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit))
            .doesNotThrowAnyException();
    }

    @Test
    void updateBasicInfo_nonImageUpload_rejectsUpdate() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        MultipartFile notImage = new MockMultipartFile(
            "image", "note.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> storeService.updateBasicInfo(validBasicInfoForm(), notImage))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.INVALID_IMAGE));
        verify(storeMapper, never()).updateBasicInfo(any(Store.class));
    }

    @Test
    void addHolidayRejectsDuplicateDateWithBusinessErrorCode() {
        when(storeMapper.findStoreById(StoreService.DEFAULT_STORE_ID)).thenReturn(Optional.of(store()));
        LocalDate date = LocalDate.of(2026, 12, 25);
        when(storeMapper.existsHolidayDate(StoreService.DEFAULT_STORE_ID, date)).thenReturn(true);
        StoreHolidayForm form = new StoreHolidayForm();
        form.setHolidayDate(date);
        form.setReason("성탄절");

        assertThatThrownBy(() -> storeService.addHoliday(form))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(StoreErrorCode.HOLIDAY_ALREADY_EXISTS));
    }

    private Store store() {
        Store store = new Store();
        store.setId(StoreService.DEFAULT_STORE_ID);
        store.setName("기존 매장");
        return store;
    }

    private StoreBasicInfoForm validBasicInfoForm() {
        StoreBasicInfoForm form = new StoreBasicInfoForm();
        form.setName("스위트온 케이크");
        form.setDescription("예약 케이크 전문점");
        form.setAddress("서울시 OO구");
        form.setPhone("02-0000-0000");
        return form;
    }

    private StoreBusinessHoursForm validBusinessHoursForm() {
        StoreBusinessHoursForm form = new StoreBusinessHoursForm();
        form.setWeekdayOpenTime(LocalTime.of(10, 0));
        form.setWeekdayCloseTime(LocalTime.of(20, 0));
        form.setWeekendOpenTime(LocalTime.of(11, 0));
        form.setWeekendCloseTime(LocalTime.of(18, 0));
        return form;
    }

    private StorePickupInfoForm validPickupInfoForm() {
        StorePickupInfoForm form = new StorePickupInfoForm();
        form.setPickupPlace("1층 카운터");
        form.setPickupStartTime(LocalTime.of(10, 0));
        form.setPickupEndTime(LocalTime.of(19, 0));
        form.setPickupIntervalMinutes(60);
        return form;
    }
}
