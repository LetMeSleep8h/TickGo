package com.eighthours.tickgo.ticket.idempotent;

import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class PurchaseIdempotentKeyResolver {

    public String buildKey(String prefix, NewTicketPurchaseReqDTO request) {
        if (request == null || request.getUserId() == null) {
            throw new BizException("用户信息不能为空");
        }
        if (CollectionUtils.isEmpty(request.getPassengers())) {
            throw new BizException("乘车人不能为空");
        }
        String passengerFingerprint = request.getPassengers().stream()
                .sorted(Comparator.comparing(NewTicketPurchaseReqDTO.PassengerDTO::getPassengerId)
                        .thenComparing(NewTicketPurchaseReqDTO.PassengerDTO::getSeatType))
                .map(each -> each.getPassengerId() + "-" + each.getSeatType())
                .collect(Collectors.joining("_"));
        String raw = request.getUserId() + "|" + request.getTrainId() + "|" + request.getDeparture() + "|"
                + request.getArrival() + "|" + passengerFingerprint;
        return prefix + ":" + request.getUserId() + ":" + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
