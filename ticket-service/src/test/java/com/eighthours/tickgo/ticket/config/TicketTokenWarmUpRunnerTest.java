package com.eighthours.tickgo.ticket.config;

import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTokenWarmUpRunnerTest {

    @Mock
    private TrainMapper trainMapper;
    @Mock
    private TicketService ticketService;

    @Test
    void run_shouldInitializeTokensForEachTrain() throws Exception {
        TrainDO first = new TrainDO();
        first.setId(1L);
        TrainDO second = new TrainDO();
        second.setId(2L);
        when(trainMapper.selectList(null)).thenReturn(List.of(first, second));

        TicketTokenWarmUpRunner runner = new TicketTokenWarmUpRunner(trainMapper, ticketService);

        runner.run(null);

        verify(ticketService).initTicketToken(1L);
        verify(ticketService).initTicketToken(2L);
    }
}
