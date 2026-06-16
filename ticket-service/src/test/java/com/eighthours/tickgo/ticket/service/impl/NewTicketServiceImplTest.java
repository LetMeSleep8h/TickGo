package com.eighthours.tickgo.ticket.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.ticket.dto.NewTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.entity.SeatDO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.entity.TrainStationDO;
import com.eighthours.tickgo.ticket.enums.TicketStatusEnum;
import com.eighthours.tickgo.ticket.mapper.SeatMapper;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.mapper.TrainStationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewTicketServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SeatDO.class);
        TableInfoHelper.initTableInfo(assistant, TrainStationDO.class);
        TableInfoHelper.initTableInfo(assistant, TrainDO.class);
        TableInfoHelper.initTableInfo(assistant, TicketDO.class);
    }

    @Mock
    private TrainStationMapper trainStationMapper;
    @Mock
    private TrainMapper trainMapper;
    @Mock
    private SeatMapper seatMapper;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    @Test
    void purchaseTicketsV2_shouldLockSeatAndCreateTicketWithoutLegacyPreOccupy() throws Exception {
        when(trainStationMapper.selectOne(any()))
                .thenReturn(buildStation("北京南", 1))
                .thenReturn(buildStation("杭州东", 4));
        TrainDO train = new TrainDO();
        train.setId(1L);
        train.setTrainNumber("G1001");
        when(trainMapper.selectById(1L)).thenReturn(train);
        when(redissonClient.getLock("ticket:purchase:lock:1:北京南:杭州东:1")).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seatMapper.selectList(any())).thenReturn(buildSeatSegments());
        when(seatMapper.update(any(), any())).thenReturn(3);

        NewTicketServiceImpl service = new NewTicketServiceImpl(
                trainStationMapper, trainMapper, seatMapper, ticketMapper, redissonClient);

        SeatPreOccupyRespDTO result = service.purchaseTicketsV2(buildPurchaseRequest());

        assertEquals("G1001", result.getTrainNumber());
        assertEquals(1, result.getItems().size());
        assertEquals("01", result.getItems().get(0).getCarriageNumber());
        assertEquals("01A", result.getItems().get(0).getSeatNumber());
        ArgumentCaptor<TicketDO> ticketCaptor = ArgumentCaptor.forClass(TicketDO.class);
        verify(ticketMapper).insert(ticketCaptor.capture());
        TicketDO saved = ticketCaptor.getValue();
        assertEquals("order-1", saved.getOrderSn());
        assertEquals(Long.valueOf(1001L), saved.getPassengerId());
        assertEquals(TicketStatusEnum.WAIT_PAY.getCode(), saved.getTicketStatus());
        verify(lock).unlock();
    }

    @Test
    void confirmAndRelease_shouldUpdateTicketStatusAndUnlockSeatSegments() throws Exception {
        TicketDO ticket = new TicketDO();
        ticket.setId(10L);
        ticket.setTrainId(1L);
        ticket.setDeparture("北京南");
        ticket.setArrival("杭州东");
        ticket.setOrderSn("order-2");
        ticket.setSeatType(1);
        ticket.setCarriageNumber("01");
        ticket.setSeatNumber("01A");
        ticket.setTicketStatus(TicketStatusEnum.WAIT_PAY.getCode());
        when(ticketMapper.selectList(any())).thenReturn(List.of(ticket));
        when(trainStationMapper.selectOne(any()))
                .thenReturn(buildStation("北京南", 1))
                .thenReturn(buildStation("杭州东", 4));
        when(redissonClient.getLock("ticket:purchase:lock:1:北京南:杭州东:1")).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seatMapper.update(any(), any())).thenReturn(3);
        when(ticketMapper.update(any(), any())).thenReturn(1);

        NewTicketServiceImpl service = new NewTicketServiceImpl(
                trainStationMapper, trainMapper, seatMapper, ticketMapper, redissonClient);

        NewTicketOrderReqDTO request = new NewTicketOrderReqDTO();
        request.setOrderSn("order-2");
        service.confirmTickets(request);
        service.releaseSeats(request);

        verify(ticketMapper, times(2)).selectList(any());
        verify(ticketMapper, times(2)).update(any(), any());
        verify(seatMapper).update(any(), any());
        verify(lock).unlock();
    }

    @Test
    void queryRemainTicket_shouldCountPhysicalSeatsByInterval() {
        when(trainStationMapper.selectOne(any()))
                .thenReturn(buildStation("北京南", 1))
                .thenReturn(buildStation("杭州东", 4));
        when(seatMapper.selectList(any())).thenReturn(buildSeatSegments());

        NewTicketServiceImpl service = new NewTicketServiceImpl(
                trainStationMapper, trainMapper, seatMapper, ticketMapper, redissonClient);

        TicketQueryRespDTO result = service.queryRemainTicket(1L, "北京南", "杭州东");

        assertNotNull(result);
        assertEquals(1, result.getSeatTypeRemains().size());
        assertEquals(1L, result.getSeatTypeRemains().get(0).getRemainCount());
    }

    private NewTicketPurchaseReqDTO buildPurchaseRequest() {
        NewTicketPurchaseReqDTO request = new NewTicketPurchaseReqDTO();
        request.setTrainId(1L);
        request.setDeparture("北京南");
        request.setArrival("杭州东");
        request.setOrderSn("order-1");
        NewTicketPurchaseReqDTO.PassengerDTO passenger = new NewTicketPurchaseReqDTO.PassengerDTO();
        passenger.setPassengerId(1001L);
        passenger.setSeatType(1);
        request.setPassengers(List.of(passenger));
        return request;
    }

    private List<SeatDO> buildSeatSegments() {
        return List.of(
                buildSeatSegment("北京南", "济南西", 1, 2),
                buildSeatSegment("济南西", "南京南", 2, 3),
                buildSeatSegment("南京南", "杭州东", 3, 4));
    }

    private SeatDO buildSeatSegment(String startStation, String endStation, int startSequence, int endSequence) {
        SeatDO seat = new SeatDO();
        seat.setTrainId(1L);
        seat.setCarriageNumber("01");
        seat.setSeatNumber("01A");
        seat.setSeatType(1);
        seat.setStartStation(startStation);
        seat.setEndStation(endStation);
        seat.setStartSequence(startSequence);
        seat.setEndSequence(endSequence);
        seat.setPrice(100);
        seat.setSeatStatus(0);
        return seat;
    }

    private TrainStationDO buildStation(String stationName, Integer sequenceNo) {
        TrainStationDO station = new TrainStationDO();
        station.setTrainId(1L);
        station.setStationName(stationName);
        station.setSequenceNo(sequenceNo);
        return station;
    }
}
