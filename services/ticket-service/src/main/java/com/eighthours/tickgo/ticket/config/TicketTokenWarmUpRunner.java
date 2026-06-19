package com.eighthours.tickgo.ticket.config;

import com.eighthours.tickgo.ticket.entity.TrainDO;
import com.eighthours.tickgo.ticket.mapper.TrainMapper;
import com.eighthours.tickgo.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TicketTokenWarmUpRunner implements ApplicationRunner {

    private final TrainMapper trainMapper;
    private final TicketService ticketService;

    @Override
    public void run(ApplicationArguments args) {
        List<TrainDO> trains = trainMapper.selectList(null);
        if (CollectionUtils.isEmpty(trains)) {
            return;
        }
        for (TrainDO train : trains) {
            ticketService.initTicketToken(train.getId());
        }
    }
}
