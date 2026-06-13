package com.eighthours.tickgo.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.*;
import com.eighthours.tickgo.ticket.enums.TicketStatusEnum;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.entity.SeatDO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.entity.TrainStationDO;
import com.eighthours.tickgo.ticket.mapper.SeatMapper;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.mapper.TrainStationMapper;
import com.eighthours.tickgo.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final String USERNAME = "admin";
    private static final String TOKEN_NOT_INITIALIZED = "TOKEN_NOT_INITIALIZED";

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private String buildTokenKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:token:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    private String buildPurchaseLockKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:purchase:lock:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    @Override
    public void initTicketToken(Long trainId) {
        List<TrainStationDO> stations = loadTrainStations(trainId);
        List<Integer> allSeatTypes = loadAllSeatTypes(trainId);
        if (allSeatTypes.isEmpty()) {
            return;
        }

        for (int i = 0; i < stations.size() - 1; i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                refreshIntervalToken(trainId, stations.get(i).getStationName(), stations.get(j).getStationName(), allSeatTypes);
            }
        }
    }


    @Override
    public SeatDO purchaseV1(purchaseDTO request) {
        List<SeatDO> seatDOList = seatMapper.selectList(
                new LambdaQueryWrapper<SeatDO>()
                        .eq(SeatDO::getTrainId, request.getTrainId())
                        .eq(SeatDO::getSeatType, request.getSeatType())
                        .eq(SeatDO::getStartStation, request.getDeparture())
                        .eq(SeatDO::getEndStation, request.getArrival())
        );
        SeatDO seat = seatDOList.get(0);
        seatMapper.update(
                new LambdaUpdateWrapper<SeatDO>()
                        .set(SeatDO::getSeatStatus, 1)
                        .eq(SeatDO::getId, seat.getId())
        );


        return seat;
    }

    @Override
    public TicketQueryRespDTO queryRemainTicket(Long trainId, String departure, String arrival) {
        StationSegment segment = resolveStationSegment(trainId, departure, arrival);
        List<SeatTypeRemainDTO> seatTypeRemains = countSeatTypeRemains(
                trainId, segment.departureSequence(), segment.arrivalSequence(), segment.segmentCount(), null);

        TicketQueryRespDTO resp = new TicketQueryRespDTO();
        resp.setTrainId(trainId);
        resp.setDeparture(departure);
        resp.setArrival(arrival);
        resp.setSeatTypeRemains(seatTypeRemains);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeatPreOccupyRespDTO preOccupySeats(Long trainId, String departure, String arrival, String orderSn,
                                               List<Long> passengerIds, List<Integer> seatTypes) {
        StationSegment segment = resolveStationSegment(trainId, departure, arrival);
        TrainDO train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new BizException("车次不存在");
        }

        Map<Long, Integer> passengerSeatMap = new HashMap<>();
        for (int i = 0; i < passengerIds.size(); i++) {
            passengerSeatMap.put(passengerIds.get(i), seatTypes.get(i));
        }
        Map<Integer, Integer> seatTypeCountMap = new HashMap<>();
        for (Integer seatType : seatTypes) {
            seatTypeCountMap.merge(seatType, 1, Integer::sum);
        }

        Map<Integer, Integer> deductedTokenMap = new HashMap<>();
        try {
            for (Map.Entry<Integer, Integer> entry : seatTypeCountMap.entrySet()) {
                Integer seatType = entry.getKey();
                Integer count = entry.getValue();
                String key = buildTokenKey(trainId, departure, arrival, seatType);
                String tokenValue = stringRedisTemplate.opsForValue().get(key);
                if (tokenValue == null) {
                    throw new BizException(TOKEN_NOT_INITIALIZED, "余票令牌未初始化，请先刷新当前车次余票");
                }

                Long token = stringRedisTemplate.opsForValue().decrement(key, count);
                if (token == null || token < 0) {
                    deductedTokenMap.put(seatType, count);
                    throw new BizException("余票不足, seatType=" + seatType);
                }
                deductedTokenMap.put(seatType, count);
            }

            List<SeatItemDTO> result = new ArrayList<>();
            for (Long passengerId : passengerIds) {
                Integer seatType = passengerSeatMap.get(passengerId);
                RLock lock = redissonClient.getLock(buildPurchaseLockKey(trainId, departure, arrival, seatType));
                boolean locked = false;
                try {
                    locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                    if (!locked) {
                        throw new BizException("购票人数过多，请稍后重试");
                    }

                    List<SeatDO> selectedSegments = findAvailableSeat(
                            trainId,
                            segment.departureSequence(),
                            segment.arrivalSequence(),
                            segment.segmentCount(),
                            seatType);
                    lockSeatSegments(trainId, selectedSegments, segment.departureSequence(), segment.arrivalSequence());

                    int amount = selectedSegments.stream().mapToInt(SeatDO::getPrice).sum();
                    SeatDO firstSegment = selectedSegments.get(0);

                    TicketDO ticket = new TicketDO();
                    ticket.setUsername(USERNAME);
                    ticket.setTrainId(trainId);
                    ticket.setCarriageNumber(firstSegment.getCarriageNumber());
                    ticket.setSeatNumber(firstSegment.getSeatNumber());
                    ticket.setPassengerId(passengerId);
                    ticket.setSeatType(seatType);
                    ticket.setTicketStatus(TicketStatusEnum.WAIT_PAY.getCode());
                    ticket.setOrderSn(orderSn);
                    ticket.setDeparture(departure);
                    ticket.setArrival(arrival);
                    ticketMapper.insert(ticket);

                    SeatItemDTO item = new SeatItemDTO();
                    item.setPassengerId(passengerId);
                    item.setSeatType(seatType);
                    item.setCarriageNumber(firstSegment.getCarriageNumber());
                    item.setSeatNumber(firstSegment.getSeatNumber());
                    item.setAmount(amount);
                    result.add(item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("购票过程被中断，请稍后重试");
                } finally {
                    if (locked && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            initTicketToken(trainId);

            SeatPreOccupyRespDTO response = new SeatPreOccupyRespDTO();
            response.setTrainNumber(train.getTrainNumber());
            response.setItems(result);
            return response;
        } catch (Exception e) {
            for (Map.Entry<Integer, Integer> entry : deductedTokenMap.entrySet()) {
                String key = buildTokenKey(trainId, departure, arrival, entry.getKey());
                stringRedisTemplate.opsForValue().increment(key, entry.getValue());
            }
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmTickets(String orderSn) {
        List<TicketDO> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, orderSn)
                        .eq(TicketDO::getTicketStatus, TicketStatusEnum.WAIT_PAY.getCode()));
        if (CollectionUtils.isEmpty(tickets)) {
            return;
        }

        for (TicketDO ticket : tickets) {
            ticketMapper.update(null,
                    new LambdaUpdateWrapper<TicketDO>()
                            .set(TicketDO::getTicketStatus, TicketStatusEnum.PAID.getCode())
                            .eq(TicketDO::getId, ticket.getId()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseSeats(String orderSn) {
        List<TicketDO> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, orderSn)
                        .eq(TicketDO::getTicketStatus, TicketStatusEnum.WAIT_PAY.getCode()));
        if (CollectionUtils.isEmpty(tickets)) {
            return;
        }

        Map<Long, TicketDO> ticketMap = tickets.stream().collect(Collectors.toMap(TicketDO::getId, t -> t));
        Map<String, List<TicketDO>> seatTypeGroup = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getTrainId() + ":" + t.getDeparture() + ":" + t.getArrival() + ":" + t.getSeatType()));

        for (Map.Entry<String, List<TicketDO>> entry : seatTypeGroup.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long trainId = Long.parseLong(parts[0]);
            String departure = parts[1];
            String arrival = parts[2];
            Integer seatType = Integer.parseInt(parts[3]);
            List<TicketDO> sameTypeTickets = entry.getValue();

            TrainStationDO startStation = trainStationMapper.selectOne(
                    new LambdaQueryWrapper<TrainStationDO>()
                            .eq(TrainStationDO::getTrainId, trainId)
                            .eq(TrainStationDO::getStationName, departure));
            TrainStationDO endStation = trainStationMapper.selectOne(
                    new LambdaQueryWrapper<TrainStationDO>()
                            .eq(TrainStationDO::getTrainId, trainId)
                            .eq(TrainStationDO::getStationName, arrival));
            if (startStation == null || endStation == null) {
                throw new BizException("车站信息不存在");
            }

            Integer departureSequence = startStation.getSequenceNo();
            Integer arrivalSequence = endStation.getSequenceNo();

            RLock lock = redissonClient.getLock(buildPurchaseLockKey(trainId, departure, arrival, seatType));
            boolean locked = false;
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BizException("释放座位失败，请稍后重试");
                }

                for (TicketDO ticket : sameTypeTickets) {
                    int updated = seatMapper.update(null,
                            new LambdaUpdateWrapper<SeatDO>()
                                    .set(SeatDO::getSeatStatus, 0)
                                    .eq(SeatDO::getTrainId, trainId)
                                    .eq(SeatDO::getCarriageNumber, ticket.getCarriageNumber())
                                    .eq(SeatDO::getSeatNumber, ticket.getSeatNumber())
                                    .eq(SeatDO::getSeatType, seatType)
                                    .ge(SeatDO::getStartSequence, departureSequence)
                                    .le(SeatDO::getEndSequence, arrivalSequence)
                                    .eq(SeatDO::getSeatStatus, 1));
                }

                for (TicketDO ticket : sameTypeTickets) {
                    ticketMapper.update(null,
                            new LambdaUpdateWrapper<TicketDO>()
                                    .set(TicketDO::getTicketStatus, TicketStatusEnum.CANCELED.getCode())
                                    .eq(TicketDO::getId, ticket.getId()));
                }
                initTicketToken(trainId);
//                String tokenKey = buildTokenKey(trainId, departure, arrival, seatType);
//                stringRedisTemplate.opsForValue().increment(tokenKey, sameTypeTickets.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException("释放座位过程被中断，请稍后重试");
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    private StationSegment resolveStationSegment(Long trainId, String departure, String arrival) {
        TrainStationDO startStation = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getStationName, departure));
        TrainStationDO endStation = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .eq(TrainStationDO::getStationName, arrival));
        if (startStation == null || endStation == null) {
            throw new BizException("出发站或到达站不存在");
        }

        Integer departureSequence = startStation.getSequenceNo();
        Integer arrivalSequence = endStation.getSequenceNo();
        if (departureSequence >= arrivalSequence) {
            throw new BizException("到达站必须在出发站之后");
        }

        return new StationSegment(departureSequence, arrivalSequence, arrivalSequence - departureSequence);
    }

    private List<TrainStationDO> loadTrainStations(Long trainId) {
        List<TrainStationDO> stations = trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .orderByAsc(TrainStationDO::getSequenceNo));
        if (stations.size() < 2) {
            throw new BizException("车次站点不存在");
        }
        return stations;
    }

    private List<Integer> loadAllSeatTypes(Long trainId) {
        return seatMapper.selectList(
                        new LambdaQueryWrapper<SeatDO>()
                                .eq(SeatDO::getTrainId, trainId)
                                .select(SeatDO::getSeatType)
                                .groupBy(SeatDO::getSeatType))
                .stream()
                .map(SeatDO::getSeatType)
                .collect(Collectors.toList());
    }

    private void refreshIntervalToken(Long trainId, String departure, String arrival, List<Integer> allSeatTypes) {
        TicketQueryRespDTO resp = queryRemainTicket(trainId, departure, arrival);

        Map<Integer, Long> remainCountMap = resp.getSeatTypeRemains().stream()
                .collect(Collectors.toMap(SeatTypeRemainDTO::getSeatType, SeatTypeRemainDTO::getRemainCount));

        for (Integer seatType : allSeatTypes) {
            Long remainCount = remainCountMap.getOrDefault(seatType, 0L);
            String key = buildTokenKey(trainId, departure, arrival, seatType);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(remainCount));
        }
    }

    private List<SeatTypeRemainDTO> countSeatTypeRemains(Long trainId,
                                                          Integer departureSequence,
                                                          Integer arrivalSequence,
                                                          int segmentCount,
                                                          Integer seatTypeFilter) {
        List<SeatDO> seats = queryCandidateSeats(trainId, departureSequence, arrivalSequence, seatTypeFilter);

        Map<Integer, Long> seatTypeRemainMap = seats.stream()
                .collect(Collectors.groupingBy(this::physicalSeatKey))
                .values().stream()
                .filter(segmentSeats -> segmentSeats.size() == segmentCount)
                .collect(Collectors.groupingBy(
                        segmentSeats -> segmentSeats.get(0).getSeatType(),
                        Collectors.counting()));

        List<SeatTypeRemainDTO> seatTypeRemains = new ArrayList<>();
        seatTypeRemainMap.forEach((seatType, remainCount) -> {
            SeatTypeRemainDTO dto = new SeatTypeRemainDTO();
            dto.setSeatType(seatType);
            dto.setRemainCount(remainCount);
            seatTypeRemains.add(dto);
        });
        return seatTypeRemains;
    }

    private List<SeatDO> findAvailableSeat(Long trainId,
                                            Integer departureSequence,
                                            Integer arrivalSequence,
                                            int segmentCount,
                                            Integer seatType) {
        List<SeatDO> seats = queryCandidateSeats(trainId, departureSequence, arrivalSequence, seatType);

        return seats.stream()
                .collect(Collectors.groupingBy(this::physicalSeatKey))
                .values().stream()
                .filter(segmentSeats -> segmentSeats.size() == segmentCount)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可用座位，seatType=" + seatType));
    }

    private List<SeatDO> queryCandidateSeats(Long trainId,
                                              Integer departureSequence,
                                              Integer arrivalSequence,
                                              Integer seatType) {
        LambdaQueryWrapper<SeatDO> wrapper = new LambdaQueryWrapper<SeatDO>()
                .eq(SeatDO::getTrainId, trainId)
                .ge(SeatDO::getStartSequence, departureSequence)
                .le(SeatDO::getEndSequence, arrivalSequence)
                .eq(SeatDO::getSeatStatus, 0);
        if (seatType != null) {
            wrapper.eq(SeatDO::getSeatType, seatType);
        }
        return seatMapper.selectList(wrapper);
    }

    private void lockSeatSegments(Long trainId,
                                   List<SeatDO> selectedSegments,
                                   Integer departureSequence,
                                   Integer arrivalSequence) {
        SeatDO firstSegment = selectedSegments.get(0);
        int segmentCount = selectedSegments.size();

        int updated = seatMapper.update(null, new LambdaUpdateWrapper<SeatDO>()
                .set(SeatDO::getSeatStatus, 1)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, firstSegment.getCarriageNumber())
                .eq(SeatDO::getSeatNumber, firstSegment.getSeatNumber())
                .eq(SeatDO::getSeatType, firstSegment.getSeatType())
                .ge(SeatDO::getStartSequence, departureSequence)
                .le(SeatDO::getEndSequence, arrivalSequence)
                .eq(SeatDO::getSeatStatus, 0));

        if (updated != segmentCount) {
            throw new IllegalStateException("座位锁定失败，可能已被占用");
        }
    }

    private String physicalSeatKey(SeatDO seat) {
        return seat.getCarriageNumber() + "|" + seat.getSeatNumber() + "|" + seat.getSeatType();
    }

    private record StationSegment(Integer departureSequence, Integer arrivalSequence, int segmentCount) {
    }
}
