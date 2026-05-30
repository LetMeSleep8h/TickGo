package com.eighthours.tickgo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.dto.PassengerPurchaseReqDTO;
import com.eighthours.tickgo.dto.SeatTypeRemainDTO;
import com.eighthours.tickgo.dto.TicketPurchaseReqDTO;
import com.eighthours.tickgo.dto.TicketPurchaseRespDTO;
import com.eighthours.tickgo.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.entity.OrderDO;
import com.eighthours.tickgo.entity.OrderItemDO;
import com.eighthours.tickgo.entity.SeatDO;
import com.eighthours.tickgo.entity.TicketDO;
import com.eighthours.tickgo.entity.TrainDO;
import com.eighthours.tickgo.entity.TrainStationDO;
import com.eighthours.tickgo.mapper.OrderItemMapper;
import com.eighthours.tickgo.mapper.OrderMapper;
import com.eighthours.tickgo.mapper.SeatMapper;
import com.eighthours.tickgo.mapper.TicketMapper;
import com.eighthours.tickgo.mapper.TrainMapper;
import com.eighthours.tickgo.mapper.TrainStationMapper;
import com.eighthours.tickgo.enums.OrderItemStatusEnum;
import com.eighthours.tickgo.enums.OrderStatusEnum;
import com.eighthours.tickgo.enums.TicketStatusEnum;
import com.eighthours.tickgo.exception.BizException;
import com.eighthours.tickgo.mq.OrderCancelProducer;
import com.eighthours.tickgo.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final long USER_ID = 1L;
    private static final String USERNAME = "admin";

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final OrderCancelProducer orderCancelProducer;

    private String buildTokenKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:token:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    private String buildPurchaseLockKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:purchase:lock:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    private void rollbackTokens(Long trainId, String departure, String arrival, Map<Integer, Integer> deductedTokenMap) {
        for (Map.Entry<Integer, Integer> entry : deductedTokenMap.entrySet()) {
            String key = buildTokenKey(trainId, departure, arrival, entry.getKey());
            stringRedisTemplate.opsForValue().increment(key, entry.getValue());
        }
    }

    @Override
    public void initTicketToken(Long trainId, String departure, String arrival) {
        // 先查询数据库中该 trainId 下的所有 seatType（包括无票的）
        List<Integer> allSeatTypes = seatMapper.selectList(
                        new LambdaQueryWrapper<SeatDO>()
                                .eq(SeatDO::getTrainId, trainId)
                                .select(SeatDO::getSeatType)
                                .groupBy(SeatDO::getSeatType))
                .stream()
                .map(SeatDO::getSeatType)
                .collect(Collectors.toList());
        
        TicketQueryRespDTO resp = queryRemainTicket(trainId, departure, arrival);
        
        // 构建余票 map，方便查找
        Map<Integer, Long> remainCountMap = resp.getSeatTypeRemains().stream()
                .collect(Collectors.toMap(SeatTypeRemainDTO::getSeatType, SeatTypeRemainDTO::getRemainCount));
        
        // 遍历数据库中所有的 seatType，设置 Redis（包括余票为 0 的）
        for (Integer seatType : allSeatTypes) {
            Long remainCount = remainCountMap.getOrDefault(seatType, 0L);
            String key = buildTokenKey(trainId, departure, arrival, seatType);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(remainCount));
        }
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
    public TicketPurchaseRespDTO purchaseTicket(TicketPurchaseReqDTO requestParam) {
        if (requestParam == null || CollectionUtils.isEmpty(requestParam.getPassengers())) {
            throw new BizException("乘车人不能为空");
        }

        Long trainId = requestParam.getTrainId();
        String departure = requestParam.getDeparture();
        String arrival = requestParam.getArrival();
        StationSegment segment = resolveStationSegment(trainId, departure, arrival);

        TrainDO train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new BizException("车次不存在");
        }

        // Step 0: 校验乘客字段
        for (PassengerPurchaseReqDTO passenger : requestParam.getPassengers()) {
            if (passenger == null) {
                throw new BizException("乘客信息不能为空");
            }
            if (passenger.getPassengerId() == null || passenger.getPassengerId() <= 0) {
                throw new BizException("乘客ID不能为空");
            }
            if (passenger.getSeatType() == null) {
                throw new BizException("座位类型不能为空");
            }
        }

        // Step 1: 按 seatType 分组乘客
        Map<Integer, Integer> seatTypeCountMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PassengerPurchaseReqDTO::getSeatType, Collectors.summingInt(p -> 1)));

        Map<Integer, Integer> deductedTokenMap = new HashMap<>();
        try {
            // Step 2: 批量扣减 token
            for (Map.Entry<Integer, Integer> entry : seatTypeCountMap.entrySet()) {
                Integer seatType = entry.getKey();
                Integer count = entry.getValue();
                String key = buildTokenKey(trainId, departure, arrival, seatType);

                Long token = stringRedisTemplate.opsForValue().decrement(key, count);
                if (token == null || token < 0) {
                    // 只记录已扣减的，让外层 catch 统一回滚
                    deductedTokenMap.put(seatType, count);
                    throw new BizException("余票不足, seatType=" + seatType);
                }
                deductedTokenMap.put(seatType, count);
            }

            // Step 3: MySQL 锁座和写单流程（带分布式锁）
            String orderSn = generateOrderSn();
            for (PassengerPurchaseReqDTO passenger : requestParam.getPassengers()) {
                Integer seatType = passenger.getSeatType();
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
                    ticket.setPassengerId(passenger.getPassengerId());
                    ticket.setSeatType(seatType);
                    ticket.setTicketStatus(TicketStatusEnum.WAIT_PAY.getCode());
                    ticketMapper.insert(ticket);

                    OrderItemDO orderItem = new OrderItemDO();
                    orderItem.setOrderSn(orderSn);
                    orderItem.setUserId(USER_ID);
                    orderItem.setUsername(USERNAME);
                    orderItem.setTrainId(trainId);
                    orderItem.setCarriageNumber(firstSegment.getCarriageNumber());
                    orderItem.setSeatNumber(firstSegment.getSeatNumber());
                    orderItem.setSeatType(seatType);
                    orderItem.setRealName("Passenger-" + passenger.getPassengerId());
                    orderItem.setIdCard("MOCK-" + passenger.getPassengerId());
                    orderItem.setStatus(OrderItemStatusEnum.WAIT_PAY.getCode());
                    orderItem.setAmount(amount);
                    orderItemMapper.insert(orderItem);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("购票过程被中断，请稍后重试");
                } finally {
                    if (locked && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            OrderDO order = new OrderDO();
            order.setOrderSn(orderSn);
            order.setUserId(USER_ID);
            order.setUsername(USERNAME);
            order.setTrainId(trainId);
            order.setTrainNumber(train.getTrainNumber());
            order.setDeparture(departure);
            order.setArrival(arrival);
            order.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
            order.setOrderTime(LocalDateTime.now());
            orderMapper.insert(order);

            TicketPurchaseRespDTO resp = new TicketPurchaseRespDTO();
            resp.setOrderSn(orderSn);

            // 在事务提交后发送延迟取消消息
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // RocketMQ 延迟等级: 4 对应 30秒
                    orderCancelProducer.sendCancelDelayMessage(orderSn, 4);
                }
            });

            return resp;
        } catch (Exception e) {
            // Step 4: 异常时回滚 token
            rollbackTokens(trainId, departure, arrival, deductedTokenMap);
            throw e;
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

    private String generateOrderSn() {
        return UUID.randomUUID().toString();
    }

    private record StationSegment(Integer departureSequence, Integer arrivalSequence, int segmentCount) {
    }

}
