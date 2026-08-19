package com.cakeshop.domain.store.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StoreMapperTests {

    private static final long STORE_ID = 1L;

    @Autowired
    private StoreMapper storeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        int updatedRows = jdbcTemplate.update(
            """
            UPDATE store
               SET name = '기존 매장',
                   description = '기존 소개',
                   image_url = '/uploads/store/original.jpg',
                   address = '기존 주소',
                   phone = '02-1111-2222',
                   pickup_place = '기존 픽업 장소',
                   pickup_start_time = '10:00:00',
                   pickup_end_time = '19:00:00',
                   pickup_interval_minutes = 60
             WHERE id = ?
            """,
            STORE_ID
        );
        assertThat(updatedRows).isEqualTo(1);
    }

    @Test
    void updateBasicInfo_validStore_updatesOnlyBasicColumns() {
        Store command = new Store();
        command.setId(STORE_ID);
        command.setName("변경 매장");
        command.setDescription("변경 소개");
        command.setImageUrl("/uploads/store/changed.jpg");
        command.setAddress("변경 주소");
        command.setPhone("02-3333-4444");
        command.setPickupPlace("변경되면 안 되는 픽업 장소");
        command.setPickupStartTime(LocalTime.of(8, 0));
        command.setPickupEndTime(LocalTime.of(17, 0));
        command.setPickupIntervalMinutes(30);

        assertThat(storeMapper.updateBasicInfo(command)).isEqualTo(1);

        Store updated = storeMapper.findStoreById(STORE_ID).orElseThrow();
        assertThat(updated).satisfies(store -> {
            assertThat(store.getName()).isEqualTo("변경 매장");
            assertThat(store.getDescription()).isEqualTo("변경 소개");
            assertThat(store.getImageUrl()).isEqualTo("/uploads/store/changed.jpg");
            assertThat(store.getAddress()).isEqualTo("변경 주소");
            assertThat(store.getPhone()).isEqualTo("02-3333-4444");
            assertThat(store.getPickupPlace()).isEqualTo("기존 픽업 장소");
            assertThat(store.getPickupStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(store.getPickupEndTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(store.getPickupIntervalMinutes()).isEqualTo(60);
        });
    }

    @Test
    void updatePickupInfo_validStore_updatesOnlyPickupColumns() {
        Store command = new Store();
        command.setId(STORE_ID);
        command.setName("변경되면 안 되는 매장명");
        command.setDescription("변경되면 안 되는 소개");
        command.setImageUrl("/uploads/store/should-not-change.jpg");
        command.setAddress("변경되면 안 되는 주소");
        command.setPhone("02-9999-9999");
        command.setPickupPlace("변경 픽업 장소");
        command.setPickupStartTime(LocalTime.of(11, 0));
        command.setPickupEndTime(LocalTime.of(18, 0));
        command.setPickupIntervalMinutes(30);

        assertThat(storeMapper.updatePickupInfo(command)).isEqualTo(1);

        Store updated = storeMapper.findStoreById(STORE_ID).orElseThrow();
        assertThat(updated).satisfies(store -> {
            assertThat(store.getName()).isEqualTo("기존 매장");
            assertThat(store.getDescription()).isEqualTo("기존 소개");
            assertThat(store.getImageUrl()).isEqualTo("/uploads/store/original.jpg");
            assertThat(store.getAddress()).isEqualTo("기존 주소");
            assertThat(store.getPhone()).isEqualTo("02-1111-2222");
            assertThat(store.getPickupPlace()).isEqualTo("변경 픽업 장소");
            assertThat(store.getPickupStartTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(store.getPickupEndTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(store.getPickupIntervalMinutes()).isEqualTo(30);
        });
    }
}
