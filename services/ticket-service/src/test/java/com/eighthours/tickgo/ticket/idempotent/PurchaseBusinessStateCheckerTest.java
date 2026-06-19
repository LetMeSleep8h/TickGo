package com.eighthours.tickgo.ticket.idempotent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.entity.TicketDO;
import com.eighthours.tickgo.ticket.enums.TicketStatusEnum;
import com.eighthours.tickgo.ticket.exception.BizException;
import com.eighthours.tickgo.ticket.mapper.TicketMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseBusinessStateCheckerTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TicketDO.class);
    }

    @Mock
    private TicketMapper ticketMapper;

    @Test
    void check_shouldAllowRetryWhenOnlyCanceledTicketsExist() {
        when(ticketMapper.selectList(any())).thenReturn(List.of(buildTicket(TicketStatusEnum.CANCELED.getCode(), 1001L)));
        PurchaseBusinessStateChecker checker = new PurchaseBusinessStateChecker(ticketMapper);

        assertDoesNotThrow(() -> checker.check(buildRequest()));
    }

    @Test
    void check_shouldRejectWhenWaitingPayTicketExists() {
        when(ticketMapper.selectList(any())).thenReturn(List.of(buildTicket(TicketStatusEnum.WAIT_PAY.getCode(), 1001L)));
        PurchaseBusinessStateChecker checker = new PurchaseBusinessStateChecker(ticketMapper);

        BizException exception = assertThrows(BizException.class, () -> checker.check(buildRequest()));

        assertEquals("乘车人存在待支付订单，passengerId=1001", exception.getMessage());
    }

    @Test
    void check_shouldRejectWhenPaidTicketExists() {
        when(ticketMapper.selectList(any())).thenReturn(List.of(buildTicket(TicketStatusEnum.PAID.getCode(), 1001L)));
        PurchaseBusinessStateChecker checker = new PurchaseBusinessStateChecker(ticketMapper);

        BizException exception = assertThrows(BizException.class, () -> checker.check(buildRequest()));

        assertEquals("乘车人已购票成功，passengerId=1001", exception.getMessage());
    }

    private NewTicketPurchaseReqDTO buildRequest() {
        NewTicketPurchaseReqDTO request = new NewTicketPurchaseReqDTO();
        request.setUserId(1L);
        request.setTrainId(1L);
        request.setDeparture("北京南");
        request.setArrival("杭州东");
        NewTicketPurchaseReqDTO.PassengerDTO passenger = new NewTicketPurchaseReqDTO.PassengerDTO();
        passenger.setPassengerId(1001L);
        passenger.setSeatType(1);
        request.setPassengers(List.of(passenger));
        return request;
    }

    private TicketDO buildTicket(Integer status, Long passengerId) {
        TicketDO ticket = new TicketDO();
        ticket.setTrainId(1L);
        ticket.setPassengerId(passengerId);
        ticket.setTicketStatus(status);
        ticket.setDeparture("北京南");
        ticket.setArrival("杭州东");
        return ticket;
    }
}
