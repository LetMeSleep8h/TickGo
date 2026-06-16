package com.eighthours.tickgo.ticket.controller;

import com.eighthours.tickgo.ticket.common.Result;
import com.eighthours.tickgo.ticket.dto.NewTicketOrderReqDTO;
import com.eighthours.tickgo.ticket.dto.NewTicketPurchaseReqDTO;
import com.eighthours.tickgo.ticket.dto.TicketQueryRespDTO;
import com.eighthours.tickgo.ticket.service.NewTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewTicketControllerTest {

    @Mock
    private NewTicketService newTicketService;

    @Test
    void queryRemainTicket_shouldDelegateToNewService() {
        TicketQueryRespDTO expected = new TicketQueryRespDTO();
        when(newTicketService.queryRemainTicket(1L, "北京南", "杭州东")).thenReturn(expected);
        NewTicketController controller = new NewTicketController(newTicketService);

        Result<TicketQueryRespDTO> actual = controller.queryRemainTicket(1L, "北京南", "杭州东");

        assertEquals(200, actual.getCode());
        assertSame(expected, actual.getData());
    }

    @Test
    void confirmTickets_shouldUseRequestBody() {
        NewTicketController controller = new NewTicketController(newTicketService);
        NewTicketOrderReqDTO request = new NewTicketOrderReqDTO();
        request.setOrderSn("order-3");

        Result<Void> actual = controller.confirmTickets(request);

        assertEquals(200, actual.getCode());
        verify(newTicketService).confirmTickets(request);
    }

    @Test
    void purchaseTicketsV2_shouldDelegateToNewService() {
        NewTicketPurchaseReqDTO request = new NewTicketPurchaseReqDTO();
        NewTicketController controller = new NewTicketController(newTicketService);

        controller.purchaseTicketsV2(request);

        verify(newTicketService).purchaseTicketsV2(request);
    }
}
