package com.eighthours.tickgo.ticket.idempotent;

import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PurchaseIdempotentKeyResolverTest {

    private final PurchaseIdempotentKeyResolver resolver = new PurchaseIdempotentKeyResolver();

    @Test
    void buildKey_shouldIgnorePassengerOrderForSameBusinessRequest() {
        NewTicketPurchaseReqDTO first = buildRequest(List.of(
                buildPassenger(1001L, 1),
                buildPassenger(1002L, 2)));
        NewTicketPurchaseReqDTO second = buildRequest(List.of(
                buildPassenger(1002L, 2),
                buildPassenger(1001L, 1)));

        String firstKey = resolver.buildKey("ticket:purchase", first);
        String secondKey = resolver.buildKey("ticket:purchase", second);

        assertEquals(firstKey, secondKey);
    }

    @Test
    void buildKey_shouldChangeWhenBusinessRequestChanges() {
        NewTicketPurchaseReqDTO first = buildRequest(List.of(buildPassenger(1001L, 1)));
        NewTicketPurchaseReqDTO second = buildRequest(List.of(buildPassenger(1001L, 2)));

        String firstKey = resolver.buildKey("ticket:purchase", first);
        String secondKey = resolver.buildKey("ticket:purchase", second);

        assertNotEquals(firstKey, secondKey);
    }

    private NewTicketPurchaseReqDTO buildRequest(List<NewTicketPurchaseReqDTO.PassengerDTO> passengers) {
        NewTicketPurchaseReqDTO request = new NewTicketPurchaseReqDTO();
        request.setUserId(1L);
        request.setTrainId(10L);
        request.setDeparture("北京南");
        request.setArrival("杭州东");
        request.setPassengers(passengers);
        return request;
    }

    private NewTicketPurchaseReqDTO.PassengerDTO buildPassenger(Long passengerId, Integer seatType) {
        NewTicketPurchaseReqDTO.PassengerDTO passenger = new NewTicketPurchaseReqDTO.PassengerDTO();
        passenger.setPassengerId(passengerId);
        passenger.setSeatType(seatType);
        return passenger;
    }
}
