package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private ImageService imageService;
    @Mock
    private StatusListener statusListener;

    private SecurityService securityService;
    private Sensor sensor;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        String sensorName = "sensor_" + UUID.randomUUID();
        sensor = new Sensor(sensorName, SensorType.DOOR);
    }

    /**
     * Create sensors.
     *
     * @param active True to set all sensors to active. Otherwise, set to inactive.
     * @param count  Number of sensors you want to create.
     * @return Set of sensors.
     */
    private static Set<Sensor> createSensors(boolean active, int count) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            var sensor = new Sensor("sensor_" + UUID.randomUUID(), SensorType.DOOR);
            sensor.setActive(active);
            sensors.add(sensor);
        }
        return sensors;
    }

    // 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void setAlarmStatus_whenAlarmArmedAndSensorActivated_toPending(ArmingStatus armingStatus) {
        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> alarmStatusCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(alarmStatusCaptor.capture());
        assertEquals(alarmStatusCaptor.getValue(), AlarmStatus.PENDING_ALARM);
    }

    // 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @Test
    void setAlarmStatus_whenAlarmAlreadyPendingAndSensorActivated_toAlarm() {
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMost(2)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 3. If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    void checkAlarmStatus_whenPendingAlarmAndAllSensorsInactive_returnNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 4. If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenActiveAlarm_whenChangeSensorState_shouldNotAffectAlarmStatus(boolean active) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, active);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    void ifSensorIsActivated_whileAlreadyActiveAndSystemIsInPendingState_changeItToAlarmState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void ifSensorIsDeactivated_whileAlreadyInactive_makeNoChangesToAlarmState(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    void ifImageServiceIdentifiesImageContainingCat_whileSystemIsArmedHome_setSystemIntoAlarmStatus() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(alarmStatusCaptor.capture());
        assertEquals(alarmStatusCaptor.getValue(), AlarmStatus.ALARM);
    }

    // 8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    void ifImageServiceIdentifiesImageNotContainingCat_changeStatusToNoAlarm_asLongAsSensorAreNotActive() {
        Set<Sensor> inactiveSensors = createSensors(false, 5);
        when(securityRepository.getSensors()).thenReturn(inactiveSensors);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(alarmStatusCaptor.capture());
        assertEquals(alarmStatusCaptor.getValue(), AlarmStatus.NO_ALARM);
    }

    // 9. If the system is disarmed, set the status to no alarm.
    @Test
    void ifSystemIsDisarmed_thenSetStatusToNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.NO_ALARM);
    }

    // 10. If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifSystemIsArmed_thenResetSensorsToInactive(ArmingStatus armingStatus) {
        Set<Sensor> activeSensors = createSensors(true, 5);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(activeSensors);

        securityService.setArmingStatus(armingStatus);
        securityService.getSensors().forEach(sensor1 -> assertEquals(sensor1.getActive(), false));
    }

    // 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    void ifSystemIsArmedHome_whileTheCameraShowsACat_thenSetAlarmStatusToAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> alarmStatusCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, times(1)).setAlarmStatus(alarmStatusCaptor.capture());
        assertEquals(alarmStatusCaptor.getValue(), AlarmStatus.ALARM);
    }

    @Test
    void ifCatDetectedAndArmingStatusArmedHome_setAlarmStatusAlarm() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        ArgumentCaptor<AlarmStatus> alarmStatusArgumentCaptor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce()).setAlarmStatus(alarmStatusArgumentCaptor.capture());
        assertEquals(alarmStatusArgumentCaptor.getValue(), AlarmStatus.ALARM);
    }

    @Test
    void ifAlarmStateAndSystemDisarmed_changeStatusToPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM"})
    void ifSystemDisarmedAndSensorActivated_noChangesToArmingState(AlarmStatus status) {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(status);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, never()).setArmingStatus(ArmingStatus.DISARMED);
    }

    @Test
    void ifAlarmStatusIsPendingAlarmAndSensorInactive_thenSetAlarmStatusToNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor);
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.NO_ALARM);
    }
}
