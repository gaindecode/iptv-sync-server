package com.iptvplayer.sync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iptvplayer.sync.domain.device.Device;
import com.iptvplayer.sync.domain.pairing.PairCode;
import com.iptvplayer.sync.dto.DeviceDto;
import com.iptvplayer.sync.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceController.class)
@ActiveProfiles("test")
@DisplayName("DeviceController — Tests d'intégration")
class DeviceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DeviceService deviceService;

    @Test
    @DisplayName("POST /api/device/register — 200 avec deviceId et pairCode")
    void register_shouldReturn200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        OffsetDateTime expiry = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);

        when(deviceService.register(any())).thenReturn(
            new DeviceDto.RegisterResponse(deviceId, "AB82JK", expiry, "/link")
        );

        mockMvc.perform(post("/api/device/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "deviceName": "TV Samsung", "deviceType": "ANDROID_TV" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.pairCode").value("AB82JK"))
            .andExpect(jsonPath("$.pairCodeExpiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/device/register — 400 si deviceName manquant")
    void register_shouldReturn400_whenNameMissing() throws Exception {
        mockMvc.perform(post("/api/device/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"deviceType\": \"ANDROID_TV\" }"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/device/resolve/{code} — 200 si code valide")
    void resolveCode_shouldReturn200_whenValid() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Device device = Device.builder()
            .id(deviceId).deviceName("TV").status(Device.DeviceStatus.ONLINE)
            .lastSeen(LocalDateTime.now()).build();

        when(deviceService.resolveCode("AB82JK")).thenReturn(Optional.of(device));

        mockMvc.perform(get("/api/device/resolve/AB82JK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
            .andExpect(jsonPath("$.deviceName").value("TV"));
    }

    @Test
    @DisplayName("GET /api/device/resolve/{code} — 404 si code invalide")
    void resolveCode_shouldReturn404_whenInvalid() throws Exception {
        when(deviceService.resolveCode("XXXXXX")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/device/resolve/XXXXXX"))
            .andExpect(status().isNotFound());
    }
}
