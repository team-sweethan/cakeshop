package com.cakeshop.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.chat.dto.view.ChatOrderRoomView;
import com.cakeshop.domain.chat.mapper.ChatOrderMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatOrderQueryServiceTests {

    @Mock
    private ChatOrderMapper chatOrderMapper;

    @Test
    void findRoomById_nonPositiveId_returnsEmptyWithoutQuery() {
        ChatOrderQueryService service = new ChatOrderQueryService(chatOrderMapper);

        assertThat(service.findRoomById(0L)).isEmpty();

        verifyNoInteractions(chatOrderMapper);
    }

    @Test
    void findRoomById_existingRoom_returnsAuthoritativeCustomerId() {
        ChatOrderRoomView room = new ChatOrderRoomView(31L, 7L);
        when(chatOrderMapper.findRoomById(31L)).thenReturn(Optional.of(room));
        ChatOrderQueryService service = new ChatOrderQueryService(chatOrderMapper);

        assertThat(service.findRoomById(31L)).contains(room);

        verify(chatOrderMapper).findRoomById(31L);
    }
}
