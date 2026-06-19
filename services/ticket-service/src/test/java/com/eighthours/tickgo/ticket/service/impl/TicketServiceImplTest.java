package com.eighthours.tickgo.ticket.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.ticket.dto.SeatTypeRemainDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.entity.SeatDO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.entity.TrainStationDO;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.mapper.SeatMapper;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.mapper.TrainStationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

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
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RedissonClient redissonClient;

    @Test
    void initTicketToken_shouldInitializeAllIntervalsForTrain() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(trainStationMapper.selectList(any())).thenReturn(buildStations());
        when(seatMapper.selectList(any())).thenReturn(List.of(buildSeatType(1)));

        TestableTicketServiceImpl service = new TestableTicketServiceImpl(
                trainStationMapper, trainMapper, seatMapper, ticketMapper, stringRedisTemplate, redissonClient);

        service.initTicketToken(1L);

        assertEquals(10, service.getQueriedIntervals().size());
        assertIterableEquals(
                List.of(
                        "北京南->济南西",
                        "北京南->南京南",
                        "北京南->杭州东",
                        "北京南->宁波",
                        "济南西->南京南",
                        "济南西->杭州东",
                        "济南西->宁波",
                        "南京南->杭州东",
                        "南京南->宁波",
                        "杭州东->宁波"),
                service.getQueriedIntervals());
        verify(valueOperations).set("ticket:token:1:北京南:宁波:1", "3");
        verify(valueOperations).set("ticket:token:1:北京南:杭州东:1", "2");
        verify(valueOperations).set("ticket:token:1:南京南:杭州东:1", "1");
    }

    @Test
    void preOccupySeats_shouldThrowExplicitExceptionWhenTokenMissing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(trainStationMapper.selectOne(any()))
                .thenReturn(buildStation(1L, "北京南", 1))
                .thenReturn(buildStation(1L, "杭州东", 4));
        TrainDO train = new TrainDO();
        train.setId(1L);
        train.setTrainNumber("G1001");
        when(trainMapper.selectById(1L)).thenReturn(train);
        when(valueOperations.get("ticket:token:1:北京南:杭州东:1")).thenReturn(null);

        TicketServiceImpl service = new TicketServiceImpl(
                trainStationMapper, trainMapper, seatMapper, ticketMapper, stringRedisTemplate, redissonClient);

        BizException ex = assertThrows(BizException.class, () -> service.preOccupySeats(
                1L, "北京南", "杭州东", "order-1", List.of(1001L), List.of(1)));

        assertEquals("TOKEN_NOT_INITIALIZED", ex.getCode());
        assertEquals("余票令牌未初始化，请先刷新当前车次余票", ex.getMessage());
        verify(valueOperations, never()).decrement(any(String.class), any(long.class));
    }

    private List<TrainStationDO> buildStations() {
        return List.of(
                buildStation(1L, "北京南", 1),
                buildStation(1L, "济南西", 2),
                buildStation(1L, "南京南", 3),
                buildStation(1L, "杭州东", 4),
                buildStation(1L, "宁波", 5));
    }

    private TrainStationDO buildStation(Long trainId, String name, Integer sequence) {
        TrainStationDO station = new TrainStationDO();
        station.setTrainId(trainId);
        station.setStationName(name);
        station.setSequenceNo(sequence);
        return station;
    }

    private SeatDO buildSeatType(Integer seatType) {
        SeatDO seat = new SeatDO();
        seat.setSeatType(seatType);
        return seat;
    }

    private static final class TestableTicketServiceImpl extends TicketServiceImpl {

        private final List<String> queriedIntervals = new ArrayList<>();

        private TestableTicketServiceImpl(TrainStationMapper trainStationMapper,
                                          TrainMapper trainMapper,
                                          SeatMapper seatMapper,
                                          TicketMapper ticketMapper,
                                          StringRedisTemplate stringRedisTemplate,
                                          RedissonClient redissonClient) {
            super(trainStationMapper, trainMapper, seatMapper, ticketMapper, stringRedisTemplate, redissonClient);
        }

        @Override
        public TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival) {
            queriedIntervals.add(departure + "->" + arrival);

            long remainCount = switch (departure + "->" + arrival) {
                case "北京南->宁波" -> 3L;
                case "北京南->杭州东" -> 2L;
                case "南京南->杭州东" -> 1L;
                default -> 0L;
            };

            SeatTypeRemainDTO remain = new SeatTypeRemainDTO();
            remain.setSeatType(1);
            remain.setRemainCount(remainCount);

            TicketQueryRespDTO resp = new TicketQueryRespDTO();
            resp.setTrainId(trainId);
            resp.setDeparture(departure);
            resp.setArrival(arrival);
            resp.setSeatTypeRemains(List.of(remain));
            return resp;
        }

        private List<String> getQueriedIntervals() {
            return queriedIntervals;
        }
    }
}
