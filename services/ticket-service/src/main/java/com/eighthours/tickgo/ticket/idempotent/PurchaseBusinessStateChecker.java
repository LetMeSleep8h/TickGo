package com.eighthours.tickgo.ticket.idempotent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.enums.TicketStatusEnum;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseBusinessStateChecker {

    private final TicketMapper ticketMapper;

    public void check(NewTicketPurchaseReqDTO request) {
        PurchaseBlockDecision decision = resolveDecision(request);
        if (PurchaseBlockStatusEnum.ALLOW.equals(decision.blockStatus())) {
            return;
        }
        throw new BizException(buildMessage(decision));
    }

    private PurchaseBlockDecision resolveDecision(NewTicketPurchaseReqDTO request) {
        Map<PurchaseBlockStatusEnum, Set<Long>> statusPassengerMap = queryBlockedPassengers(request);
        PurchaseBlockStatusEnum blockStatus = statusPassengerMap.keySet().stream()
                .max(Comparator.comparingInt(PurchaseBlockStatusEnum::getPriority))
                .orElse(PurchaseBlockStatusEnum.ALLOW);
        return new PurchaseBlockDecision(blockStatus, statusPassengerMap.getOrDefault(blockStatus, Set.of()));
    }

    private String buildMessage(PurchaseBlockDecision decision) {
        String joinedPassengerIds = decision.passengerIds().stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return decision.blockStatus().getMessagePrefix() + "，passengerId=" + joinedPassengerIds;
    }

    private Map<PurchaseBlockStatusEnum, Set<Long>> queryBlockedPassengers(NewTicketPurchaseReqDTO request) {
        List<Long> passengerIds = request.getPassengers().stream()
                .map(NewTicketPurchaseReqDTO.PassengerDTO::getPassengerId)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(passengerIds)) {
            return Map.of();
        }
        List<TicketDO> existedTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<TicketDO>()
                        .eq(TicketDO::getTrainId, request.getTrainId())
                        .in(TicketDO::getPassengerId, passengerIds));
        if (CollectionUtils.isEmpty(existedTickets)) {
            return Map.of();
        }

        Map<PurchaseBlockStatusEnum, Set<Long>> result = new LinkedHashMap<>();
        for (TicketDO existedTicket : existedTickets) {
            PurchaseBlockStatusEnum blockStatus = mapTicketStatus(existedTicket.getTicketStatus());
            if (PurchaseBlockStatusEnum.ALLOW.equals(blockStatus)) {
                continue;
            }
            result.computeIfAbsent(blockStatus, unused -> new LinkedHashSet<>())
                    .add(existedTicket.getPassengerId());
        }
        return result;
    }

    private PurchaseBlockStatusEnum mapTicketStatus(Integer ticketStatus) {
        if (TicketStatusEnum.PAID.getCode().equals(ticketStatus)) {
            return PurchaseBlockStatusEnum.PAID;
        }
        if (TicketStatusEnum.WAIT_PAY.getCode().equals(ticketStatus)) {
            return PurchaseBlockStatusEnum.WAIT_PAY;
        }
        return PurchaseBlockStatusEnum.ALLOW;
    }

    private record PurchaseBlockDecision(PurchaseBlockStatusEnum blockStatus, Set<Long> passengerIds) {
    }
}
