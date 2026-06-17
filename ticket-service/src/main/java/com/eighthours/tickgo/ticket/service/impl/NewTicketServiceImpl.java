package com.eighthours.tickgo.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.CreateTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.dto.SeatItemDTO;
import com.eighthours.tickgo.ticket.dto.SeatPreOccupyRespDTO;
import com.eighthours.tickgo.ticket.dto.SeatTypeRemainDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.entity.SeatDO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.entity.TrainStationDO;
import com.eighthours.tickgo.ticket.enums.TicketStatusEnum;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.feign.OrderServiceClient;
import com.eighthours.tickgo.ticket.idempotent.Idempotent;
import com.eighthours.tickgo.ticket.mapper.SeatMapper;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.mapper.TrainStationMapper;
import com.eighthours.tickgo.ticket.service.NewTicketService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewTicketServiceImpl implements NewTicketService {

    private static final String USERNAME = "admin";

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final RedissonClient redissonClient;
    private final OrderServiceClient orderServiceClient;

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
    @Idempotent(prefix = "ticket:purchase")
    public SeatPreOccupyRespDTO purchaseTicketsV2(NewTicketPurchaseReqDTO request) {
        Map<Integer, List<NewTicketPurchaseReqDTO.PassengerDTO>> seatTypeMap = request.getPassengers().stream()
                .collect(Collectors.groupingBy(NewTicketPurchaseReqDTO.PassengerDTO::getSeatType));
        List<RLock> lockList = new ArrayList<>();
        try {
            for (Integer seatType : seatTypeMap.keySet()) {
                RLock lock = redissonClient.getLock(buildPurchaseLockKey(
                        request.getTrainId(), request.getDeparture(), request.getArrival(), seatType));
                boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BizException("购票人数过多，请稍后重试");
                }
                lockList.add(lock);
            }
            SeatPreOccupyRespDTO purchaseResp = executePurchaseTickets(request);
            Result<Void> createOrderResult;
            try {
                createOrderResult = orderServiceClient.createOrder(buildCreateOrderRequest(request, purchaseResp));
            } catch (Exception ex) {
                releaseSeats(buildOrderRequest(request.getOrderSn()));
                throw new BizException("订单服务调用失败");
            }
            if (createOrderResult == null || createOrderResult.getCode() != 200) {
                releaseSeats(buildOrderRequest(request.getOrderSn()));
                throw new BizException(createOrderResult == null ? "订单服务调用失败" : createOrderResult.getMessage());
            }
            return purchaseResp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("购票过程被中断，请稍后重试");
        } finally {
            lockList.forEach(lock -> {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeatPreOccupyRespDTO executePurchaseTickets(NewTicketPurchaseReqDTO request) {
        StationSegment segment = resolveStationSegment(request.getTrainId(), request.getDeparture(), request.getArrival());
        TrainDO train = trainMapper.selectById(request.getTrainId());
        if (train == null) {
            throw new BizException("车次不存在");
        }

        List<SeatItemDTO> items = new ArrayList<>();
        for (NewTicketPurchaseReqDTO.PassengerDTO passenger : request.getPassengers()) {
            List<SeatDO> selectedSegments = findAvailableSeat(
                    request.getTrainId(),
                    segment.departureSequence(),
                    segment.arrivalSequence(),
                    segment.segmentCount(),
                    passenger.getSeatType());
            lockSeatSegments(
                    request.getTrainId(),
                    selectedSegments,
                    segment.departureSequence(),
                    segment.arrivalSequence());

            SeatDO firstSegment = selectedSegments.get(0);
            int amount = selectedSegments.stream().mapToInt(SeatDO::getPrice).sum();
            TicketDO ticket = new TicketDO();
            ticket.setUsername(USERNAME);
            ticket.setTrainId(request.getTrainId());
            ticket.setCarriageNumber(firstSegment.getCarriageNumber());
            ticket.setSeatNumber(firstSegment.getSeatNumber());
            ticket.setPassengerId(passenger.getPassengerId());
            ticket.setSeatType(passenger.getSeatType());
            ticket.setTicketStatus(TicketStatusEnum.WAIT_PAY.getCode());
            ticket.setOrderSn(request.getOrderSn());
            ticket.setDeparture(request.getDeparture());
            ticket.setArrival(request.getArrival());
            ticketMapper.insert(ticket);

            SeatItemDTO item = new SeatItemDTO();
            item.setPassengerId(passenger.getPassengerId());
            item.setSeatType(passenger.getSeatType());
            item.setCarriageNumber(firstSegment.getCarriageNumber());
            item.setSeatNumber(firstSegment.getSeatNumber());
            item.setAmount(amount);
            items.add(item);
        }

        SeatPreOccupyRespDTO response = new SeatPreOccupyRespDTO();
        response.setTrainNumber(train.getTrainNumber());
        response.setItems(items);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmTickets(NewTicketOrderReqDTO request) {
        List<TicketDO> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, request.getOrderSn())
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
    public void releaseSeats(NewTicketOrderReqDTO request) {
        List<TicketDO> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, request.getOrderSn())
                        .eq(TicketDO::getTicketStatus, TicketStatusEnum.WAIT_PAY.getCode()));
        if (CollectionUtils.isEmpty(tickets)) {
            return;
        }

        Map<String, List<TicketDO>> seatTypeGroup = tickets.stream()
                .collect(Collectors.groupingBy(ticket ->
                        ticket.getTrainId() + ":" + ticket.getDeparture() + ":" + ticket.getArrival() + ":" + ticket.getSeatType()));

        for (Map.Entry<String, List<TicketDO>> entry : seatTypeGroup.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long trainId = Long.parseLong(parts[0]);
            String departure = parts[1];
            String arrival = parts[2];
            Integer seatType = Integer.parseInt(parts[3]);
            StationSegment segment = resolveStationSegment(trainId, departure, arrival);
            RLock lock = redissonClient.getLock(buildPurchaseLockKey(trainId, departure, arrival, seatType));
            boolean locked = false;
            try {
                locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    throw new BizException("释放座位失败，请稍后重试");
                }

                for (TicketDO ticket : entry.getValue()) {
                    seatMapper.update(null,
                            new LambdaUpdateWrapper<SeatDO>()
                                    .set(SeatDO::getSeatStatus, 0)
                                    .eq(SeatDO::getTrainId, trainId)
                                    .eq(SeatDO::getCarriageNumber, ticket.getCarriageNumber())
                                    .eq(SeatDO::getSeatNumber, ticket.getSeatNumber())
                                    .eq(SeatDO::getSeatType, seatType)
                                    .ge(SeatDO::getStartSequence, segment.departureSequence())
                                    .le(SeatDO::getEndSequence, segment.arrivalSequence())
                                    .eq(SeatDO::getSeatStatus, 1));
                    ticketMapper.update(null,
                            new LambdaUpdateWrapper<TicketDO>()
                                    .set(TicketDO::getTicketStatus, TicketStatusEnum.CANCELED.getCode())
                                    .eq(TicketDO::getId, ticket.getId()));
                }
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
        if (startStation.getSequenceNo() >= endStation.getSequenceNo()) {
            throw new BizException("到达站必须在出发站之后");
        }
        return new StationSegment(
                startStation.getSequenceNo(),
                endStation.getSequenceNo(),
                endStation.getSequenceNo() - startStation.getSequenceNo());
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
                .orElseThrow(() -> new BizException("没有可用座位，seatType=" + seatType));
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
        int updated = seatMapper.update(null, new LambdaUpdateWrapper<SeatDO>()
                .set(SeatDO::getSeatStatus, 1)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, firstSegment.getCarriageNumber())
                .eq(SeatDO::getSeatNumber, firstSegment.getSeatNumber())
                .eq(SeatDO::getSeatType, firstSegment.getSeatType())
                .ge(SeatDO::getStartSequence, departureSequence)
                .le(SeatDO::getEndSequence, arrivalSequence)
                .eq(SeatDO::getSeatStatus, 0));
        if (updated != selectedSegments.size()) {
            throw new BizException("座位锁定失败，可能已被占用");
        }
    }

    private String buildPurchaseLockKey(Long trainId, String departure, String arrival, Integer seatType) {
        return "ticket:purchase:lock:" + trainId + ":" + departure + ":" + arrival + ":" + seatType;
    }

    private NewTicketOrderReqDTO buildOrderRequest(String orderSn) {
        NewTicketOrderReqDTO request = new NewTicketOrderReqDTO();
        request.setOrderSn(orderSn);
        return request;
    }

    private CreateTicketOrderReqDTO buildCreateOrderRequest(NewTicketPurchaseReqDTO request,
                                                            SeatPreOccupyRespDTO purchaseResp) {
        CreateTicketOrderReqDTO createOrderReq = new CreateTicketOrderReqDTO();
        createOrderReq.setOrderSn(request.getOrderSn());
        createOrderReq.setUserId(request.getUserId());
        createOrderReq.setUsername(USERNAME);
        createOrderReq.setTrainId(request.getTrainId());
        createOrderReq.setTrainNumber(purchaseResp.getTrainNumber());
        createOrderReq.setDeparture(request.getDeparture());
        createOrderReq.setArrival(request.getArrival());
        createOrderReq.setItems(purchaseResp.getItems().stream()
                .map(this::buildOrderItem)
                .toList());
        return createOrderReq;
    }

    private CreateTicketOrderReqDTO.OrderItemDTO buildOrderItem(SeatItemDTO item) {
        CreateTicketOrderReqDTO.OrderItemDTO orderItem = new CreateTicketOrderReqDTO.OrderItemDTO();
        orderItem.setPassengerId(item.getPassengerId());
        orderItem.setSeatType(item.getSeatType());
        orderItem.setCarriageNumber(item.getCarriageNumber());
        orderItem.setSeatNumber(item.getSeatNumber());
        orderItem.setAmount(item.getAmount());
        return orderItem;
    }

    private String physicalSeatKey(SeatDO seat) {
        return seat.getCarriageNumber() + "|" + seat.getSeatNumber() + "|" + seat.getSeatType();
    }

    private record StationSegment(Integer departureSequence, Integer arrivalSequence, int segmentCount) {
    }
}
